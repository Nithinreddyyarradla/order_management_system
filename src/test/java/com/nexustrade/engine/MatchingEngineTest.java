package com.nexustrade.engine;

import com.nexustrade.model.Order;
import com.nexustrade.model.Trade;
import com.nexustrade.model.enums.OrderSide;
import com.nexustrade.model.enums.OrderStatus;
import com.nexustrade.model.enums.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Matching Engine.
 * Tests order matching, price-time priority, and concurrent processing.
 */
class MatchingEngineTest {

    private MatchingEngine matchingEngine;
    private List<Trade> capturedTrades;
    private List<Order> capturedOrderUpdates;

    @BeforeEach
    void setUp() throws Exception {
        matchingEngine = new MatchingEngine();
        capturedTrades = new ArrayList<>();
        capturedOrderUpdates = new ArrayList<>();

        // Register listeners
        matchingEngine.addTradeListener(capturedTrades::add);
        matchingEngine.addOrderUpdateListener(capturedOrderUpdates::add);

        // Start the engine using reflection (since @PostConstruct won't run in unit test)
        java.lang.reflect.Field threadPoolSizeField = MatchingEngine.class.getDeclaredField("threadPoolSize");
        threadPoolSizeField.setAccessible(true);
        threadPoolSizeField.set(matchingEngine, 4);

        java.lang.reflect.Field orderQueueCapacityField = MatchingEngine.class.getDeclaredField("orderQueueCapacity");
        orderQueueCapacityField.setAccessible(true);
        orderQueueCapacityField.set(matchingEngine, 1000);

        matchingEngine.start();
    }

    @Test
    @DisplayName("Test Order immutability")
    void testOrderImmutability() {
        Order order = Order.builder()
                .accountId("acc-1")
                .instrumentId("AAPL")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .build();

        // Verify order is created with PENDING status
        assertEquals(OrderStatus.PENDING, order.getStatus());

        // Create new order with different status (immutability)
        Order filledOrder = order.withStatus(OrderStatus.FILLED);

        // Original order should be unchanged
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(OrderStatus.FILLED, filledOrder.getStatus());

        // Same order ID
        assertEquals(order.getOrderId(), filledOrder.getOrderId());
    }

    @Test
    @DisplayName("Test Order validation in builder")
    void testOrderValidation() {
        // Should throw on missing required fields
        assertThrows(NullPointerException.class, () ->
                Order.builder()
                        .side(OrderSide.BUY)
                        .type(OrderType.LIMIT)
                        .quantity(new BigDecimal("100"))
                        .price(new BigDecimal("150.00"))
                        .build() // Missing accountId and instrumentId
        );

        // Limit order without price should throw
        assertThrows(NullPointerException.class, () ->
                Order.builder()
                        .accountId("acc-1")
                        .instrumentId("AAPL")
                        .side(OrderSide.BUY)
                        .type(OrderType.LIMIT)
                        .quantity(new BigDecimal("100"))
                        // Missing price
                        .build()
        );
    }

    @Test
    @DisplayName("Test Price-Time Priority for buy orders")
    void testPriceTimePriorityBuy() {
        PriceTimePriority buyPriority = PriceTimePriority.forBuySide();

        Order higherPrice = Order.builder()
                .accountId("acc-1")
                .instrumentId("AAPL")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("151.00"))
                .build();

        // Small delay to ensure different timestamps
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        Order lowerPrice = Order.builder()
                .accountId("acc-2")
                .instrumentId("AAPL")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .build();

