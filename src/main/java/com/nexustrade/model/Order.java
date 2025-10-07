package com.nexustrade.model;

import com.nexustrade.model.enums.OrderSide;
import com.nexustrade.model.enums.OrderStatus;
import com.nexustrade.model.enums.OrderType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable Order entity representing a trading order in the system.
 * Thread-safe by design - all fields are final and set via builder.
 * In high-frequency trading, immutability prevents race conditions.
 */
@Entity
@Table(name = "ORDERS", indexes = {
    @Index(name = "idx_orders_account", columnList = "account_id"),
    @Index(name = "idx_orders_instrument", columnList = "instrument_id"),
    @Index(name = "idx_orders_status", columnList = "status"),
    @Index(name = "idx_orders_timestamp", columnList = "created_at")
})
public final class Order {

    @Id
    @Column(name = "order_id", length = 36)
    private final String orderId;

    @Column(name = "account_id", nullable = false, length = 36)
    private final String accountId;

    @Column(name = "instrument_id", nullable = false, length = 36)
    private final String instrumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 4)
    private final OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 10)
    private final OrderType type;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 8)
    private final BigDecimal quantity;

    @Column(name = "filled_quantity", precision = 18, scale = 8)
    private final BigDecimal filledQuantity;

    @Column(name = "price", precision = 18, scale = 8)
    private final BigDecimal price;

    @Column(name = "stop_price", precision = 18, scale = 8)
    private final BigDecimal stopPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private final OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private final Instant createdAt;

    @Column(name = "updated_at")
    private final Instant updatedAt;

    @Column(name = "client_order_id", length = 64)
    private final String clientOrderId;

    @Version
    @Column(name = "version")
    private final Long version;

    // JPA requires a no-arg constructor
    protected Order() {
        this.orderId = null;
        this.accountId = null;
        this.instrumentId = null;
        this.side = null;
        this.type = null;
        this.quantity = null;
        this.filledQuantity = null;
        this.price = null;
        this.stopPrice = null;
        this.status = null;
        this.createdAt = null;
        this.updatedAt = null;
        this.clientOrderId = null;
        this.version = null;
    }

    private Order(Builder builder) {
        this.orderId = builder.orderId;
        this.accountId = builder.accountId;
        this.instrumentId = builder.instrumentId;
        this.side = builder.side;
        this.type = builder.type;
        this.quantity = builder.quantity;
        this.filledQuantity = builder.filledQuantity;
        this.price = builder.price;
        this.stopPrice = builder.stopPrice;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.clientOrderId = builder.clientOrderId;
        this.version = builder.version;
    }

    // Getters only - no setters for immutability
    public String getOrderId() { return orderId; }
    public String getAccountId() { return accountId; }
    public String getInstrumentId() { return instrumentId; }
    public OrderSide getSide() { return side; }
    public OrderType getType() { return type; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getFilledQuantity() { return filledQuantity; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getStopPrice() { return stopPrice; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getClientOrderId() { return clientOrderId; }
    public Long getVersion() { return version; }

    /**
     * Calculate remaining quantity to be filled.
     */
    public BigDecimal getRemainingQuantity() {
        if (filledQuantity == null) {
            return quantity;
        }
        return quantity.subtract(filledQuantity);
    }

    /**
     * Check if order can be matched.
     */
    public boolean isMatchable() {
        return status.isActive() && getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Create a new order with updated status (returns new immutable instance).
     */
    public Order withStatus(OrderStatus newStatus) {
        return new Builder(this).status(newStatus).updatedAt(Instant.now()).build();
    }

    /**
     * Create a new order with updated filled quantity (returns new immutable instance).
     */
    public Order withFilledQuantity(BigDecimal newFilledQuantity) {
        OrderStatus newStatus = status;
        if (newFilledQuantity.compareTo(quantity) >= 0) {
            newStatus = OrderStatus.FILLED;
        } else if (newFilledQuantity.compareTo(BigDecimal.ZERO) > 0) {
            newStatus = OrderStatus.PARTIALLY_FILLED;
        }
        return new Builder(this)
                .filledQuantity(newFilledQuantity)
                .status(newStatus)
                .updatedAt(Instant.now())
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order order)) return false;
        return Objects.equals(orderId, order.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return String.format("Order[id=%s, %s %s %s @ %s, status=%s, filled=%s/%s]",
                orderId, side, quantity, instrumentId, price, status, filledQuantity, quantity);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String orderId;
        private String accountId;
        private String instrumentId;
        private OrderSide side;
        private OrderType type;
        private BigDecimal quantity;
        private BigDecimal filledQuantity = BigDecimal.ZERO;
        private BigDecimal price;
        private BigDecimal stopPrice;
        private OrderStatus status = OrderStatus.PENDING;
        private Instant createdAt;
        private Instant updatedAt;
        private String clientOrderId;
        private Long version;

        public Builder() {
            this.orderId = UUID.randomUUID().toString();
            this.createdAt = Instant.now();
        }

        public Builder(Order order) {
            this.orderId = order.orderId;
            this.accountId = order.accountId;
            this.instrumentId = order.instrumentId;
            this.side = order.side;
            this.type = order.type;
            this.quantity = order.quantity;
            this.filledQuantity = order.filledQuantity;
            this.price = order.price;
            this.stopPrice = order.stopPrice;
            this.status = order.status;
            this.createdAt = order.createdAt;
            this.updatedAt = order.updatedAt;
            this.clientOrderId = order.clientOrderId;
            this.version = order.version;
        }

        public Builder orderId(String orderId) { this.orderId = orderId; return this; }
        public Builder accountId(String accountId) { this.accountId = accountId; return this; }
        public Builder instrumentId(String instrumentId) { this.instrumentId = instrumentId; return this; }
        public Builder side(OrderSide side) { this.side = side; return this; }
        public Builder type(OrderType type) { this.type = type; return this; }
        public Builder quantity(BigDecimal quantity) { this.quantity = quantity; return this; }
        public Builder filledQuantity(BigDecimal filledQuantity) { this.filledQuantity = filledQuantity; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder stopPrice(BigDecimal stopPrice) { this.stopPrice = stopPrice; return this; }
        public Builder status(OrderStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder clientOrderId(String clientOrderId) { this.clientOrderId = clientOrderId; return this; }
        public Builder version(Long version) { this.version = version; return this; }

        public Order build() {
            Objects.requireNonNull(accountId, "accountId is required");
            Objects.requireNonNull(instrumentId, "instrumentId is required");
            Objects.requireNonNull(side, "side is required");
            Objects.requireNonNull(type, "type is required");
            Objects.requireNonNull(quantity, "quantity is required");

            if (type == OrderType.LIMIT || type == OrderType.STOP_LIMIT) {
                Objects.requireNonNull(price, "price is required for LIMIT orders");
            }
            if (type == OrderType.STOP || type == OrderType.STOP_LIMIT) {
                Objects.requireNonNull(stopPrice, "stopPrice is required for STOP orders");
            }

            return new Order(this);
        }
    }
}
