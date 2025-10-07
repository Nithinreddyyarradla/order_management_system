package com.nexustrade.repository;

import com.nexustrade.model.Order;
import com.nexustrade.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByAccountId(String accountId);

    List<Order> findByInstrumentId(String instrumentId);

    List<Order> findByAccountIdAndStatus(String accountId, OrderStatus status);

    List<Order> findByStatus(OrderStatus status);

    Page<Order> findByAccountIdOrderByCreatedAtDesc(String accountId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.status IN :statuses ORDER BY o.createdAt")
    List<Order> findByStatusIn(@Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt < :before")
    List<Order> findByStatusAndCreatedAtBefore(
            @Param("status") OrderStatus status,
            @Param("before") Instant before);

    @Query("SELECT o FROM Order o WHERE o.accountId = :accountId AND o.instrumentId = :instrumentId AND o.status IN :statuses")
    List<Order> findActiveOrdersForAccountAndInstrument(
            @Param("accountId") String accountId,
            @Param("instrumentId") String instrumentId,
            @Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.accountId = :accountId AND o.status = :status")
    long countByAccountIdAndStatus(@Param("accountId") String accountId, @Param("status") OrderStatus status);

    Optional<Order> findByClientOrderIdAndAccountId(String clientOrderId, String accountId);
}
