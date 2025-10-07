-- ============================================
-- PR_SETTLE_TRADE
-- Atomic Trade Settlement Procedure
-- ============================================
-- Purpose: Settles a trade with ACID compliance.
-- Ensures money never disappears by executing all
-- balance updates in a single atomic transaction.
-- ============================================

CREATE OR REPLACE PROCEDURE PR_SETTLE_TRADE (
    p_trade_id IN VARCHAR2,
    p_success OUT NUMBER,
    p_error_message OUT VARCHAR2
) AS
    v_trade_value NUMBER(18,8);
    v_quantity NUMBER(18,8);
    v_price NUMBER(18,8);
    v_instrument_id VARCHAR2(36);
    v_buyer_id VARCHAR2(36);
    v_seller_id VARCHAR2(36);
    v_buyer_balance NUMBER(18,2);
    v_seller_balance NUMBER(18,2);
    v_is_settled NUMBER(1);

    -- Exception declarations
    e_trade_not_found EXCEPTION;
    e_already_settled EXCEPTION;
    e_insufficient_funds EXCEPTION;
    e_position_not_found EXCEPTION;

BEGIN
    p_success := 0;
    p_error_message := NULL;

    -- Set savepoint for rollback
    SAVEPOINT before_settlement;

    -- Lock and retrieve trade details
    SELECT total_value, quantity, price, instrument_id,
           buyer_account_id, seller_account_id, is_settled
    INTO v_trade_value, v_quantity, v_price, v_instrument_id,
         v_buyer_id, v_seller_id, v_is_settled
    FROM TRADES
    WHERE trade_id = p_trade_id
    FOR UPDATE NOWAIT;

    -- Check if already settled
    IF v_is_settled = 1 THEN
        RAISE e_already_settled;
    END IF;

    -- Get buyer's current balance
    SELECT available_balance
    INTO v_buyer_balance
    FROM ACCOUNTS
    WHERE account_id = v_buyer_id
    FOR UPDATE;

    -- Verify buyer has sufficient funds
    IF v_buyer_balance < v_trade_value THEN
        RAISE e_insufficient_funds;
    END IF;

    -- Get seller's current balance (for position check)
    SELECT available_balance
    INTO v_seller_balance
    FROM ACCOUNTS
    WHERE account_id = v_seller_id
    FOR UPDATE;

    -- ========================================
    -- STEP 1: Debit buyer's account
    -- ========================================
    UPDATE ACCOUNTS
    SET cash_balance = cash_balance - v_trade_value,
        available_balance = available_balance - v_trade_value,
        updated_at = SYSTIMESTAMP
    WHERE account_id = v_buyer_id;

    IF SQL%ROWCOUNT = 0 THEN
        RAISE e_trade_not_found;
    END IF;

    -- ========================================
    -- STEP 2: Credit seller's account
    -- ========================================
    UPDATE ACCOUNTS
    SET cash_balance = cash_balance + v_trade_value,
        available_balance = available_balance + v_trade_value,
        updated_at = SYSTIMESTAMP
    WHERE account_id = v_seller_id;

    IF SQL%ROWCOUNT = 0 THEN
        RAISE e_trade_not_found;
    END IF;

    -- ========================================
    -- STEP 3: Update buyer's position (add shares)
    -- ========================================
    MERGE INTO POSITIONS p
    USING (SELECT v_buyer_id AS account_id,
                  v_instrument_id AS instrument_id,
                  v_quantity AS quantity,
                  v_price AS price
           FROM DUAL) src
    ON (p.account_id = src.account_id AND p.instrument_id = src.instrument_id)
    WHEN MATCHED THEN
        UPDATE SET
            quantity = p.quantity + src.quantity,
            average_cost = (p.quantity * p.average_cost + src.quantity * src.price) / (p.quantity + src.quantity),
            updated_at = SYSTIMESTAMP,
            version = version + 1
    WHEN NOT MATCHED THEN
        INSERT (position_id, account_id, instrument_id, quantity, average_cost, updated_at)
        VALUES (SYS_GUID(), src.account_id, src.instrument_id, src.quantity, src.price, SYSTIMESTAMP);

    -- ========================================
    -- STEP 4: Update seller's position (remove shares)
    -- ========================================
    UPDATE POSITIONS
    SET quantity = quantity - v_quantity,
        realized_pnl = NVL(realized_pnl, 0) + (v_quantity * (v_price - average_cost)),
        updated_at = SYSTIMESTAMP,
        version = version + 1
    WHERE account_id = v_seller_id
    AND instrument_id = v_instrument_id;

    IF SQL%ROWCOUNT = 0 THEN
        RAISE e_position_not_found;
    END IF;

    -- ========================================
    -- STEP 5: Mark trade as settled
    -- ========================================
    UPDATE TRADES
    SET is_settled = 1,
        settlement_date = SYSTIMESTAMP,
        version = version + 1
    WHERE trade_id = p_trade_id;

    -- Success
    p_success := 1;
    COMMIT;

    DBMS_OUTPUT.PUT_LINE('Trade settled successfully: ' || p_trade_id);
    DBMS_OUTPUT.PUT_LINE('Value: ' || TO_CHAR(v_trade_value, '999,999,999.99'));
    DBMS_OUTPUT.PUT_LINE('Buyer: ' || v_buyer_id || ' debited');
    DBMS_OUTPUT.PUT_LINE('Seller: ' || v_seller_id || ' credited');

EXCEPTION
    WHEN NO_DATA_FOUND THEN
        ROLLBACK TO before_settlement;
        p_success := 0;
        p_error_message := 'Trade not found: ' || p_trade_id;
        DBMS_OUTPUT.PUT_LINE(p_error_message);

    WHEN e_already_settled THEN
        ROLLBACK TO before_settlement;
        p_success := 0;
        p_error_message := 'Trade already settled: ' || p_trade_id;
        DBMS_OUTPUT.PUT_LINE(p_error_message);

    WHEN e_insufficient_funds THEN
        ROLLBACK TO before_settlement;
        p_success := 0;
        p_error_message := 'Insufficient funds for buyer. Required: ' ||
                          TO_CHAR(v_trade_value, '999,999,999.99') ||
                          ', Available: ' || TO_CHAR(v_buyer_balance, '999,999,999.99');
        DBMS_OUTPUT.PUT_LINE(p_error_message);

    WHEN e_position_not_found THEN
        ROLLBACK TO before_settlement;
        p_success := 0;
        p_error_message := 'Seller position not found for instrument: ' || v_instrument_id;
        DBMS_OUTPUT.PUT_LINE(p_error_message);

    WHEN OTHERS THEN
        ROLLBACK TO before_settlement;
        p_success := 0;
        p_error_message := 'Settlement failed: ' || SQLERRM;
        DBMS_OUTPUT.PUT_LINE(p_error_message);
        RAISE;

END PR_SETTLE_TRADE;
/

-- ============================================
-- Grant execution privileges
-- ============================================
-- GRANT EXECUTE ON PR_SETTLE_TRADE TO nexustrade_app;

-- ============================================
-- Example usage:
-- ============================================
-- DECLARE
--     v_success NUMBER;
--     v_error VARCHAR2(500);
-- BEGIN
--     PR_SETTLE_TRADE(
--         p_trade_id => 'trade-12345',
--         p_success => v_success,
--         p_error_message => v_error
--     );
--
--     IF v_success = 1 THEN
--         DBMS_OUTPUT.PUT_LINE('Settlement successful');
--     ELSE
--         DBMS_OUTPUT.PUT_LINE('Settlement failed: ' || v_error);
--     END IF;
-- END;
-- /
