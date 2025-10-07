package com.nexustrade.model;

import com.nexustrade.model.enums.InstrumentType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Instrument entity representing a tradeable financial instrument.
 * Can be Stock, Option, Future, etc.
 */
@Entity
@Table(name = "INSTRUMENTS", indexes = {
    @Index(name = "idx_instruments_symbol", columnList = "symbol"),
    @Index(name = "idx_instruments_type", columnList = "instrument_type")
})
public final class Instrument {

    @Id
    @Column(name = "instrument_id", length = 36)
    private final String instrumentId;

    @Column(name = "symbol", nullable = false, length = 20, unique = true)
    private final String symbol;

    @Column(name = "name", nullable = false, length = 100)
    private final String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false, length = 10)
    private final InstrumentType type;

    @Column(name = "exchange", length = 20)
    private final String exchange;

    @Column(name = "currency", nullable = false, length = 3)
    private final String currency;

    @Column(name = "tick_size", precision = 10, scale = 6)
    private final BigDecimal tickSize;

    @Column(name = "lot_size", precision = 18, scale = 8)
    private final BigDecimal lotSize;

    @Column(name = "expiry_date")
    private final LocalDate expiryDate;

    @Column(name = "strike_price", precision = 18, scale = 8)
    private final BigDecimal strikePrice;

    @Column(name = "underlying_instrument_id", length = 36)
    private final String underlyingInstrumentId;

    @Column(name = "is_active", nullable = false)
    private final boolean active;

    @Version
    @Column(name = "version")
    private final Long version;

    // JPA requires a no-arg constructor
    protected Instrument() {
        this.instrumentId = null;
        this.symbol = null;
        this.name = null;
        this.type = null;
        this.exchange = null;
        this.currency = null;
        this.tickSize = null;
        this.lotSize = null;
        this.expiryDate = null;
        this.strikePrice = null;
        this.underlyingInstrumentId = null;
        this.active = true;
        this.version = null;
    }

    private Instrument(Builder builder) {
        this.instrumentId = builder.instrumentId;
        this.symbol = builder.symbol;
        this.name = builder.name;
        this.type = builder.type;
        this.exchange = builder.exchange;
        this.currency = builder.currency;
        this.tickSize = builder.tickSize;
        this.lotSize = builder.lotSize;
        this.expiryDate = builder.expiryDate;
        this.strikePrice = builder.strikePrice;
        this.underlyingInstrumentId = builder.underlyingInstrumentId;
        this.active = builder.active;
        this.version = builder.version;
    }

    // Getters only
    public String getInstrumentId() { return instrumentId; }
    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public InstrumentType getType() { return type; }
    public String getExchange() { return exchange; }
    public String getCurrency() { return currency; }
    public BigDecimal getTickSize() { return tickSize; }
    public BigDecimal getLotSize() { return lotSize; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public BigDecimal getStrikePrice() { return strikePrice; }
    public String getUnderlyingInstrumentId() { return underlyingInstrumentId; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }

    /**
     * Check if this is a derivative instrument.
     */
    public boolean isDerivative() {
        return type == InstrumentType.OPTION || type == InstrumentType.FUTURE;
    }

    /**
     * Check if the instrument has expired.
     */
    public boolean isExpired() {
        if (expiryDate == null) return false;
        return LocalDate.now().isAfter(expiryDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Instrument that)) return false;
        return Objects.equals(instrumentId, that.instrumentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instrumentId);
    }

    @Override
    public String toString() {
        return String.format("Instrument[%s - %s (%s), %s]", symbol, name, type, currency);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String instrumentId;
        private String symbol;
        private String name;
        private InstrumentType type;
        private String exchange;
        private String currency = "USD";
        private BigDecimal tickSize = new BigDecimal("0.01");
        private BigDecimal lotSize = BigDecimal.ONE;
        private LocalDate expiryDate;
        private BigDecimal strikePrice;
        private String underlyingInstrumentId;
        private boolean active = true;
        private Long version;

        public Builder() {
            this.instrumentId = java.util.UUID.randomUUID().toString();
        }

        public Builder instrumentId(String instrumentId) { this.instrumentId = instrumentId; return this; }
        public Builder symbol(String symbol) { this.symbol = symbol; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder type(InstrumentType type) { this.type = type; return this; }
        public Builder exchange(String exchange) { this.exchange = exchange; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder tickSize(BigDecimal tickSize) { this.tickSize = tickSize; return this; }
        public Builder lotSize(BigDecimal lotSize) { this.lotSize = lotSize; return this; }
        public Builder expiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; return this; }
        public Builder strikePrice(BigDecimal strikePrice) { this.strikePrice = strikePrice; return this; }
        public Builder underlyingInstrumentId(String id) { this.underlyingInstrumentId = id; return this; }
        public Builder active(boolean active) { this.active = active; return this; }
        public Builder version(Long version) { this.version = version; return this; }

        public Instrument build() {
            Objects.requireNonNull(symbol, "symbol is required");
            Objects.requireNonNull(name, "name is required");
            Objects.requireNonNull(type, "type is required");
            return new Instrument(this);
        }
    }
}
