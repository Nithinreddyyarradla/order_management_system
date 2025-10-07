package com.nexustrade.service;

import com.nexustrade.engine.MatchingEngine;
import com.nexustrade.model.Order;
import com.nexustrade.model.enums.OrderStatus;
import com.nexustrade.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service for order lifecycle management.
 * Handles order submission, cancellation, and queries.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ValidationService validationService;
    private final MatchingEngine matchingEngine;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${nexustrade.kafka.topics.orders}")
    private String ordersTopic;

    public OrderService(OrderRepository orderRepository,
                       ValidationService validationService,
                       MatchingEngine matchingEngine,
                       KafkaTemplate<String, Object> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.validationService = validationService;
        this.matchingEngine = matchingEngine;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Submit a new order.
     */
    @Transactional
    public Order submitOrder(Order order) {
        log.info("Submitting order: {}", order);

        // Validate order
        List<String> errors = validationService.validateOrder(order);
        if (!errors.isEmpty()) {
            log.warn("Order validation failed: {}", errors);
            return order.withStatus(OrderStatus.REJECTED);
        }

        // Save order to database
        Order savedOrder = orderRepository.save(order);

        // Publish to Kafka for async processing
        publishOrderToKafka(savedOrder);

        log.info("Order submitted successfully: {}", savedOrder.getOrderId());
        return savedOrder;
    }

    /**
     * Submit order and wait for matching result.
     */
    public CompletableFuture<Order> submitOrderAsync(Order order) {
        log.info("Submitting order async: {}", order);

        // Validate order
        List<String> errors = validationService.validateOrder(order);
        if (!errors.isEmpty()) {
            log.warn("Order validation failed: {}", errors);
            return CompletableFuture.completedFuture(order.withStatus(OrderStatus.REJECTED));
        }

        // Save and submit to matching engine
        Order savedOrder = orderRepository.save(order);
        return matchingEngine.submitOrder(savedOrder)
                .thenApply(matchedOrder -> {
                    orderRepository.save(matchedOrder);
                    return matchedOrder;
                });
    }

    /**
     * Cancel an existing order.
     */
    @Transactional
    public boolean cancelOrder(String orderId) {
        log.info("Cancelling order: {}", orderId);

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            log.warn("Order not found: {}", orderId);
            return false;
        }

        Order order = orderOpt.get();
        if (!order.getStatus().isActive()) {
            log.warn("Cannot cancel order in status: {}", order.getStatus());
            return false;
        }

        // Cancel in matching engine
        boolean cancelled = matchingEngine.cancelOrder(order.getInstrumentId(), orderId);

        // Update order status
        Order cancelledOrder = order.withStatus(OrderStatus.CANCELLED);
        orderRepository.save(cancelledOrder);

        log.info("Order cancelled: {}", orderId);
        return true;
    }

    /**
     * Get order by ID.
     */
    public Optional<Order> getOrder(String orderId) {
        return orderRepository.findById(orderId);
    }

    /**
     * Get all orders for an account.
     */
    public List<Order> getOrdersByAccount(String accountId) {
        return orderRepository.findByAccountId(accountId);
    }

    /**
     * Get active orders for an account.
     */
    public List<Order> getActiveOrdersByAccount(String accountId) {
        return orderRepository.findByStatusIn(
                List.of(OrderStatus.PENDING, OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED)
        );
    }

    /**
     * Get orders by status.
     */
    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    /**
     * Update order status.
     */
    @Transactional
    public Order updateOrderStatus(String orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        Order updatedOrder = order.withStatus(newStatus);
        return orderRepository.save(updatedOrder);
    }

    /**
     * Publish order to Kafka for async processing.
     */
    private void publishOrderToKafka(Order order) {
        try {
            kafkaTemplate.send(ordersTopic, order.getInstrumentId(), order)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish order to Kafka: {}", order.getOrderId(), ex);
                        } else {
                            log.debug("Order published to Kafka: {}", order.getOrderId());
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing order to Kafka", e);
        }
    }
}