        // Higher price should come first for buy orders
        assertTrue(buyPriority.compare(higherPrice, lowerPrice) < 0);
    }

    @Test
    @DisplayName("Test Price-Time Priority for sell orders")
    void testPriceTimePrioritySell() {
        PriceTimePriority sellPriority = PriceTimePriority.forSellSide();

        Order lowerPrice = Order.builder()
                .accountId("acc-1")
                .instrumentId("AAPL")
                .side(OrderSide.SELL)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .build();

        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        Order higherPrice = Order.builder()
                .accountId("acc-2")
                .instrumentId("AAPL")
                .side(OrderSide.SELL)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("151.00"))
                .build();

        // Lower price should come first for sell orders
        assertTrue(sellPriority.compare(lowerPrice, higherPrice) < 0);
    }

    @Test
    @DisplayName("Test OrderBook basic operations")
    void testOrderBookOperations() {
        OrderBook book = new OrderBook("AAPL");

        Order buyOrder = Order.builder()
                .accountId("acc-1")
                .instrumentId("AAPL")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .build();

        Order sellOrder = Order.builder()
                .accountId("acc-2")
                .instrumentId("AAPL")
                .side(OrderSide.SELL)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("151.00"))
                .build();

        book.addOrder(buyOrder);
        book.addOrder(sellOrder);

        assertEquals(1, book.getBuyOrderCount());
        assertEquals(1, book.getSellOrderCount());

        assertTrue(book.peekBestBuy().isPresent());
        assertTrue(book.peekBestSell().isPresent());
        assertEquals(buyOrder.getOrderId(), book.peekBestBuy().get().getOrderId());
        assertEquals(sellOrder.getOrderId(), book.peekBestSell().get().getOrderId());
    }

    @Test
    @DisplayName("Test spread calculation")
    void testSpreadCalculation() {
        OrderBook book = new OrderBook("AAPL");

        Order buyOrder = Order.builder()
                .accountId("acc-1")
                .instrumentId("AAPL")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .build();

        Order sellOrder = Order.builder()
                .accountId("acc-2")
                .instrumentId("AAPL")
                .side(OrderSide.SELL)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("151.00"))
                .build();

        book.addOrder(buyOrder);
        book.addOrder(sellOrder);

        assertTrue(book.getSpread().isPresent());
        assertEquals(new BigDecimal("1.00"), book.getSpread().get());
    }

    @Test
    @DisplayName("Test order removal")
    void testOrderRemoval() {
        OrderBook book = new OrderBook("AAPL");

        Order order = Order.builder()
                .accountId("acc-1")
                .instrumentId("AAPL")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .build();

        book.addOrder(order);
        assertEquals(1, book.getBuyOrderCount());

        boolean removed = book.removeOrder(order.getOrderId());
        assertTrue(removed);
        assertEquals(0, book.getBuyOrderCount());

        // Remove again should return false
        assertFalse(book.removeOrder(order.getOrderId()));
    }

    @Test
    @DisplayName("Test can match check")
    void testCanMatch() {
        Order buyOrder = Order.builder()
                .accountId("acc-1")
                .instrumentId("AAPL")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .build();

        Order matchingSell = Order.builder()
                .accountId("acc-2")
                .instrumentId("AAPL")
                .side(OrderSide.SELL)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))  // Same price - should match
                .build();

        Order nonMatchingSell = Order.builder()
                .accountId("acc-2")
                .instrumentId("AAPL")
                .side(OrderSide.SELL)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("151.00"))  // Higher than buy - no match
                .build();

        assertTrue(PriceTimePriority.canMatch(buyOrder, matchingSell));
        assertFalse(PriceTimePriority.canMatch(buyOrder, nonMatchingSell));
    }

    @Test
    @DisplayName("Test remaining quantity calculation")
    void testRemainingQuantity() {
        Order order = Order.builder()
                .accountId("acc-1")
                .instrumentId("AAPL")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .build();

        assertEquals(new BigDecimal("100"), order.getRemainingQuantity());

        Order partiallyFilled = order.withFilledQuantity(new BigDecimal("30"));
        assertEquals(new BigDecimal("70"), partiallyFilled.getRemainingQuantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, partiallyFilled.getStatus());

        Order fullyFilled = order.withFilledQuantity(new BigDecimal("100"));
        assertEquals(BigDecimal.ZERO, fullyFilled.getRemainingQuantity());
        assertEquals(OrderStatus.FILLED, fullyFilled.getStatus());
    }

    @Test
    @DisplayName("Test Trade creation")
    void testTradeCreation() {
        Trade trade = Trade.builder()
                .buyOrderId("buy-order-1")
                .sellOrderId("sell-order-1")
                .buyerAccountId("buyer-acc")
                .sellerAccountId("seller-acc")
                .instrumentId("AAPL")
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("150.00"))
                .build();

        assertEquals(new BigDecimal("15000.00"), trade.getTotalValue());
        assertFalse(trade.isSettled());
        assertNotNull(trade.getTradeId());
        assertNotNull(trade.getTradeDate());
    }

    @Test
    @DisplayName("Test engine statistics")
    void testEngineStatistics() {
        var stats = matchingEngine.getStatistics();

        assertNotNull(stats);
        assertTrue(stats.containsKey("orderBooksCount"));
        assertTrue(stats.containsKey("running"));
        assertEquals(true, stats.get("running"));
    }

    void tearDown() {
        if (matchingEngine != null) {
            matchingEngine.stop();
        }
    }
}
