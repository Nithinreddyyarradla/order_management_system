-- NexusTrade Oracle Database Schema
-- High-Throughput Order Management & Settlement System
-- Compatible with Oracle XE

-- ============================================
-- ACCOUNTS TABLE
-- ============================================
CREATE TABLE ACCOUNTS (
    account_id          VARCHAR2(36) PRIMARY KEY,
    account_number      VARCHAR2(20) NOT NULL UNIQUE,
    account_name        VARCHAR2(100) NOT NULL,
    account_type        VARCHAR2(20) NOT NULL CHECK (account_type IN ('CASH', 'MARGIN', 'INSTITUTIONAL')),
    status              VARCHAR2(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    currency            VARCHAR2(3) NOT NULL DEFAULT 'USD',
    cash_balance        NUMBER(18,2) NOT NULL DEFAULT 0,
    available_balance   NUMBER(18,2) NOT NULL DEFAULT 0,
    margin_balance      NUMBER(18,2),
    buying_power        NUMBER(18,2),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    version             NUMBER DEFAULT 0
);

CREATE INDEX idx_accounts_status ON ACCOUNTS(status);
CREATE INDEX idx_accounts_currency ON ACCOUNTS(currency);

-- ============================================
-- INSTRUMENTS TABLE
-- ============================================
CREATE TABLE INSTRUMENTS (
    instrument_id           VARCHAR2(36) PRIMARY KEY,
    symbol                  VARCHAR2(20) NOT NULL UNIQUE,
    name                    VARCHAR2(100) NOT NULL,
    instrument_type         VARCHAR2(10) NOT NULL CHECK (instrument_type IN ('STOCK', 'OPTION', 'FUTURE', 'FOREX', 'BOND', 'ETF', 'INDEX')),
    exchange                VARCHAR2(20),
    currency                VARCHAR2(3) NOT NULL DEFAULT 'USD',
    tick_size               NUMBER(10,6) DEFAULT 0.01,
    lot_size                NUMBER(18,8) DEFAULT 1,
    expiry_date             DATE,
    strike_price            NUMBER(18,8),
    underlying_instrument_id VARCHAR2(36),
    is_active               NUMBER(1) NOT NULL DEFAULT 1,
    version                 NUMBER DEFAULT 0,
    CONSTRAINT fk_underlying FOREIGN KEY (underlying_instrument_id) REFERENCES INSTRUMENTS(instrument_id)
);

CREATE INDEX idx_instruments_symbol ON INSTRUMENTS(symbol);
CREATE INDEX idx_instruments_type ON INSTRUMENTS(instrument_type);
CREATE INDEX idx_instruments_active ON INSTRUMENTS(is_active);

-- ============================================
-- ORDERS TABLE
-- ============================================
CREATE TABLE ORDERS (
    order_id            VARCHAR2(36) PRIMARY KEY,
    account_id          VARCHAR2(36) NOT NULL,
    instrument_id       VARCHAR2(36) NOT NULL,
    side                VARCHAR2(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    order_type          VARCHAR2(10) NOT NULL CHECK (order_type IN ('MARKET', 'LIMIT', 'STOP', 'STOP_LIMIT')),
    quantity            NUMBER(18,8) NOT NULL CHECK (quantity > 0),
    filled_quantity     NUMBER(18,8) DEFAULT 0,
    price               NUMBER(18,8),
    stop_price          NUMBER(18,8),
    status              VARCHAR2(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'OPEN', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'REJECTED', 'EXPIRED')),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    client_order_id     VARCHAR2(64),
    version             NUMBER DEFAULT 0,
    CONSTRAINT fk_order_account FOREIGN KEY (account_id) REFERENCES ACCOUNTS(account_id),
    CONSTRAINT fk_order_instrument FOREIGN KEY (instrument_id) REFERENCES INSTRUMENTS(instrument_id)
);

CREATE INDEX idx_orders_account ON ORDERS(account_id);
CREATE INDEX idx_orders_instrument ON ORDERS(instrument_id);
CREATE INDEX idx_orders_status ON ORDERS(status);
CREATE INDEX idx_orders_timestamp ON ORDERS(created_at);
CREATE INDEX idx_orders_client_order ON ORDERS(client_order_id, account_id);

-- ============================================
-- TRADES TABLE
-- ============================================
CREATE TABLE TRADES (
    trade_id            VARCHAR2(36) PRIMARY KEY,
    buy_order_id        VARCHAR2(36) NOT NULL,
    sell_order_id       VARCHAR2(36) NOT NULL,
    buyer_account_id    VARCHAR2(36) NOT NULL,
    seller_account_id   VARCHAR2(36) NOT NULL,
    instrument_id       VARCHAR2(36) NOT NULL,
    quantity            NUMBER(18,8) NOT NULL CHECK (quantity > 0),
    price               NUMBER(18,8) NOT NULL CHECK (price > 0),
    total_value         NUMBER(18,8) NOT NULL,
    trade_date          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settlement_date     TIMESTAMP,
    is_settled          NUMBER(1) NOT NULL DEFAULT 0,
    version             NUMBER DEFAULT 0,
    CONSTRAINT fk_trade_buy_order FOREIGN KEY (buy_order_id) REFERENCES ORDERS(order_id),
    CONSTRAINT fk_trade_sell_order FOREIGN KEY (sell_order_id) REFERENCES ORDERS(order_id),
    CONSTRAINT fk_trade_buyer FOREIGN KEY (buyer_account_id) REFERENCES ACCOUNTS(account_id),
    CONSTRAINT fk_trade_seller FOREIGN KEY (seller_account_id) REFERENCES ACCOUNTS(account_id),
    CONSTRAINT fk_trade_instrument FOREIGN KEY (instrument_id) REFERENCES INSTRUMENTS(instrument_id)
);

CREATE INDEX idx_trades_buy_order ON TRADES(buy_order_id);
CREATE INDEX idx_trades_sell_order ON TRADES(sell_order_id);
CREATE INDEX idx_trades_instrument ON TRADES(instrument_id);
CREATE INDEX idx_trades_date ON TRADES(trade_date);
CREATE INDEX idx_trades_settled ON TRADES(is_settled);

-- ============================================
-- TRADE_HISTORY TABLE (Archive)
-- ============================================
CREATE TABLE TRADE_HISTORY (
    trade_id            VARCHAR2(36) PRIMARY KEY,
    buy_order_id        VARCHAR2(36) NOT NULL,
    sell_order_id       VARCHAR2(36) NOT NULL,
    buyer_account_id    VARCHAR2(36) NOT NULL,
    seller_account_id   VARCHAR2(36) NOT NULL,
    instrument_id       VARCHAR2(36) NOT NULL,
    quantity            NUMBER(18,8) NOT NULL,
    price               NUMBER(18,8) NOT NULL,
    total_value         NUMBER(18,8) NOT NULL,
    trade_date          TIMESTAMP NOT NULL,
    settlement_date     TIMESTAMP,
    archived_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trade_history_date ON TRADE_HISTORY(trade_date);
CREATE INDEX idx_trade_history_instrument ON TRADE_HISTORY(instrument_id);

-- ============================================
-- POSITIONS TABLE
-- ============================================
CREATE TABLE POSITIONS (
    position_id         VARCHAR2(36) PRIMARY KEY,
    account_id          VARCHAR2(36) NOT NULL,
    instrument_id       VARCHAR2(36) NOT NULL,
    quantity            NUMBER(18,8) NOT NULL DEFAULT 0,
    average_cost        NUMBER(18,8) NOT NULL DEFAULT 0,
    market_value        NUMBER(18,8),
    unrealized_pnl      NUMBER(18,8),
    realized_pnl        NUMBER(18,8) DEFAULT 0,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             NUMBER DEFAULT 0,
    CONSTRAINT uk_position_account_instrument UNIQUE (account_id, instrument_id),
    CONSTRAINT fk_position_account FOREIGN KEY (account_id) REFERENCES ACCOUNTS(account_id),
    CONSTRAINT fk_position_instrument FOREIGN KEY (instrument_id) REFERENCES INSTRUMENTS(instrument_id)
);

CREATE INDEX idx_positions_account ON POSITIONS(account_id);
CREATE INDEX idx_positions_instrument ON POSITIONS(instrument_id);

-- ============================================
-- SPRING BATCH TABLES (Required for batch processing)
-- ============================================
-- Note: Spring Batch will create these tables automatically
-- if spring.batch.jdbc.initialize-schema=always

-- ============================================
-- SEQUENCES (for ID generation if needed)
-- ============================================
CREATE SEQUENCE seq_account_number START WITH 1000001 INCREMENT BY 1;

-- ============================================
-- SAMPLE DATA (for testing)
-- ============================================
-- Insert sample instruments
INSERT INTO INSTRUMENTS (instrument_id, symbol, name, instrument_type, exchange, currency, tick_size, lot_size, is_active)
VALUES ('inst-001', 'AAPL', 'Apple Inc.', 'STOCK', 'NASDAQ', 'USD', 0.01, 1, 1);

INSERT INTO INSTRUMENTS (instrument_id, symbol, name, instrument_type, exchange, currency, tick_size, lot_size, is_active)
VALUES ('inst-002', 'GOOGL', 'Alphabet Inc.', 'STOCK', 'NASDAQ', 'USD', 0.01, 1, 1);

INSERT INTO INSTRUMENTS (instrument_id, symbol, name, instrument_type, exchange, currency, tick_size, lot_size, is_active)
VALUES ('inst-003', 'MSFT', 'Microsoft Corporation', 'STOCK', 'NASDAQ', 'USD', 0.01, 1, 1);

INSERT INTO INSTRUMENTS (instrument_id, symbol, name, instrument_type, exchange, currency, tick_size, lot_size, is_active)
VALUES ('inst-004', 'AMZN', 'Amazon.com Inc.', 'STOCK', 'NASDAQ', 'USD', 0.01, 1, 1);

INSERT INTO INSTRUMENTS (instrument_id, symbol, name, instrument_type, exchange, currency, tick_size, lot_size, is_active)
VALUES ('inst-005', 'TSLA', 'Tesla Inc.', 'STOCK', 'NASDAQ', 'USD', 0.01, 1, 1);

-- Insert sample accounts
INSERT INTO ACCOUNTS (account_id, account_number, account_name, account_type, status, currency, cash_balance, available_balance)
VALUES ('acct-001', 'NX1000001', 'Test Trading Account 1', 'CASH', 'ACTIVE', 'USD', 100000.00, 100000.00);

INSERT INTO ACCOUNTS (account_id, account_number, account_name, account_type, status, currency, cash_balance, available_balance, margin_balance, buying_power)
VALUES ('acct-002', 'NX1000002', 'Test Margin Account', 'MARGIN', 'ACTIVE', 'USD', 50000.00, 50000.00, 50000.00, 100000.00);

INSERT INTO ACCOUNTS (account_id, account_number, account_name, account_type, status, currency, cash_balance, available_balance)
VALUES ('acct-003', 'NX1000003', 'Institutional Account', 'INSTITUTIONAL', 'ACTIVE', 'USD', 1000000.00, 1000000.00);

COMMIT;
