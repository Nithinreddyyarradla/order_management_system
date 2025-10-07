package com.nexustrade.service;

import com.nexustrade.model.Account;
import com.nexustrade.model.Instrument;
import com.nexustrade.model.Order;
import com.nexustrade.model.Position;
import com.nexustrade.model.enums.OrderSide;
import com.nexustrade.model.enums.OrderType;
import com.nexustrade.repository.AccountRepository;
import com.nexustrade.repository.InstrumentRepository;
import com.nexustrade.repository.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for validating orders before submission.
 * Checks account balances, instrument validity, and position limits.
 */
@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private final AccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;
    private final PositionRepository positionRepository;

    public ValidationService(AccountRepository accountRepository,
                            InstrumentRepository instrumentRepository,
                            PositionRepository positionRepository) {
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
        this.positionRepository = positionRepository;
    }

    /**
     * Validate an order before submission.
     * Returns a list of validation errors, empty if valid.
     */
    public List<String> validateOrder(Order order) {
        List<String> errors = new ArrayList<>();

        // Basic field validation
        if (order.getAccountId() == null || order.getAccountId().isBlank()) {
            errors.add("Account ID is required");
        }
        if (order.getInstrumentId() == null || order.getInstrumentId().isBlank()) {
            errors.add("Instrument ID is required");
        }
        if (order.getQuantity() == null || order.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Quantity must be positive");
        }
        if (order.getSide() == null) {
            errors.add("Order side is required");
        }
        if (order.getType() == null) {
            errors.add("Order type is required");
        }

        // Price validation for limit orders
        if (order.getType() == OrderType.LIMIT || order.getType() == OrderType.STOP_LIMIT) {
            if (order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Limit price must be positive for limit orders");
            }
        }

        // Stop price validation
        if (order.getType() == OrderType.STOP || order.getType() == OrderType.STOP_LIMIT) {
            if (order.getStopPrice() == null || order.getStopPrice().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Stop price must be positive for stop orders");
            }
        }

        // If basic validation fails, return early
        if (!errors.isEmpty()) {
            return errors;
        }

        // Account validation
        Optional<Account> accountOpt = accountRepository.findById(order.getAccountId());
        if (accountOpt.isEmpty()) {
            errors.add("Account not found: " + order.getAccountId());
            return errors;
        }

        Account account = accountOpt.get();
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            errors.add("Account is not active");
            return errors;
        }

        // Instrument validation
        Optional<Instrument> instrumentOpt = instrumentRepository.findById(order.getInstrumentId());
        if (instrumentOpt.isEmpty()) {
            errors.add("Instrument not found: " + order.getInstrumentId());
            return errors;
        }

        Instrument instrument = instrumentOpt.get();
        if (!instrument.isActive()) {
            errors.add("Instrument is not active for trading");
        }
        if (instrument.isExpired()) {
            errors.add("Instrument has expired");
        }

        // Buy order: Check sufficient funds
        if (order.getSide() == OrderSide.BUY) {
            BigDecimal estimatedCost = estimateOrderCost(order);
            if (!account.canBuy(estimatedCost)) {
                errors.add(String.format("Insufficient funds. Required: %s, Available: %s",
                        estimatedCost, account.getAvailableBalance()));
            }
        }

        // Sell order: Check sufficient holdings
        if (order.getSide() == OrderSide.SELL) {
            Optional<Position> positionOpt = positionRepository.findByAccountIdAndInstrumentId(
                    order.getAccountId(), order.getInstrumentId());

            if (positionOpt.isEmpty() || positionOpt.get().getQuantity().compareTo(order.getQuantity()) < 0) {
                BigDecimal available = positionOpt.map(Position::getQuantity).orElse(BigDecimal.ZERO);
                errors.add(String.format("Insufficient holdings. Required: %s, Available: %s",
                        order.getQuantity(), available));
            }
        }

        return errors;
    }

    /**
     * Validate an account exists and is active.
     */
    public boolean isAccountValid(String accountId) {
        return accountRepository.findById(accountId)
                .map(a -> a.getStatus() == Account.AccountStatus.ACTIVE)
                .orElse(false);
    }

    /**
     * Validate an instrument exists and is tradeable.
     */
    public boolean isInstrumentTradeable(String instrumentId) {
        return instrumentRepository.findById(instrumentId)
                .map(i -> i.isActive() && !i.isExpired())
                .orElse(false);
    }

    /**
     * Estimate the cost of an order (for buy orders).
     */
    public BigDecimal estimateOrderCost(Order order) {
        if (order.getSide() != OrderSide.BUY) {
            return BigDecimal.ZERO;
        }

        BigDecimal price = order.getPrice();
        if (price == null) {
            // For market orders, use a buffer (e.g., estimate high)
            // In production, this would use last trade price + slippage
            price = new BigDecimal("1000"); // Placeholder
        }

        return order.getQuantity().multiply(price);
    }

    /**
     * Check if account has sufficient balance for a transaction.
     */
    public boolean hasSufficientBalance(String accountId, BigDecimal amount) {
        return accountRepository.findById(accountId)
                .map(account -> account.getAvailableBalance().compareTo(amount) >= 0)
                .orElse(false);
    }

    /**
     * Check if account has sufficient position for selling.
     */
    public boolean hasSufficientPosition(String accountId, String instrumentId, BigDecimal quantity) {
        return positionRepository.findByAccountIdAndInstrumentId(accountId, instrumentId)
                .map(position -> position.getQuantity().compareTo(quantity) >= 0)
                .orElse(false);
    }
}
