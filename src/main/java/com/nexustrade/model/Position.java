package com.nexustrade.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Position entity representing holdings in a specific instrument.
 */
@Entity
@Table(name = "POSITIONS", indexes = {
    @Index(name = "idx_positions_account", columnList = "account_id"),
    @Index(name = "idx_positions_instrument", columnList = "instrument_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_position_account_instrument",
                      columnNames = {"account_id", "instrument_id"})
})
public final class Position {

    @Id
    @Column(name = "position_id", length = 36)
    private final String positionId;

    @Column(name = "account_id", nullable = false, length = 36)
    private final String accountId;

    @Column(name = "instrument_id", nullable = false, length = 36)
    private final String instrumentId;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 8)
    private final BigDecimal quantity;

    @Column(name = "average_cost", nullable = false, precision = 18, scale = 8)
    private final BigDecimal averageCost;

    @Column(name = "market_value", precision = 18, scale = 8)
    private final BigDecimal marketValue;

    @Column(name = "unrealized_pnl", precision = 18, scale = 8)
    private final BigDecimal unrealizedPnl;

    @Column(name = "realized_pnl", precision = 18, scale = 8)
    private final BigDecimal realizedPnl;

    @Column(name = "updated_at", nullable = false)
    private final Instant updatedAt;

    @Version
    @Column(name = "version")
    private final Long version;

    // JPA requires no-arg constructor
    protected Position() {
        this.positionId = null;
        this.accountId = null;
        this.instrumentId = null;
        this.quantity = null;
        this.averageCost = null;
        this.marketValue = null;
        this.unrealizedPnl = null;
        this.realizedPnl = null;
        this.updatedAt = null;
        this.version = null;
    }

    private Position(Builder builder) {
        this.positionId = builder.positionId;
        this.accountId = builder.accountId;
        this.instrumentId = builder.instrumentId;
        this.quantity = builder.quantity;
        this.averageCost = builder.averageCost;
        this.marketValue = builder.marketValue;
        this.unrealizedPnl = builder.unrealizedPnl;
        this.realizedPnl = builder.realizedPnl;
        this.updatedAt = builder.updatedAt;
        this.version = builder.version;
    }

    // Getters only
    public String getPositionId() { return positionId; }
    public String getAccountId() { return accountId; }
    public String getInstrumentId() { return instrumentId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getAverageCost() { return averageCost; }
    public BigDecimal getMarketValue() { return marketValue; }
    public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    /**
     * Calculate total cost basis.
     */
    public BigDecimal getCostBasis() {
        return quantity.multiply(averageCost);
    }

    /**
     * Check if position is long.
     */
    public boolean isLong() {
        return quantity.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if position is short.
     */
    public boolean isShort() {
        return quantity.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Check if position is flat (no holdings).
     */
    public boolean isFlat() {
        return quantity.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Create a new position with added quantity (for buy).
     */
    public Position withBuy(BigDecimal addedQuantity, BigDecimal price) {
        BigDecimal newQuantity = this.quantity.add(addedQuantity);
        BigDecimal totalCost = this.quantity.multiply(this.averageCost)
                .add(addedQuantity.multiply(price));
        BigDecimal newAverageCost = totalCost.divide(newQuantity, 8, java.math.RoundingMode.HALF_UP);

        return new Builder(this)
                .quantity(newQuantity)
                .averageCost(newAverageCost)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Create a new position with reduced quantity (for sell).
     */
    public Position withSell(BigDecimal soldQuantity, BigDecimal price) {
        BigDecimal newQuantity = this.quantity.subtract(soldQuantity);
        BigDecimal pnl = soldQuantity.multiply(price.subtract(this.averageCost));
        BigDecimal newRealizedPnl = (this.realizedPnl != null ? this.realizedPnl : BigDecimal.ZERO).add(pnl);

        return new Builder(this)
                .quantity(newQuantity)
                .realizedPnl(newRealizedPnl)
                .updatedAt(Instant.now())
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Position position)) return false;
        return Objects.equals(positionId, position.positionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(positionId);
    }

    @Override
    public String toString() {
        return String.format("Position[account=%s, instrument=%s, qty=%s, avgCost=%s]",
                accountId, instrumentId, quantity, averageCost);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String positionId;
        private String accountId;
        private String instrumentId;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal averageCost = BigDecimal.ZERO;
        private BigDecimal marketValue;
        private BigDecimal unrealizedPnl;
        private BigDecimal realizedPnl = BigDecimal.ZERO;
        private Instant updatedAt;
        private Long version;

        public Builder() {
            this.positionId = UUID.randomUUID().toString();
            this.updatedAt = Instant.now();
        }

        public Builder(Position position) {
            this.positionId = position.positionId;
            this.accountId = position.accountId;
            this.instrumentId = position.instrumentId;
            this.quantity = position.quantity;
            this.averageCost = position.averageCost;
            this.marketValue = position.marketValue;
            this.unrealizedPnl = position.unrealizedPnl;
            this.realizedPnl = position.realizedPnl;
            this.updatedAt = position.updatedAt;
            this.version = position.version;
        }

        public Builder positionId(String positionId) { this.positionId = positionId; return this; }
        public Builder accountId(String accountId) { this.accountId = accountId; return this; }
        public Builder instrumentId(String instrumentId) { this.instrumentId = instrumentId; return this; }
        public Builder quantity(BigDecimal quantity) { this.quantity = quantity; return this; }
        public Builder averageCost(BigDecimal averageCost) { this.averageCost = averageCost; return this; }
        public Builder marketValue(BigDecimal marketValue) { this.marketValue = marketValue; return this; }
        public Builder unrealizedPnl(BigDecimal unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; return this; }
        public Builder realizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder version(Long version) { this.version = version; return this; }

        public Position build() {
            Objects.requireNonNull(accountId, "accountId is required");
            Objects.requireNonNull(instrumentId, "instrumentId is required");
            return new Position(this);
        }
    }
}
