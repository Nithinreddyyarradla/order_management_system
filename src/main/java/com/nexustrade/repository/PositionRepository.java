package com.nexustrade.repository;

import com.nexustrade.model.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, String> {

    List<Position> findByAccountId(String accountId);

    Optional<Position> findByAccountIdAndInstrumentId(String accountId, String instrumentId);

    List<Position> findByInstrumentId(String instrumentId);

    @Query("SELECT p FROM Position p WHERE p.accountId = :accountId AND p.quantity != 0")
    List<Position> findActivePositionsByAccountId(@Param("accountId") String accountId);

    @Query("SELECT p FROM Position p WHERE p.quantity < 0")
    List<Position> findShortPositions();

    @Query("SELECT SUM(p.marketValue) FROM Position p WHERE p.accountId = :accountId")
    BigDecimal sumMarketValueByAccountId(@Param("accountId") String accountId);

    @Query("SELECT SUM(p.unrealizedPnl) FROM Position p WHERE p.accountId = :accountId")
    BigDecimal sumUnrealizedPnlByAccountId(@Param("accountId") String accountId);

    @Query("SELECT p.instrumentId, SUM(p.quantity) FROM Position p GROUP BY p.instrumentId HAVING SUM(p.quantity) != 0")
    List<Object[]> getAggregatedPositionsByInstrument();
}
