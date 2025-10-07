package com.nexustrade.batch;

import com.nexustrade.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;

/**
 * Spring Batch job for archiving settled trades.
 * Runs every 5 minutes to move FILLED and settled orders to TRADE_HISTORY.
 */
@Configuration
public class TradeArchiveJob {

    private static final Logger log = LoggerFactory.getLogger(TradeArchiveJob.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Value("${nexustrade.batch.trade-archive.chunk-size:1000}")
    private int chunkSize;

    public TradeArchiveJob(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Bean
    public Job tradeArchiveJobBean(JobRepository jobRepository, Step archiveStep) {
        return new JobBuilder("tradeArchiveJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(archiveStep)
                .build();
    }

    @Bean
    public Step archiveStep(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager,
                           ItemReader<TradeRecord> settledTradeReader,
                           ItemProcessor<TradeRecord, TradeRecord> tradeProcessor,
                           ItemWriter<TradeRecord> tradeArchiveWriter) {
        return new StepBuilder("archiveStep", jobRepository)
                .<TradeRecord, TradeRecord>chunk(chunkSize, transactionManager)
                .reader(settledTradeReader)
                .processor(tradeProcessor)
                .writer(tradeArchiveWriter)
                .build();
    }

    @Bean
    public JdbcCursorItemReader<TradeRecord> settledTradeReader() {
        return new JdbcCursorItemReaderBuilder<TradeRecord>()
                .name("settledTradeReader")
                .dataSource(dataSource)
                .sql("""
                    SELECT trade_id, buy_order_id, sell_order_id, buyer_account_id,
                           seller_account_id, instrument_id, quantity, price, total_value,
                           trade_date, settlement_date
                    FROM TRADES
                    WHERE is_settled = 1
                    AND settlement_date < CURRENT_TIMESTAMP - INTERVAL '1' HOUR
                    ORDER BY trade_date
                    """)
                .rowMapper((ResultSet rs, int rowNum) -> new TradeRecord(
                        rs.getString("trade_id"),
                        rs.getString("buy_order_id"),
                        rs.getString("sell_order_id"),
                        rs.getString("buyer_account_id"),
                        rs.getString("seller_account_id"),
                        rs.getString("instrument_id"),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("price"),
                        rs.getBigDecimal("total_value"),
                        rs.getTimestamp("trade_date").toInstant(),
                        rs.getTimestamp("settlement_date") != null ?
                                rs.getTimestamp("settlement_date").toInstant() : null
                ))
                .build();
    }

    @Bean
    public ItemProcessor<TradeRecord, TradeRecord> tradeProcessor() {
        return trade -> {
            log.debug("Processing trade for archive: {}", trade.tradeId());
            return trade;
        };
    }

    @Bean
    public ItemWriter<TradeRecord> tradeArchiveWriter() {
        return trades -> {
            for (TradeRecord trade : trades) {
                // Insert into TRADE_HISTORY
                jdbcTemplate.update("""
                    INSERT INTO TRADE_HISTORY
                    (trade_id, buy_order_id, sell_order_id, buyer_account_id, seller_account_id,
                     instrument_id, quantity, price, total_value, trade_date, settlement_date, archived_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                        trade.tradeId(),
                        trade.buyOrderId(),
                        trade.sellOrderId(),
                        trade.buyerAccountId(),
                        trade.sellerAccountId(),
                        trade.instrumentId(),
                        trade.quantity(),
                        trade.price(),
                        trade.totalValue(),
                        java.sql.Timestamp.from(trade.tradeDate()),
                        trade.settlementDate() != null ?
                                java.sql.Timestamp.from(trade.settlementDate()) : null
                );

                // Delete from TRADES
                jdbcTemplate.update("DELETE FROM TRADES WHERE trade_id = ?", trade.tradeId());

                log.debug("Archived trade: {}", trade.tradeId());
            }
            log.info("Archived {} trades", trades.size());
        };
    }

    /**
     * Scheduled execution every 5 minutes.
     */
    @Scheduled(cron = "${nexustrade.batch.trade-archive.cron}")
    public void runArchiveJob() {
        log.info("Starting scheduled trade archive job");

        // Get count of trades to archive
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TRADES WHERE is_settled = 1 AND settlement_date < CURRENT_TIMESTAMP - INTERVAL '1' HOUR",
                Integer.class
        );

        if (count != null && count > 0) {
            log.info("Found {} trades to archive", count);
            // Note: In production, would launch the job via JobLauncher
        } else {
            log.debug("No trades to archive");
        }
    }

    // Record for trade data
    public record TradeRecord(
            String tradeId,
            String buyOrderId,
            String sellOrderId,
            String buyerAccountId,
            String sellerAccountId,
            String instrumentId,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal totalValue,
            Instant tradeDate,
            Instant settlementDate
    ) {}
}
