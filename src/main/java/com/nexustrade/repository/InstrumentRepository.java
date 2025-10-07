package com.nexustrade.repository;

import com.nexustrade.model.Instrument;
import com.nexustrade.model.enums.InstrumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InstrumentRepository extends JpaRepository<Instrument, String> {

    Optional<Instrument> findBySymbol(String symbol);

    List<Instrument> findByType(InstrumentType type);

    List<Instrument> findByActiveTrue();

    List<Instrument> findByTypeAndActiveTrue(InstrumentType type);

    @Query("SELECT i FROM Instrument i WHERE i.expiryDate IS NOT NULL AND i.expiryDate <= :date")
    List<Instrument> findExpiringBefore(@Param("date") LocalDate date);

    @Query("SELECT i FROM Instrument i WHERE i.underlyingInstrumentId = :underlyingId")
    List<Instrument> findDerivativesByUnderlying(@Param("underlyingId") String underlyingId);

    List<Instrument> findByExchange(String exchange);

    @Query("SELECT i FROM Instrument i WHERE i.symbol LIKE :prefix%")
    List<Instrument> findBySymbolStartingWith(@Param("prefix") String prefix);
}
