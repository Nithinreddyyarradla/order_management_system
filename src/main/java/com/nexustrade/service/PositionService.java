package com.nexustrade.service;

import com.nexustrade.model.Position;
import com.nexustrade.repository.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service for position management.
 * Handles position queries and calculations.
 */
@Service
public class PositionService {

    private static final Logger log = LoggerFactory.getLogger(PositionService.class);

    private final PositionRepository positionRepository;

    public PositionService(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    /**
     * Get all positions for an account.
     */
    public List<Position> getPositionsByAccount(String accountId) {
        return positionRepository.findByAccountId(accountId);
    }

    /**
     * Get active (non-zero) positions for an account.
     */
    public List<Position> getActivePositions(String accountId) {
        return positionRepository.findActivePositionsByAccountId(accountId);
    }

    /**
     * Get specific position for account and instrument.
     */
    public Optional<Position> getPosition(String accountId, String instrumentId) {
        return positionRepository.findByAccountIdAndInstrumentId(accountId, instrumentId);
    }

    /**
     * Get total market value of all positions for an account.
     */
    public BigDecimal getTotalMarketValue(String accountId) {
        BigDecimal total = positionRepository.sumMarketValueByAccountId(accountId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Get total unrealized P&L for an account.
     */
    public BigDecimal getTotalUnrealizedPnl(String accountId) {
        BigDecimal total = positionRepository.sumUnrealizedPnlByAccountId(accountId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Check if account has sufficient quantity to sell.
     */
    public boolean canSell(String accountId, String instrumentId, BigDecimal quantity) {
        return positionRepository.findByAccountIdAndInstrumentId(accountId, instrumentId)
                .map(p -> p.getQuantity().compareTo(quantity) >= 0)
                .orElse(false);
    }

    /**
     * Get all short positions (for risk monitoring).
     */
    public List<Position> getShortPositions() {
        return positionRepository.findShortPositions();
    }

    /**
     * Update position with current market prices.
     */
    @Transactional
    public Position updateMarketValue(String positionId, BigDecimal currentPrice) {
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Position not found: " + positionId));

        BigDecimal marketValue = position.getQuantity().multiply(currentPrice);
        BigDecimal unrealizedPnl = marketValue.subtract(position.getCostBasis());

        // Since Position is immutable, we need to create a new one with updated values
        // This would require adding a withMarketValue method to Position
        // For now, we'll use the builder
        Position updated = Position.builder()
                .positionId(position.getPositionId())
                .accountId(position.getAccountId())
                .instrumentId(position.getInstrumentId())
                .quantity(position.getQuantity())
                .averageCost(position.getAverageCost())
                .marketValue(marketValue)
                .unrealizedPnl(unrealizedPnl)
                .realizedPnl(position.getRealizedPnl())
                .build();

        return positionRepository.save(updated);
    }

    /**
     * Get portfolio summary for an account.
     */
    public PortfolioSummary getPortfolioSummary(String accountId) {
        List<Position> positions = positionRepository.findActivePositionsByAccountId(accountId);

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalUnrealizedPnl = BigDecimal.ZERO;
        BigDecimal totalRealizedPnl = BigDecimal.ZERO;

        for (Position position : positions) {
            if (position.getMarketValue() != null) {
                totalValue = totalValue.add(position.getMarketValue());
            }
            totalCost = totalCost.add(position.getCostBasis());
            if (position.getUnrealizedPnl() != null) {
                totalUnrealizedPnl = totalUnrealizedPnl.add(position.getUnrealizedPnl());
            }
            if (position.getRealizedPnl() != null) {
                totalRealizedPnl = totalRealizedPnl.add(position.getRealizedPnl());
            }
        }

        return new PortfolioSummary(
                accountId,
                positions.size(),
                totalValue,
                totalCost,
                totalUnrealizedPnl,
                totalRealizedPnl
        );
    }

    /**
     * Portfolio summary record.
     */
    public record PortfolioSummary(
            String accountId,
            int positionCount,
            BigDecimal totalMarketValue,
            BigDecimal totalCostBasis,
            BigDecimal totalUnrealizedPnl,
            BigDecimal totalRealizedPnl
    ) {}
}
