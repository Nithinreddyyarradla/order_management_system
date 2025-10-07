package com.nexustrade.kafka;

import com.nexustrade.model.Order;
import com.nexustrade.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for publishing orders and trades.
 */
@Component
public class OrderProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${nexustrade.kafka.topics.orders}")
    private String ordersTopic;

    @Value("${nexustrade.kafka.topics.trades}")
    private String tradesTopic;

    @Value("${nexustrade.kafka.topics.settlements}")
    private String settlementsTopic;

    public OrderProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish order for processing.
     * Key is instrument ID for partitioning (orders for same instrument go to same partition).
     */
    public CompletableFuture<SendResult<String, Object>> publishOrder(Order order) {
        log.debug("Publishing order to Kafka: {}", order.getOrderId());

        return kafkaTemplate.send(ordersTopic, order.getInstrumentId(), order)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish order: {}", order.getOrderId(), ex);
                    } else {
                        log.debug("Order published successfully: {} to partition {}",
                                order.getOrderId(),
                                result.getRecordMetadata().partition());
                    }
                });
    }

    /**
     * Publish trade for settlement.
     */
    public CompletableFuture<SendResult<String, Object>> publishTrade(Trade trade) {
        log.debug("Publishing trade to Kafka: {}", trade.getTradeId());

        return kafkaTemplate.send(tradesTopic, trade.getInstrumentId(), trade)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish trade: {}", trade.getTradeId(), ex);
                    } else {
                        log.debug("Trade published successfully: {}", trade.getTradeId());
                    }
                });
    }

    /**
     * Publish settlement request.
     */
    public CompletableFuture<SendResult<String, Object>> publishSettlement(Trade trade) {
        log.debug("Publishing settlement request: {}", trade.getTradeId());

        return kafkaTemplate.send(settlementsTopic, trade.getTradeId(), trade)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish settlement: {}", trade.getTradeId(), ex);
                    } else {
                        log.debug("Settlement published successfully: {}", trade.getTradeId());
                    }
                });
    }
}
