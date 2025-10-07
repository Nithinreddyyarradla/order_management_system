package com.nexustrade.controller;

import com.nexustrade.model.Position;
import com.nexustrade.service.PositionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST controller for position queries.
 */
@RestController
@RequestMapping("/v1/positions")
public class PositionController {

    private static final Logger log = LoggerFactory.getLogger(PositionController.class);

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    /**
     * Get all positions for an account.
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<List<PositionResponse>> getPositions(@PathVariable String accountId) {
        log.debug("Getting positions for account: {}", accountId);

        List<Position> positions = positionService.getActivePositions(accountId);

        List<PositionResponse> responses = positions.stream()
                .map(this::toPositionResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Get specific position for account and instrument.
     */
    @GetMapping("/{accountId}/{instrumentId}")
    public ResponseEntity<PositionResponse> getPosition(
            @PathVariable String accountId,
            @PathVariable String instrumentId) {

        return positionService.getPosition(accountId, instrumentId)
                .map(this::toPositionResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get portfolio summary for an account.
     */
    @GetMapping("/{accountId}/summary")
    public ResponseEntity<PortfolioSummaryResponse> getPortfolioSummary(@PathVariable String accountId) {
        log.debug("Getting portfolio summary for account: {}", accountId);

        PositionService.PortfolioSummary summary = positionService.getPortfolioSummary(accountId);

        PortfolioSummaryResponse response = new PortfolioSummaryResponse(
                summary.accountId(),
                summary.positionCount(),
                summary.totalMarketValue(),
                summary.totalCostBasis(),
                summary.totalUnrealizedPnl(),
                summary.totalRealizedPnl()
        );

        return ResponseEntity.ok(response);
    }

    private PositionResponse toPositionResponse(Position position) {
        return new PositionResponse(
                position.getPositionId(),
                position.getAccountId(),
                position.getInstrumentId(),
                position.getQuantity(),
                position.getAverageCost(),
                position.getMarketValue(),
                position.getCostBasis(),
                position.getUnrealizedPnl(),
                position.getRealizedPnl(),
                position.getUpdatedAt().toString()
        );
    }

    public record PositionResponse(
            String positionId,
            String accountId,
            String instrumentId,
            BigDecimal quantity,
            BigDecimal averageCost,
            BigDecimal marketValue,
            BigDecimal costBasis,
            BigDecimal unrealizedPnl,
            BigDecimal realizedPnl,
            String updatedAt
    ) {}

    public record PortfolioSummaryResponse(
            String accountId,
            int positionCount,
            BigDecimal totalMarketValue,
            BigDecimal totalCostBasis,
            BigDecimal totalUnrealizedPnl,
            BigDecimal totalRealizedPnl
    ) {}
}
