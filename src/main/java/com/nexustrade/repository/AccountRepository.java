package com.nexustrade.repository;

import com.nexustrade.model.Account;
import com.nexustrade.model.Account.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByStatus(AccountStatus status);

    @Query("SELECT a FROM Account a WHERE a.status = 'ACTIVE' AND a.cashBalance < :threshold")
    List<Account> findAccountsWithLowBalance(@Param("threshold") BigDecimal threshold);

    @Query("SELECT a FROM Account a WHERE a.currency = :currency AND a.status = 'ACTIVE'")
    List<Account> findActiveByCurrency(@Param("currency") String currency);

    @Modifying
    @Query("UPDATE Account a SET a.cashBalance = a.cashBalance + :amount, " +
           "a.availableBalance = a.availableBalance + :amount WHERE a.accountId = :accountId")
    int creditAccount(@Param("accountId") String accountId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("UPDATE Account a SET a.cashBalance = a.cashBalance - :amount, " +
           "a.availableBalance = a.availableBalance - :amount WHERE a.accountId = :accountId " +
           "AND a.availableBalance >= :amount")
    int debitAccount(@Param("accountId") String accountId, @Param("amount") BigDecimal amount);

    @Query("SELECT SUM(a.cashBalance) FROM Account a WHERE a.currency = :currency AND a.status = 'ACTIVE'")
    BigDecimal sumCashBalanceByCurrency(@Param("currency") String currency);
}
