package com.nexustrade.controller;

import com.nexustrade.model.Account;
import com.nexustrade.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * REST controller for account management.
 */
@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Create a new account.
     */
    @PostMapping
    public ResponseEntity<?> createAccount(@RequestBody CreateAccountRequest request) {
        log.info("Creating new account: {}", request.accountNumber());

        try {
            Account account = Account.builder()
                    .accountNumber(request.accountNumber())
                    .accountName(request.accountName())
                    .accountType(Account.AccountType.valueOf(request.accountType().toUpperCase()))
                    .currency(request.currency() != null ? request.currency() : "USD")
                    .cashBalance(request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO)
                    .availableBalance(request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO)
                    .build();

            Account saved = accountRepository.save(account);

            return ResponseEntity.status(HttpStatus.CREATED).body(toAccountResponse(saved));

        } catch (Exception e) {
            log.error("Error creating account", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get account by ID.
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String accountId) {
        return accountRepository.findById(accountId)
                .map(this::toAccountResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get account balance.
     */
    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String accountId) {
        return accountRepository.findById(accountId)
                .map(account -> new BalanceResponse(
                        account.getAccountId(),
                        account.getCashBalance(),
                        account.getAvailableBalance(),
                        account.getMarginBalance(),
                        account.getBuyingPower(),
                        account.getCurrency()
                ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deposit funds to account.
     */
    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<?> deposit(
            @PathVariable String accountId,
            @RequestBody DepositRequest request) {

        log.info("Depositing {} to account {}", request.amount(), accountId);

        int updated = accountRepository.creditAccount(accountId, request.amount());

        if (updated > 0) {
            return accountRepository.findById(accountId)
                    .map(account -> ResponseEntity.ok(Map.of(
                            "accountId", accountId,
                            "depositedAmount", request.amount(),
                            "newBalance", account.getCashBalance()
                    )))
                    .orElse(ResponseEntity.notFound().build());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Withdraw funds from account.
     */
    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<?> withdraw(
            @PathVariable String accountId,
            @RequestBody WithdrawRequest request) {

        log.info("Withdrawing {} from account {}", request.amount(), accountId);

        int updated = accountRepository.debitAccount(accountId, request.amount());

        if (updated > 0) {
            return accountRepository.findById(accountId)
                    .map(account -> ResponseEntity.ok(Map.of(
                            "accountId", accountId,
                            "withdrawnAmount", request.amount(),
                            "newBalance", account.getCashBalance()
                    )))
                    .orElse(ResponseEntity.notFound().build());
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Insufficient funds or account not found"
            ));
        }
    }

    private AccountResponse toAccountResponse(Account account) {
        return new AccountResponse(
                account.getAccountId(),
                account.getAccountNumber(),
                account.getAccountName(),
                account.getAccountType().name(),
                account.getStatus().name(),
                account.getCurrency(),
                account.getCashBalance(),
                account.getAvailableBalance(),
                account.getMarginBalance(),
                account.getBuyingPower(),
                account.getCreatedAt().toString()
        );
    }

    // Request/Response records
    public record CreateAccountRequest(
            String accountNumber,
            String accountName,
            String accountType,
            String currency,
            BigDecimal initialBalance
    ) {}

    public record AccountResponse(
            String accountId,
            String accountNumber,
            String accountName,
            String accountType,
            String status,
            String currency,
            BigDecimal cashBalance,
            BigDecimal availableBalance,
            BigDecimal marginBalance,
            BigDecimal buyingPower,
            String createdAt
    ) {}

    public record BalanceResponse(
            String accountId,
            BigDecimal cashBalance,
            BigDecimal availableBalance,
            BigDecimal marginBalance,
            BigDecimal buyingPower,
            String currency
    ) {}

    public record DepositRequest(BigDecimal amount) {}

    public record WithdrawRequest(BigDecimal amount) {}
}
