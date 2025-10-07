package com.nexustrade.engine;

import com.nexustrade.model.Order;
import com.nexustrade.model.enums.OrderSide;
import com.nexustrade.model.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Order Book for a single instrument.
 * Maintains separate buy and sell queues with Price-Time Priority.
 * Thread-safe implementation using concurrent collections and locks.
 */
public class OrderBook {

    private static final int INITIAL_CAPACITY = 1000;

    private final String instrumentId;

    // Buy orders: highest price first
    private final PriorityBlockingQueue<Order> buyOrders;

    // Sell orders: lowest price first
    private final PriorityBlockingQueue<Order> sellOrders;

    // Fast lookup by order ID
    private final Map<String, Order> orderIndex;

    // Lock for complex operations
    private final ReentrantReadWriteLock lock;

    public OrderBook(String instrumentId) {
        this.instrumentId = instrumentId;
        this.buyOrders = new PriorityBlockingQueue<>(INITIAL_CAPACITY, PriceTimePriority.forBuySide());
        this.sellOrders = new PriorityBlockingQueue<>(INITIAL_CAPACITY, PriceTimePriority.forSellSide());
        this.orderIndex = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    /**
     * Add an order to the book.
     */
    public void addOrder(Order order) {
        if (!order.getInstrumentId().equals(instrumentId)) {
            throw new IllegalArgumentException("Order instrument doesn't match book");
        }

        lock.writeLock().lock();
        try {
            orderIndex.put(order.getOrderId(), order);

            if (order.getSide() == OrderSide.BUY) {
                buyOrders.offer(order);
            } else {
                sellOrders.offer(order);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove an order from the book.
     */
    public boolean removeOrder(String orderId) {
        lock.writeLock().lock();
        try {
            Order order = orderIndex.remove(orderId);
            if (order == null) {
                return false;
            }

            if (order.getSide() == OrderSide.BUY) {
                buyOrders.remove(order);
            } else {
                sellOrders.remove(order);
            }
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get the best (top) buy order without removing it.
     */
    public Optional<Order> peekBestBuy() {
        return Optional.ofNullable(buyOrders.peek());
    }

    /**
     * Get the best (top) sell order without removing it.
     */
    public Optional<Order> peekBestSell() {
        return Optional.ofNullable(sellOrders.peek());
    }

    /**
     * Remove and return the best buy order.
     */
    public Optional<Order> pollBestBuy() {
        lock.writeLock().lock();
        try {
            Order order = buyOrders.poll();
            if (order != null) {
                orderIndex.remove(order.getOrderId());
            }
            return Optional.ofNullable(order);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove and return the best sell order.
     */
    public Optional<Order> pollBestSell() {
        lock.writeLock().lock();
        try {
            Order order = sellOrders.poll();
            if (order != null) {
                orderIndex.remove(order.getOrderId());
            }
            return Optional.ofNullable(order);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get order by ID.
     */
    public Optional<Order> getOrder(String orderId) {
        return Optional.ofNullable(orderIndex.get(orderId));
    }

    /**
     * Update an order in the book (e.g., after partial fill).
     */
    public void updateOrder(Order updatedOrder) {
        lock.writeLock().lock();
        try {
            Order oldOrder = orderIndex.get(updatedOrder.getOrderId());
            if (oldOrder == null) {
                return;
            }

            // Remove old order from queue
            if (oldOrder.getSide() == OrderSide.BUY) {
                buyOrders.remove(oldOrder);
            } else {
                sellOrders.remove(oldOrder);
            }

            // Update index
            orderIndex.put(updatedOrder.getOrderId(), updatedOrder);

            // Re-add to queue if still active
            if (updatedOrder.getStatus().isActive()) {
                if (updatedOrder.getSide() == OrderSide.BUY) {
                    buyOrders.offer(updatedOrder);
                } else {
                    sellOrders.offer(updatedOrder);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get the current spread (difference between best buy and best sell).
     */
    public Optional<BigDecimal> getSpread() {
        lock.readLock().lock();
        try {
            Order bestBuy = buyOrders.peek();
            Order bestSell = sellOrders.peek();

            if (bestBuy == null || bestSell == null ||
                bestBuy.getPrice() == null || bestSell.getPrice() == null) {
                return Optional.empty();
            }

            return Optional.of(bestSell.getPrice().subtract(bestBuy.getPrice()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get order book depth for buy side.
     */
    public List<Order> getBuyDepth(int levels) {
        lock.readLock().lock();
        try {
            return buyOrders.stream()
                    .sorted(PriceTimePriority.forBuySide())
                    .limit(levels)
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get order book depth for sell side.
     */
    public List<Order> getSellDepth(int levels) {
        lock.readLock().lock();
        try {
            return sellOrders.stream()
                    .sorted(PriceTimePriority.forSellSide())
                    .limit(levels)
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get total buy order count.
     */
    public int getBuyOrderCount() {
        return buyOrders.size();
    }

    /**
     * Get total sell order count.
     */
    public int getSellOrderCount() {
        return sellOrders.size();
    }

    /**
     * Check if book has any matchable orders.
     */
    public boolean hasMatchableOrders() {
        lock.readLock().lock();
        try {
            Order bestBuy = buyOrders.peek();
            Order bestSell = sellOrders.peek();

            if (bestBuy == null || bestSell == null) {
                return false;
            }

            return PriceTimePriority.canMatch(bestBuy, bestSell);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clear all orders from the book.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            buyOrders.clear();
            sellOrders.clear();
            orderIndex.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String toString() {
        return String.format("OrderBook[%s, buys=%d, sells=%d]",
                instrumentId, buyOrders.size(), sellOrders.size());
    }
}
