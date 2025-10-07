package com.nexustrade.service;

import com.nexustrade.engine.MatchingEngine;
import com.nexustrade.model.Account;
import com.nexustrade.model.Instrument;
import com.nexustrade.model.Order;
import com.nexustrade.model.enums.InstrumentType;
import com.nexustrade.model.enums.OrderSide;
import com.nexustrade.model.enums.OrderStatus;
import com.nexustrade.model.enums.OrderType;
import com.nexustrade.repository.AccountRepository;
import com.nexustrade.repository.InstrumentRepository;
import com.nexustrade.repository.OrderRepository;
import com.nexustrade.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private MatchingEngine matchingEngine;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private ValidationService validationService;
    private OrderService orderService;

    private Account testAccount;
    private Instrument testInstrument;

    @BeforeEach
    void setUp() {
        validationService = new ValidationService(accountRepository, instrumentRepository, positionRepository);
        orderService = new OrderService(orderRepository, validationService, matchingEngine, kafkaTemplate);

        // Setup test data
        testAccount = Account.builder()
                .accountId("test-account-id")
                .accountNumber("NX1000001")
                .accountName("Test Account")
                .cashBalance(new BigDecimal("100000.00"))
                .availableBalance(new BigDecimal("100000.00"))
                .build();

        testInstrument = Instrument.builder()
                .instrumentId("test-instrument-id")
                .symbol("AAPL")
                .name("Apple Inc.")
                .type(InstrumentType.STOCK)
                .build();
    }

    @Test
    @DisplayName("Validate order - should pass for valid buy order with sufficient funds")
    void testValidOrderWithSufficientFunds() {
        when(accountRepository.findById("test-account-id")).thenReturn(Optional.of(testAccount));
        when(instrumentRepository.findById("test-instrument-id")).thenReturn(Optional.of(testInstrument));

        Order order = Order.builder()
                .accountId("test-account-id")
                .instrumentId("test-instrument-id")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .build();

        List<String> errors = validationService.validateOrder(order);

        assertTrue(errors.isEmpty(), "Should have no validation errors");
    }

    @Test
    @DisplayName("Validate order - should fail for buy order with insufficient funds")
    void testValidOrderWithInsufficientFunds() {
        Account poorAccount = Account.builder()
                .accountId("poor-account")
                .accountNumber("NX1000002")
                .accountName("Poor Account")
                .cashBalance(new BigDecimal("100.00"))
                .availableBalance(new BigDecimal("100.00"))
                .build();

        when(accountRepository.findById("poor-account")).thenReturn(Optional.of(poorAccount));
        when(instrumentRepository.findById("test-instrument-id")).thenReturn(Optional.of(testInstrument));

        Order order = Order.builder()
                .accountId("poor-account")
                .instrumentId("test-instrument-id")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))  // Total: 15000
                .build();

        List<String> errors = validationService.validateOrder(order);

        assertFalse(errors.isEmpty(), "Should have validation errors");
        assertTrue(errors.stream().anyMatch(e -> e.contains("Insufficient funds")));
    }

    @Test
    @DisplayName("Validate order - should fail for non-existent account")
    void testValidateNonExistentAccount() {
        when(accountRepository.findById("non-existent")).thenReturn(Optional.empty());

        Order order = Order.builder()
                .accountId("non-existent")
                .instrumentId("test-instrument-id")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .build();

        List<String> errors = validationService.validateOrder(order);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("Account not found")));
    }

    @Test
    @DisplayName("Validate order - should fail for inactive instrument")
    void testValidateInactiveInstrument() {
        Instrument inactiveInstrument = Instrument.builder()
                .instrumentId("inactive-instrument")
                .symbol("DEAD")
                .name("Delisted Stock")
                .type(InstrumentType.STOCK)
                .active(false)
                .build();

        when(accountRepository.findById("test-account-id")).thenReturn(Optional.of(testAccount));
        when(instrumentRepository.findById("inactive-instrument")).thenReturn(Optional.of(inactiveInstrument));

        Order order = Order.builder()
                .accountId("test-account-id")
                .instrumentId("inactive-instrument")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .build();

        List<String> errors = validationService.validateOrder(order);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("not active")));
    }

    @Test
    @DisplayName("Validate order - should fail for limit order without price")
    void testValidateLimitOrderWithoutPrice() {
        // Building a LIMIT order without a price should throw NullPointerException
        assertThrows(NullPointerException.class, () ->
            Order.builder()
                .accountId("test-account-id")
                .instrumentId("test-instrument-id")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                // No price specified - should fail
                .build()
        );
    }

    @Test
    @DisplayName("Submit order - should save and publish to Kafka")
    void testSubmitOrder() {
        when(accountRepository.findById("test-account-id")).thenReturn(Optional.of(testAccount));
        when(instrumentRepository.findById("test-instrument-id")).thenReturn(Optional.of(testInstrument));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        Order order = Order.builder()
                .accountId("test-account-id")
                .instrumentId("test-instrument-id")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .build();

        Order result = orderService.submitOrder(order);

        assertNotNull(result);
        verify(orderRepository).save(any(Order.class));
        verify(kafkaTemplate).send(any(), any(), any());
    }

    @Test
    @DisplayName("Cancel order - should update status")
    void testCancelOrder() {
        Order activeOrder = Order.builder()
                .orderId("order-to-cancel")
                .accountId("test-account-id")
                .instrumentId("test-instrument-id")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .status(OrderStatus.OPEN)
                .build();

        when(orderRepository.findById("order-to-cancel")).thenReturn(Optional.of(activeOrder));
        when(matchingEngine.cancelOrder(any(), any())).thenReturn(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean cancelled = orderService.cancelOrder("order-to-cancel");

        assertTrue(cancelled);
        verify(orderRepository).save(argThat(o ->
                o.getStatus() == OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("Cancel order - should fail for already filled order")
    void testCancelFilledOrder() {
        Order filledOrder = Order.builder()
                .orderId("filled-order")
                .accountId("test-account-id")
                .instrumentId("test-instrument-id")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .status(OrderStatus.FILLED)
                .build();

        when(orderRepository.findById("filled-order")).thenReturn(Optional.of(filledOrder));

        boolean cancelled = orderService.cancelOrder("filled-order");

        assertFalse(cancelled);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Get orders by account - should return list")
    void testGetOrdersByAccount() {
        Order order1 = Order.builder()
                .accountId("test-account-id")
                .instrumentId("AAPL")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .build();

        Order order2 = Order.builder()
                .accountId("test-account-id")
                .instrumentId("GOOGL")
                .side(OrderSide.SELL)
                .type(OrderType.MARKET)
                .quantity(new BigDecimal("50"))
                .build();

        when(orderRepository.findByAccountId("test-account-id"))
                .thenReturn(List.of(order1, order2));

        List<Order> orders = orderService.getOrdersByAccount("test-account-id");

        assertEquals(2, orders.size());
    }
}
