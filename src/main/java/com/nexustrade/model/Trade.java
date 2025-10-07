package com.nexustrade.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable Trade entity representing an executed trade.
 * Created when a buy order matches with a sell order.
 * Thread-safe by design.
 */
@Entity
@Table(name = "TRADES", indexes = {
    @Index(name = "idx_trades_buy_order", columnList = "buy_order_id"),
    @Index(name = "idx_trades_sell_order", columnList = "sell_order_id"),
    @Index(name = "idx_trades_instrument", columnList = "instrument_id"),
    @Index(name = "idx_trades_date", columnList = "trade_date")
})
public final class Trade {

    @Id
    @Column(name = "trade_id", length = 36)
    private final String tradeId;

    @Column(name = "buy_order_id", nullable = false, length = 36)
    private final String buyOrderId;

    @Column(name = "sell_order_id", nullable = false, length = 36)
    private final String sellOrderId;

    @Column(name = "buyer_account_id", nullable = false, length = 36)
    private final String buyerAccountId;

    @Column(name = "seller_account_id", nullable = false, length = 36)
    private final String sellerAccountId;

    @Column(name = "instrument_id", nullable = false, length = 36)
    private final String instrumentId;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 8)
    private final BigDecimal quantity;

    @Column(name = "price", nullable = false, precision = 18, scale = 8)
    private final BigDecimal price;

    @Column(name = "total_value", nullable = false, precision = 18, scale = 8)
    private final BigDecimal totalValue;

    @Column(name = "trade_date", nullable = false)
    private final Instant tradeDate;

    @Column(name = "settlement_date")
    private final Instant settlementDate;

    @Column(name = "is_settled", nullable = false)
    private final boolean settled;

    @Version
    @Column(name = "version")
    private final Long version;

    // JPA requires a no-arg constructor
    protected Trade() {
        this.tradeId = null;
        this.buyOrderId = null;
        this.sellOrderId = null;
        this.buyerAccountId = null;
        this.sellerAccountId = null;
        this.instrumentId = null;
        this.quantity = null;
        this.price = null;
        this.totalValue = null;
        this.tradeDate = null;
        this.settlementDate = null;
        this.settled = false;
        this.version = null;
    }

    private Trade(Builder builder) {
        this.tradeId = builder.tradeId;
        this.buyOrderId = builder.buyOrderId;
        this.sellOrderId = builder.sellOrderId;
        this.buyerAccountId = builder.buyerAccountId;
        this.sellerAccountId = builder.sellerAccountId;
        this.instrumentId = builder.instrumentId;
        this.quantity = builder.quantity;
        this.price = builder.price;
        this.totalValue = builder.quantity.multiply(builder.price);
        this.tradeDate = builder.tradeDate;
        this.settlementDate = builder.settlementDate;
        this.settled = builder.settled;
        this.version = builder.version;
    }

    // Getters only - no setters for immutability
    public String getTradeId() { return tradeId; }
    public String getBuyOrderId() { return buyOrderId; }
    public String getSellOrderId() { return sellOrderId; }
    public String getBuyerAccountId() { return buyerAccountId; }
    public String getSellerAccountId() { return sellerAccountId; }
    public String getInstrumentId() { return instrumentId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getTotalValue() { return totalValue; }
    public Instant getTradeDate() { return tradeDate; }
    public Instant getSettlementDate() { return settlementDate; }
    public boolean isSettled() { return settled; }
    public Long getVersion() { return version; }

    /**
     * Create a settled version of this trade.
     */
    public Trade withSettled(Instant settlementDate) {
        return new Builder(this)
                .settlementDate(settlementDate)
                .settled(true)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trade trade)) return false;
        return Objects.equals(tradeId, trade.tradeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradeId);
    }

    @Override
    public String toString() {
        return String.format("Trade[id=%s, instrument=%s, qty=%s @ %s, value=%s, settled=%s]",
                tradeId, instrumentId, quantity, price, totalValue, settled);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String tradeId;
        private String buyOrderId;
        private String sellOrderId;
        private String buyerAccountId;
        private String sellerAccountId;
        private String instrumentId;
        private BigDecimal quantity;
        private BigDecimal price;
        private Instant tradeDate;
        private Instant settlementDate;
        private boolean settled = false;
        private Long version;

        public Builder() {
            this.tradeId = UUID.randomUUID().toString();
            this.tradeDate = Instant.now();
        }

        public Builder(Trade trade) {
            this.tradeId = trade.tradeId;
            this.buyOrderId = trade.buyOrderId;
            this.sellOrderId = trade.sellOrderId;
            this.buyerAccountId = trade.buyerAccountId;
            this.sellerAccountId = trade.sellerAccountId;
            this.instrumentId = trade.instrumentId;
            this.quantity = trade.quantity;
            this.price = trade.price;
            this.tradeDate = trade.tradeDate;
            this.settlementDate = trade.settlementDate;
            this.settled = trade.settled;
            this.version = trade.version;
        }

        public Builder tradeId(String tradeId) { this.tradeId = tradeId; return this; }
        public Builder buyOrderId(String buyOrderId) { this.buyOrderId = buyOrderId; return this; }
        public Builder sellOrderId(String sellOrderId) { this.sellOrderId = sellOrderId; return this; }
        public Builder buyerAccountId(String buyerAccountId) { this.buyerAccountId = buyerAccountId; return this; }
        public Builder sellerAccountId(String sellerAccountId) { this.sellerAccountId = sellerAccountId; return this; }
        public Builder instrumentId(String instrumentId) { this.instrumentId = instrumentId; return this; }
        public Builder quantity(BigDecimal quantity) { this.quantity = quantity; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder tradeDate(Instant tradeDate) { this.tradeDate = tradeDate; return this; }
        public Builder settlementDate(Instant settlementDate) { this.settlementDate = settlementDate; return this; }
        public Builder settled(boolean settled) { this.settled = settled; return this; }
        public Builder version(Long version) { this.version = version; return this; }

        public Trade build() {
            Objects.requireNonNull(buyOrderId, "buyOrderId is required");
            Objects.requireNonNull(sellOrderId, "sellOrderId is required");
            Objects.requireNonNull(buyerAccountId, "buyerAccountId is required");
            Objects.requireNonNull(sellerAccountId, "sellerAccountId is required");
            Objects.requireNonNull(instrumentId, "instrumentId is required");
            Objects.requireNonNull(quantity, "quantity is required");
            Objects.requireNonNull(price, "price is required");

            return new Trade(this);
        }
    }
}
