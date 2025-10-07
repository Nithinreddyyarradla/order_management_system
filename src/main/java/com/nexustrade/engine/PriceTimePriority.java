package com.nexustrade.engine;

import com.nexustrade.model.Order;
import com.nexustrade.model.enums.OrderSide;

import java.util.Comparator;

/**
 * Price-Time Priority comparator for order matching.
 *
 * For BUY orders: Higher price has priority (willing to pay more)
 * For SELL orders: Lower price has priority (willing to accept less)
 *
 * When prices are equal, earlier timestamp has priority (FIFO).
 * This is the standard matching algorithm used by most exchanges.
 */
public final class PriceTimePriority implements Comparator<Order> {

    private final OrderSide side;

    private PriceTimePriority(OrderSide side) {
        this.side = side;
    }

    /**
     * Create comparator for buy orders (highest price first).
     */
    public static PriceTimePriority forBuySide() {
        return new PriceTimePriority(OrderSide.BUY);
    }

    /**
     * Create comparator for sell orders (lowest price first).
     */
    public static PriceTimePriority forSellSide() {
        return new PriceTimePriority(OrderSide.SELL);
    }

    @Override
    public int compare(Order o1, Order o2) {
        // First compare by price
        int priceComparison;

        if (o1.getPrice() == null && o2.getPrice() == null) {
            priceComparison = 0;
        } else if (o1.getPrice() == null) {
            // Market orders (null price) have highest priority
            return -1;
        } else if (o2.getPrice() == null) {
            return 1;
        } else {
            priceComparison = o1.getPrice().compareTo(o2.getPrice());
        }

        // For BUY side: higher price comes first (reverse order)
        // For SELL side: lower price comes first (natural order)
        if (side == OrderSide.BUY) {
            priceComparison = -priceComparison; // Reverse for buy orders
        }

        // If prices are equal, use time priority (earlier order first)
        if (priceComparison == 0) {
            return o1.getCreatedAt().compareTo(o2.getCreatedAt());
        }

        return priceComparison;
    }

    /**
     * Check if two orders can potentially match based on price.
     * A buy order matches with a sell if buy price >= sell price.
     */
    public static boolean canMatch(Order buyOrder, Order sellOrder) {
        if (buyOrder.getSide() != OrderSide.BUY || sellOrder.getSide() != OrderSide.SELL) {
            throw new IllegalArgumentException("Must provide buy and sell orders");
        }

        // Market orders can always match
        if (buyOrder.getPrice() == null || sellOrder.getPrice() == null) {
            return true;
        }

        // Buy price must be >= sell price for a match
        return buyOrder.getPrice().compareTo(sellOrder.getPrice()) >= 0;
    }
}
