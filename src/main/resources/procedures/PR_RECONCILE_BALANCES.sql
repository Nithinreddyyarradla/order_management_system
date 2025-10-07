-- ============================================
-- PR_RECONCILE_BALANCES
-- End-of-Day Balance Reconciliation Procedure
-- ============================================
-- Purpose: Reconciles account balances by summing all trades
-- and updating account balances to ensure data integrity.
-- Should be run at end of trading day.
-- ============================================

CREATE OR REPLACE PROCEDURE PR_RECONCILE_BALANCES (
    p_reconciliation_date IN DATE DEFAULT TRUNC(SYSDATE),
    p_accounts_processed OUT NUMBER,
    p_discrepancies_found OUT NUMBER
) AS
    v_start_time TIMESTAMP := SYSTIMESTAMP;
    v_calculated_balance NUMBER(18,2);
    v_current_balance NUMBER(18,2);
    v_discrepancy NUMBER(18,2);

    -- Cursor for all active accounts
    CURSOR c_accounts IS
        SELECT account_id, account_number, cash_balance
        FROM ACCOUNTS
        WHERE status = 'ACTIVE'
        FOR UPDATE;

    -- Record type for logging
    TYPE t_reconciliation_log IS RECORD (
        account_id VARCHAR2(36),
        expected_balance NUMBER(18,2),
        actual_balance NUMBER(18,2),
        discrepancy NUMBER(18,2)
    );

BEGIN
    p_accounts_processed := 0;
    p_discrepancies_found := 0;

    DBMS_OUTPUT.PUT_LINE('Starting balance reconciliation for date: ' || TO_CHAR(p_reconciliation_date, 'YYYY-MM-DD'));
    DBMS_OUTPUT.PUT_LINE('Start time: ' || TO_CHAR(v_start_time, 'YYYY-MM-DD HH24:MI:SS.FF3'));

    FOR r_account IN c_accounts LOOP
        BEGIN
            -- Calculate expected balance based on settled trades
            -- Credits (from sales) minus Debits (from purchases)
            SELECT NVL(
                (SELECT SUM(total_value)
                 FROM TRADES
                 WHERE seller_account_id = r_account.account_id
                 AND is_settled = 1
                 AND TRUNC(settlement_date) <= p_reconciliation_date)
                -
                (SELECT SUM(total_value)
                 FROM TRADES
                 WHERE buyer_account_id = r_account.account_id
                 AND is_settled = 1
                 AND TRUNC(settlement_date) <= p_reconciliation_date),
                0
            ) + r_account.cash_balance -- Add to original balance
            INTO v_calculated_balance
            FROM DUAL;

            -- Compare with current balance
            v_current_balance := r_account.cash_balance;
            v_discrepancy := ABS(v_calculated_balance - v_current_balance);

            -- If discrepancy found, log it
            IF v_discrepancy > 0.01 THEN -- Allow for rounding tolerance
                p_discrepancies_found := p_discrepancies_found + 1;

                DBMS_OUTPUT.PUT_LINE('DISCREPANCY FOUND - Account: ' || r_account.account_number);
                DBMS_OUTPUT.PUT_LINE('  Expected: ' || TO_CHAR(v_calculated_balance, '999,999,999,999.99'));
                DBMS_OUTPUT.PUT_LINE('  Actual:   ' || TO_CHAR(v_current_balance, '999,999,999,999.99'));
                DBMS_OUTPUT.PUT_LINE('  Diff:     ' || TO_CHAR(v_discrepancy, '999,999,999,999.99'));

                -- Insert into audit log (would create separate audit table in production)
                -- INSERT INTO RECONCILIATION_LOG (...) VALUES (...);
            END IF;

            -- Update available balance to match cash balance for reconciliation
            UPDATE ACCOUNTS
            SET available_balance = cash_balance,
                updated_at = SYSTIMESTAMP
            WHERE account_id = r_account.account_id;

            p_accounts_processed := p_accounts_processed + 1;

        EXCEPTION
            WHEN OTHERS THEN
                DBMS_OUTPUT.PUT_LINE('Error processing account ' || r_account.account_number || ': ' || SQLERRM);
                ROLLBACK;
                RAISE;
        END;
    END LOOP;

    -- Commit all changes
    COMMIT;

    DBMS_OUTPUT.PUT_LINE('Reconciliation completed.');
    DBMS_OUTPUT.PUT_LINE('Accounts processed: ' || p_accounts_processed);
    DBMS_OUTPUT.PUT_LINE('Discrepancies found: ' || p_discrepancies_found);
    DBMS_OUTPUT.PUT_LINE('Duration: ' ||
        EXTRACT(SECOND FROM (SYSTIMESTAMP - v_start_time)) || ' seconds');

EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('Fatal error in reconciliation: ' || SQLERRM);
        ROLLBACK;
        RAISE;
END PR_RECONCILE_BALANCES;
/

-- ============================================
-- Grant execution privileges
-- ============================================
-- GRANT EXECUTE ON PR_RECONCILE_BALANCES TO nexustrade_app;

-- ============================================
-- Example usage:
-- ============================================
-- DECLARE
--     v_processed NUMBER;
--     v_discrepancies NUMBER;
-- BEGIN
--     PR_RECONCILE_BALANCES(
--         p_reconciliation_date => TRUNC(SYSDATE),
--         p_accounts_processed => v_processed,
--         p_discrepancies_found => v_discrepancies
--     );
--     DBMS_OUTPUT.PUT_LINE('Processed: ' || v_processed || ', Discrepancies: ' || v_discrepancies);
-- END;
-- /
