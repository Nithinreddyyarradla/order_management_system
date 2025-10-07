package com.nexustrade.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Account entity representing a trading account.
 * Contains balance information and account metadata.
 */
@Entity
@Table(name = "ACCOUNTS", indexes = {
    @Index(name = "idx_accounts_status", columnList = "status")
})
public final class Account {

    public enum AccountStatus {
        ACTIVE, SUSPENDED, CLOSED
    }

    public enum AccountType {
        CASH, MARGIN, INSTITUTIONAL
    }

    @Id
    @Column(name = "account_id", length = 36)
    private final String accountId;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private final String accountNumber;

    @Column(name = "account_name", nullable = false, length = 100)
    private final String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private final AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private final AccountStatus status;

    @Column(name = "currency", nullable = false, length = 3)
    private final String currency;

    @Column(name = "cash_balance", nullable = false, precision = 18, scale = 2)
    private final BigDecimal cashBalance;

    @Column(name = "available_balance", nullable = false, precision = 18, scale = 2)
    private final BigDecimal availableBalance;

    @Column(name = "margin_balance", precision = 18, scale = 2)
    private final BigDecimal marginBalance;

    @Column(name = "buying_power", precision = 18, scale = 2)
    private final BigDecimal buyingPower;

    @Column(name = "created_at", nullable = false)
    private final Instant createdAt;

    @Column(name = "updated_at")
    private final Instant updatedAt;

    @Version
    @Column(name = "version")
    private final Long version;

    // JPA requires no-arg constructor
    protected Account() {
        this.accountId = null;
        this.accountNumber = null;
        this.accountName = null;
        this.accountType = null;
        this.status = null;
        this.currency = null;
        this.cashBalance = null;
        this.availableBalance = null;
        this.marginBalance = null;
        this.buyingPower = null;
        this.createdAt = null;
        this.updatedAt = null;
        this.version = null;
    }

    private Account(Builder builder) {
        this.accountId = builder.accountId;
        this.accountNumber = builder.accountNumber;
        this.accountName = builder.accountName;
        this.accountType = builder.accountType;
        this.status = builder.status;
        this.currency = builder.currency;
        this.cashBalance = builder.cashBalance;
        this.availableBalance = builder.availableBalance;
        this.marginBalance = builder.marginBalance;
        this.buyingPower = builder.buyingPower;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.version = builder.version;
    }

    // Getters only
    public String getAccountId() { return accountId; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountName() { return accountName; }
    public AccountType getAccountType() { return accountType; }
    public AccountStatus getStatus() { return status; }
    public String getCurrency() { return currency; }
    public BigDecimal getCashBalance() { return cashBalance; }
    public BigDecimal getAvailableBalance() { return availableBalance; }
    public BigDecimal getMarginBalance() { return marginBalance; }
    public BigDecimal getBuyingPower() { return buyingPower; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    /**
     * Check if account can execute a buy order of given value.
     */
    public boolean canBuy(BigDecimal orderValue) {
        if (status != AccountStatus.ACTIVE) return false;
        BigDecimal effectiveBuyingPower = buyingPower != null ? buyingPower : availableBalance;
        return effectiveBuyingPower.compareTo(orderValue) >= 0;
    }

    /**
     * Create new account with updated balance after a debit.
     */
    public Account withDebit(BigDecimal amount) {
        return new Builder(this)
                .cashBalance(this.cashBalance.subtract(amount))
                .availableBalance(this.availableBalance.subtract(amount))
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Create new account with updated balance after a credit.
     */
    public Account withCredit(BigDecimal amount) {
        return new Builder(this)
                .cashBalance(this.cashBalance.add(amount))
                .availableBalance(this.availableBalance.add(amount))
                .updatedAt(Instant.now())
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account account)) return false;
        return Objects.equals(accountId, account.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId);
    }

    @Override
    public String toString() {
        return String.format("Account[%s - %s, balance=%s %s, status=%s]",
                accountNumber, accountName, cashBalance, currency, status);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String accountId;
        private String accountNumber;
        private String accountName;
        private AccountType accountType = AccountType.CASH;
        private AccountStatus status = AccountStatus.ACTIVE;
        private String currency = "USD";
        private BigDecimal cashBalance = BigDecimal.ZERO;
        private BigDecimal availableBalance = BigDecimal.ZERO;
        private BigDecimal marginBalance;
        private BigDecimal buyingPower;
        private Instant createdAt;
        private Instant updatedAt;
        private Long version;

        public Builder() {
            this.accountId = UUID.randomUUID().toString();
            this.createdAt = Instant.now();
        }

        public Builder(Account account) {
            this.accountId = account.accountId;
            this.accountNumber = account.accountNumber;
            this.accountName = account.accountName;
            this.accountType = account.accountType;
            this.status = account.status;
            this.currency = account.currency;
            this.cashBalance = account.cashBalance;
            this.availableBalance = account.availableBalance;
            this.marginBalance = account.marginBalance;
            this.buyingPower = account.buyingPower;
            this.createdAt = account.createdAt;
            this.updatedAt = account.updatedAt;
            this.version = account.version;
        }

        public Builder accountId(String accountId) { this.accountId = accountId; return this; }
        public Builder accountNumber(String accountNumber) { this.accountNumber = accountNumber; return this; }
        public Builder accountName(String accountName) { this.accountName = accountName; return this; }
        public Builder accountType(AccountType accountType) { this.accountType = accountType; return this; }
        public Builder status(AccountStatus status) { this.status = status; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder cashBalance(BigDecimal cashBalance) { this.cashBalance = cashBalance; return this; }
        public Builder availableBalance(BigDecimal availableBalance) { this.availableBalance = availableBalance; return this; }
        public Builder marginBalance(BigDecimal marginBalance) { this.marginBalance = marginBalance; return this; }
        public Builder buyingPower(BigDecimal buyingPower) { this.buyingPower = buyingPower; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder version(Long version) { this.version = version; return this; }

        public Account build() {
            Objects.requireNonNull(accountNumber, "accountNumber is required");
            Objects.requireNonNull(accountName, "accountName is required");
            return new Account(this);
        }
    }
}
