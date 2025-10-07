package com.nexustrade.service;

import com.nexustrade.model.Account;
import com.nexustrade.model.Position;
import com.nexustrade.model.Trade;
import com.nexustrade.repository.AccountRepository;
import com.nexustrade.repository.PositionRepository;
import com.nexustrade.repository.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service for trade settlement.
 * Handles ACID-compliant settlement ensuring money never disappears.
 *
 * CRITICAL: Settlement is done in a single transaction with SERIALIZABLE isolation
 * to prevent race conditions and ensure data integrity.
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final TradeRepository tradeRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;

    public SettlementService(TradeRepository tradeRepository,
                            AccountRepository accountRepository,
                            PositionRepository positionRepository) {
        this.tradeRepository = tradeRepository;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
    }

    /**
     * Settle a trade - ACID compliant transaction.
     *
     * In a single transaction:
     * 1. Debit buyer's cash
     * 2. Credit seller's cash
     * 3. Update buyer's position (add shares)
     * 4. Update seller's position (remove shares)
     * 5. Mark trade as settled
     *
     * Uses SERIALIZABLE isolation to prevent concurrent modifications.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void settleTrade(Trade trade) {
        log.info("Settling trade: {}", trade.getTradeId());

        if (trade.isSettled()) {
            log.warn("Trade already settled: {}", trade.getTradeId());
            return;
        }

        BigDecimal tradeValue = trade.getTotalValue();

        // 1. Debit buyer's cash account
        int buyerDebited = accountRepository.debitAccount(trade.getBuyerAccountId(), tradeValue);
        if (buyerDebited == 0) {
            throw new IllegalStateException("Failed to debit buyer account: " + trade.getBuyerAccountId());
        }

        // 2. Credit seller's cash account
        int sellerCredited = accountRepository.creditAccount(trade.getSellerAccountId(), tradeValue);
        if (sellerCredited == 0) {
            throw new IllegalStateException("Failed to credit seller account: " + trade.getSellerAccountId());
        }

        // 3. Update buyer's position (add shares)
        updatePosition(trade.getBuyerAccountId(), trade.getInstrumentId(),
                trade.getQuantity(), trade.getPrice(), true);

        // 4. Update seller's position (remove shares)
        updatePosition(trade.getSellerAccountId(), trade.getInstrumentId(),
                trade.getQuantity(), trade.getPrice(), false);

        // 5. Mark trade as settled
        Trade settledTrade = trade.withSettled(Instant.now());
        tradeRepository.save(settledTrade);

        log.info("Trade settled successfully: {} | Value: {} | Buyer: {} | Seller: {}",
                trade.getTradeId(), tradeValue, trade.getBuyerAccountId(), trade.getSellerAccountId());
    }

    /**
     * Async settlement for non-blocking operation.
     */
    @Async("settlementExecutor")
    public void settleTradeAsync(Trade trade) {
        try {
            settleTrade(trade);
        } catch (Exception e) {
            log.error("Async settlement failed for trade: {}", trade.getTradeId(), e);
        }
    }

    /**
     * Settle all unsettled trades.
     */
    @Transactional
    public int settleAllPendingTrades() {
        List<Trade> unsettledTrades = tradeRepository.findBySettledFalse();
        int settled = 0;

        for (Trade trade : unsettledTrades) {
            try {
                settleTrade(trade);
                settled++;
            } catch (Exception e) {
                log.error("Failed to settle trade: {}", trade.getTradeId(), e);
            }
        }

        log.info("Settled {} pending trades", settled);
        return settled;
    }

    /**
     * Update position for an account/instrument.
     */
    private void updatePosition(String accountId, String instrumentId,
                               BigDecimal quantity, BigDecimal price, boolean isBuy) {
        Optional<Position> existingPosition = positionRepository
                .findByAccountIdAndInstrumentId(accountId, instrumentId);

        Position updatedPosition;
        if (existingPosition.isEmpty()) {
            // Create new position
            if (!isBuy) {
                throw new IllegalStateException("Cannot sell without existing position");
            }
            updatedPosition = Position.builder()
                    .accountId(accountId)
                    .instrumentId(instrumentId)
                    .quantity(quantity)
                    .averageCost(price)
                    .build();
        } else {
            Position current = existingPosition.get();
            if (isBuy) {
                updatedPosition = current.withBuy(quantity, price);
            } else {
                updatedPosition = current.withSell(quantity, price);
            }
        }

        positionRepository.save(updatedPosition);
    }

    /**
     * Get settlement status for a trade.
     */
    public boolean isTradeSettled(String tradeId) {
        return tradeRepository.findById(tradeId)
                .map(Trade::isSettled)
                .orElse(false);
    }

    /**
     * Get unsettled trades older than given time.
     */
    public List<Trade> getStaleUnsettledTrades(Instant before) {
        return tradeRepository.findUnsettledTradesBefore(before);
    }
}
