package com.nexustrade.kafka;

import com.nexustrade.engine.MatchingEngine;
import com.nexustrade.model.Order;
import com.nexustrade.model.Trade;
import com.nexustrade.repository.OrderRepository;
import com.nexustrade.service.SettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for processing orders and settlements.
 */
@Component
public class OrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);

    private final MatchingEngine matchingEngine;
    private final OrderRepository orderRepository;
    private final SettlementService settlementService;
    private final OrderProducer orderProducer;

    public OrderConsumer(MatchingEngine matchingEngine,
                        OrderRepository orderRepository,
                        SettlementService settlementService,
                        OrderProducer orderProducer) {
        this.matchingEngine = matchingEngine;
        this.orderRepository = orderRepository;
        this.settlementService = settlementService;
        this.orderProducer = orderProducer;
    }

    /**
     * Consume orders from Kafka and submit to matching engine.
     */
    @KafkaListener(
            topics = "${nexustrade.kafka.topics.orders}",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    public void consumeOrder(@Payload Order order,
                            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                            @Header(KafkaHeaders.OFFSET) long offset,
                            Acknowledgment acknowledgment) {
        log.info("Received order from Kafka: {} [partition={}, offset={}]",
                order.getOrderId(), partition, offset);

        try {
            // Submit to matching engine
            matchingEngine.submitOrder(order)
                    .thenAccept(processedOrder -> {
                        // Save updated order
                        orderRepository.save(processedOrder);
                        log.debug("Order processed: {} -> {}", order.getOrderId(), processedOrder.getStatus());
                    })
                    .exceptionally(ex -> {
                        log.error("Error processing order: {}", order.getOrderId(), ex);
                        return null;
                    });

            // Acknowledge the message
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error consuming order: {}", order.getOrderId(), e);
            // Don't acknowledge - message will be redelivered
        }
    }

    /**
     * Consume trades for settlement processing.
     */
    @KafkaListener(
            topics = "${nexustrade.kafka.topics.settlements}",
            containerFactory = "tradeKafkaListenerContainerFactory"
    )
    public void consumeSettlement(@Payload Trade trade,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset,
                                  Acknowledgment acknowledgment) {
        log.info("Received settlement request from Kafka: {} [partition={}, offset={}]",
                trade.getTradeId(), partition, offset);

        try {
            // Process settlement
            settlementService.settleTrade(trade);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error settling trade: {}", trade.getTradeId(), e);
            // Don't acknowledge - will retry
        }
    }

    /**
     * Initialize trade listener on matching engine.
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        // When matching engine creates a trade, publish for settlement
        matchingEngine.addTradeListener(trade -> {
            log.info("Trade created, publishing for settlement: {}", trade.getTradeId());
            orderProducer.publishSettlement(trade);
        });

        // Log order updates
        matchingEngine.addOrderUpdateListener(order -> {
            log.debug("Order updated: {} -> {}", order.getOrderId(), order.getStatus());
        });
    }
}
