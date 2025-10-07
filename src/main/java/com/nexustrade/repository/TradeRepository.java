package com.nexustrade.repository;

import com.nexustrade.model.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, String> {

    List<Trade> findByBuyerAccountIdOrSellerAccountId(String buyerAccountId, String sellerAccountId);

    List<Trade> findByInstrumentId(String instrumentId);

    List<Trade> findBySettledFalse();

    Page<Trade> findByBuyerAccountIdOrSellerAccountIdOrderByTradeDateDesc(
            String buyerAccountId, String sellerAccountId, Pageable pageable);

    @Query("SELECT t FROM Trade t WHERE t.tradeDate BETWEEN :start AND :end ORDER BY t.tradeDate")
    List<Trade> findByTradeDateBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT t FROM Trade t WHERE t.settled = false AND t.tradeDate < :before")
    List<Trade> findUnsettledTradesBefore(@Param("before") Instant before);

    @Query("SELECT SUM(t.totalValue) FROM Trade t WHERE t.instrumentId = :instrumentId AND t.tradeDate BETWEEN :start AND :end")
    BigDecimal sumTotalValueByInstrumentAndDateRange(
            @Param("instrumentId") String instrumentId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT COUNT(t) FROM Trade t WHERE t.tradeDate BETWEEN :start AND :end")
    long countTradesBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT t.instrumentId, SUM(t.quantity), SUM(t.totalValue) FROM Trade t " +
           "WHERE t.tradeDate BETWEEN :start AND :end GROUP BY t.instrumentId")
    List<Object[]> getTradeVolumeByInstrument(@Param("start") Instant start, @Param("end") Instant end);
}
