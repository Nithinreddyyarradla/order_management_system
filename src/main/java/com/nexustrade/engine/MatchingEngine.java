package com.nexustrade.engine;

import com.nexustrade.model.Order;
import com.nexustrade.model.Trade;
import com.nexustrade.model.enums.OrderSide;
import com.nexustrade.model.enums.OrderStatus;
import com.nexustrade.model.enums.OrderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * High-throughput Matching Engine for order execution.
 *
 * Uses a fixed thread pool to process orders asynchronously,
 * ensuring high throughput without blocking.
 *
 * Key features:
 * - Price-Time Priority matching
 * - Partial fill support
 * - Thread-safe execution
 * - Async trade callbacks
 */
@Component
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    @Value("${nexustrade.matching-engine.thread-pool-size:8}")
    private int threadPoolSize;

    @Value("${nexustrade.matching-engine.order-queue-capacity:100000}")
    private int orderQueueCapacity;

    // Order books per instrument
    private final ConcurrentMap<String, OrderBook> orderBooks;

    // Thread pool for async matching
    private ExecutorService executorService;

    // Incoming order queue
    private BlockingQueue<Order> incomingOrders;

    // Callbacks for trade notifications
    private final List<Consumer<Trade>> tradeListeners;
    private final List<Consumer<Order>> orderUpdateListeners;

    // Engine state
    private volatile boolean running;

    public MatchingEngine() {
        this.orderBooks = new ConcurrentHashMap<>();
        this.tradeListeners = new CopyOnWriteArrayList<>();
        this.orderUpdateListeners = new CopyOnWriteArrayList<>();
    }

    @PostConstruct
    public void start() {
        log.info("Starting Matching Engine with {} threads", threadPoolSize);

        this.executorService = Executors.newFixedThreadPool(
                threadPoolSize,
                new ThreadFactory() {
                    private int counter = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "matching-engine-" + counter++);
                        t.setDaemon(true);
                        return t;
                    }
                }
        );

        this.incomingOrders = new LinkedBlockingQueue<>(orderQueueCapacity);
        this.running = true;

        // Start order processing threads
        for (int i = 0; i < threadPoolSize; i++) {
            executorService.submit(this::processOrders);
        }

        log.info("Matching Engine started successfully");
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping Matching Engine...");
        running = false;

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("Matching Engine stopped");
    }

    /**
     * Submit an order for matching.
     * Returns a future that completes when the order is processed.
     */
    public CompletableFuture<Order> submitOrder(Order order) {
        CompletableFuture<Order> future = new CompletableFuture<>();

        try {
            // Validate order
            if (!order.isMatchable()) {
                Order rejected = order.withStatus(OrderStatus.REJECTED);
                future.complete(rejected);
                return future;
            }

            // Queue for processing
            if (!incomingOrders.offer(order, 1, TimeUnit.SECONDS)) {
                log.warn("Order queue full, rejecting order: {}", order.getOrderId());
                Order rejected = order.withStatus(OrderStatus.REJECTED);
                future.complete(rejected);
                return future;
            }

            // The order will be processed async, complete the future
            future.complete(order.withStatus(OrderStatus.OPEN));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Cancel an existing order.
     */
    public boolean cancelOrder(String instrumentId, String orderId) {
        OrderBook book = orderBooks.get(instrumentId);
        if (book == null) {
            return false;
        }

        Optional<Order> orderOpt = book.getOrder(orderId);
        if (orderOpt.isEmpty()) {
            return false;
        }

        Order order = orderOpt.get();
        if (!order.getStatus().isActive()) {
            return false;
        }

        boolean removed = book.removeOrder(orderId);
        if (removed) {
            Order cancelled = order.withStatus(OrderStatus.CANCELLED);
            notifyOrderUpdate(cancelled);
        }

        return removed;
    }

    /**
     * Main order processing loop.
     */
    private void processOrders() {
        while (running) {
            try {
                Order order = incomingOrders.poll(100, TimeUnit.MILLISECONDS);
                if (order == null) {
                    continue;
                }

                processOrder(order);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing order", e);
            }
        }
    }

    /**
     * Process a single order - attempt matching then add to book if unfilled.
     */
    private void processOrder(Order order) {
        String instrumentId = order.getInstrumentId();
        OrderBook book = orderBooks.computeIfAbsent(instrumentId, OrderBook::new);

        log.debug("Processing order: {}", order);

        // Try to match the order
        List<Trade> trades = matchOrder(order, book);

        // If order is still active (not fully filled), add to book
        Order currentOrder = order;
        for (Trade trade : trades) {
            BigDecimal newFilledQty = currentOrder.getFilledQuantity().add(trade.getQuantity());
            currentOrder = currentOrder.withFilledQuantity(newFilledQty);
        }

        if (currentOrder.getStatus().isActive() && currentOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            // For limit orders, add to book
            if (currentOrder.getType() == OrderType.LIMIT || currentOrder.getType() == OrderType.STOP_LIMIT) {
                book.addOrder(currentOrder);
                log.debug("Order added to book: {}", currentOrder);
            } else {
                // Market orders that can't be filled are cancelled
                currentOrder = currentOrder.withStatus(OrderStatus.CANCELLED);
            }
        }

        notifyOrderUpdate(currentOrder);
    }

    /**
     * Match an incoming order against the order book.
     */
    private List<Trade> matchOrder(Order incomingOrder, OrderBook book) {
        List<Trade> trades = new ArrayList<>();

        OrderSide oppositeSide = incomingOrder.getSide().opposite();
        BigDecimal remainingQty = incomingOrder.getRemainingQuantity();

        while (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
            // Get best opposing order
            Optional<Order> oppositeOrderOpt;
            if (incomingOrder.getSide() == OrderSide.BUY) {
                oppositeOrderOpt = book.peekBestSell();
            } else {
                oppositeOrderOpt = book.peekBestBuy();
            }

            if (oppositeOrderOpt.isEmpty()) {
                break; // No orders to match against
            }

            Order oppositeOrder = oppositeOrderOpt.get();

            // Check if prices cross
            if (!canPricesCross(incomingOrder, oppositeOrder)) {
                break; // No more matches possible
            }

            // Calculate match quantity
            BigDecimal matchQty = remainingQty.min(oppositeOrder.getRemainingQuantity());

            // Determine execution price (price of the resting order)
            BigDecimal executionPrice = determineExecutionPrice(incomingOrder, oppositeOrder);

            // Create trade
            Trade trade = createTrade(incomingOrder, oppositeOrder, matchQty, executionPrice);
            trades.add(trade);
            notifyTrade(trade);

            log.info("Trade executed: {} shares of {} @ {}",
                    matchQty, incomingOrder.getInstrumentId(), executionPrice);

            // Update remaining quantity
            remainingQty = remainingQty.subtract(matchQty);

            // Update opposite order
            BigDecimal newOppositeFilled = oppositeOrder.getFilledQuantity().add(matchQty);
            Order updatedOpposite = oppositeOrder.withFilledQuantity(newOppositeFilled);

            if (updatedOpposite.getStatus() == OrderStatus.FILLED) {
                // Remove filled order from book
                if (incomingOrder.getSide() == OrderSide.BUY) {
                    book.pollBestSell();
                } else {
                    book.pollBestBuy();
                }
            } else {
                book.updateOrder(updatedOpposite);
            }

            notifyOrderUpdate(updatedOpposite);
        }

        return trades;
    }

    /**
     * Check if incoming and resting order prices can cross (match).
     */
    private boolean canPricesCross(Order incoming, Order resting) {
        // Market orders always cross
        if (incoming.getPrice() == null || resting.getPrice() == null) {
            return true;
        }

        if (incoming.getSide() == OrderSide.BUY) {
            // Buy at or above sell price
            return incoming.getPrice().compareTo(resting.getPrice()) >= 0;
        } else {
            // Sell at or below buy price
            return incoming.getPrice().compareTo(resting.getPrice()) <= 0;
        }
    }

    /**
     * Determine execution price - typically the resting order's price.
     */
    private BigDecimal determineExecutionPrice(Order incoming, Order resting) {
        // Price discovery: use resting order's price (or incoming if market)
        if (resting.getPrice() != null) {
            return resting.getPrice();
        }
        if (incoming.getPrice() != null) {
            return incoming.getPrice();
        }
        // Both are market orders - would need a reference price
        throw new IllegalStateException("Cannot determine price for two market orders");
    }

    /**
     * Create a trade record.
     */
    private Trade createTrade(Order buy, Order sell, BigDecimal quantity, BigDecimal price) {
        Order buyOrder = buy.getSide() == OrderSide.BUY ? buy : sell;
        Order sellOrder = buy.getSide() == OrderSide.SELL ? buy : sell;

        return Trade.builder()
                .buyOrderId(buyOrder.getOrderId())
                .sellOrderId(sellOrder.getOrderId())
                .buyerAccountId(buyOrder.getAccountId())
                .sellerAccountId(sellOrder.getAccountId())
                .instrumentId(buy.getInstrumentId())
                .quantity(quantity)
                .price(price)
                .build();
    }

    /**
     * Register a trade listener.
     */
    public void addTradeListener(Consumer<Trade> listener) {
        tradeListeners.add(listener);
    }

    /**
     * Register an order update listener.
     */
    public void addOrderUpdateListener(Consumer<Order> listener) {
        orderUpdateListeners.add(listener);
    }

    private void notifyTrade(Trade trade) {
        for (Consumer<Trade> listener : tradeListeners) {
            try {
                listener.accept(trade);
            } catch (Exception e) {
                log.error("Error notifying trade listener", e);
            }
        }
    }

    private void notifyOrderUpdate(Order order) {
        for (Consumer<Order> listener : orderUpdateListeners) {
            try {
                listener.accept(order);
            } catch (Exception e) {
                log.error("Error notifying order update listener", e);
            }
        }
    }

    /**
     * Get order book for an instrument.
     */
    public Optional<OrderBook> getOrderBook(String instrumentId) {
        return Optional.ofNullable(orderBooks.get(instrumentId));
    }

    /**
     * Get all active order books.
     */
    public Collection<OrderBook> getAllOrderBooks() {
        return Collections.unmodifiableCollection(orderBooks.values());
    }

    /**
     * Get engine statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("orderBooksCount", orderBooks.size());
        stats.put("pendingOrders", incomingOrders.size());
        stats.put("running", running);

        int totalBuys = 0;
        int totalSells = 0;
        for (OrderBook book : orderBooks.values()) {
            totalBuys += book.getBuyOrderCount();
            totalSells += book.getSellOrderCount();
        }
        stats.put("totalBuyOrders", totalBuys);
        stats.put("totalSellOrders", totalSells);

        return stats;
    }
}
