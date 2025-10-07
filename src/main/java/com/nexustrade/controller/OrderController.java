package com.nexustrade.controller;

import com.nexustrade.model.Order;
import com.nexustrade.model.enums.OrderSide;
import com.nexustrade.model.enums.OrderStatus;
import com.nexustrade.model.enums.OrderType;
import com.nexustrade.service.OrderService;
import com.nexustrade.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST controller for order management.
 */
@RestController
@RequestMapping("/v1/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final ValidationService validationService;

    public OrderController(OrderService orderService, ValidationService validationService) {
        this.orderService = orderService;
        this.validationService = validationService;
    }

    /**
     * Submit a new order.
     */
    @PostMapping
    public ResponseEntity<?> submitOrder(@RequestBody OrderRequest request) {
        log.info("Received order request: {}", request);

        try {
            Order order = Order.builder()
                    .accountId(request.accountId())
                    .instrumentId(request.instrumentId())
                    .side(OrderSide.valueOf(request.side().toUpperCase()))
                    .type(OrderType.valueOf(request.type().toUpperCase()))
                    .quantity(request.quantity())
                    .price(request.price())
                    .stopPrice(request.stopPrice())
                    .clientOrderId(request.clientOrderId())
                    .build();

            // Validate first
            List<String> errors = validationService.validateOrder(order);
            if (!errors.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "REJECTED",
                        "errors", errors
                ));
            }

            // Submit order
            Order submittedOrder = orderService.submitOrder(order);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "orderId", submittedOrder.getOrderId(),
                    "status", submittedOrder.getStatus().name(),
                    "message", "Order submitted successfully"
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error submitting order", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "message", "Internal server error"
            ));
        }
    }

    /**
     * Get order by ID.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        return orderService.getOrder(orderId)
                .map(order -> ResponseEntity.ok(toOrderResponse(order)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get orders for an account.
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(
            @RequestParam String accountId,
            @RequestParam(required = false) String status) {

        List<Order> orders;
        if (status != null) {
            orders = orderService.getOrdersByStatus(OrderStatus.valueOf(status.toUpperCase()));
        } else {
            orders = orderService.getOrdersByAccount(accountId);
        }

        List<OrderResponse> responses = orders.stream()
                .map(this::toOrderResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Cancel an order.
     */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<?> cancelOrder(@PathVariable String orderId) {
        log.info("Cancelling order: {}", orderId);

        boolean cancelled = orderService.cancelOrder(orderId);

        if (cancelled) {
            return ResponseEntity.ok(Map.of(
                    "orderId", orderId,
                    "status", "CANCELLED",
                    "message", "Order cancelled successfully"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Unable to cancel order. Order may not exist or is not in a cancellable state."
            ));
        }
    }

    private OrderResponse toOrderResponse(Order order) {
        return new OrderResponse(
                order.getOrderId(),
                order.getAccountId(),
                order.getInstrumentId(),
                order.getSide().name(),
                order.getType().name(),
                order.getQuantity(),
                order.getFilledQuantity(),
                order.getPrice(),
                order.getStopPrice(),
                order.getStatus().name(),
                order.getCreatedAt().toString(),
                order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null,
                order.getClientOrderId()
        );
    }

    // Request/Response records
    public record OrderRequest(
            String accountId,
            String instrumentId,
            String side,
            String type,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal stopPrice,
            String clientOrderId
    ) {}

    public record OrderResponse(
            String orderId,
            String accountId,
            String instrumentId,
            String side,
            String type,
            BigDecimal quantity,
            BigDecimal filledQuantity,
            BigDecimal price,
            BigDecimal stopPrice,
            String status,
            String createdAt,
            String updatedAt,
            String clientOrderId
    ) {}
}
