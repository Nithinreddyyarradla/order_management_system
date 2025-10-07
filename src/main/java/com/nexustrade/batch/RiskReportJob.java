package com.nexustrade.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily Risk Report Job.
 * Generates comprehensive risk exposure report per currency and account.
 * Supports CSV and JSON output formats.
 */
@Component
public class RiskReportJob {

    private static final Logger log = LoggerFactory.getLogger(RiskReportJob.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${nexustrade.batch.risk-report.output-path:./reports/}")
    private String outputPath;

    public RiskReportJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Run daily risk report at 6 PM on weekdays.
     */
    @Scheduled(cron = "${nexustrade.batch.risk-report.cron}")
    public void generateDailyRiskReport() {
        log.info("Starting daily risk report generation");

        LocalDateTime reportTime = LocalDateTime.now();
        String reportDate = reportTime.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        try {
            // Gather all risk data
            RiskReportData reportData = collectRiskData();

            // Generate CSV report
            generateCsvReport(reportData, reportDate);

            // Generate JSON report
            generateJsonReport(reportData, reportDate);

            log.info("Risk report generated successfully for {}", reportDate);

        } catch (Exception e) {
            log.error("Failed to generate risk report", e);
        }
    }

    /**
     * Collect all risk data from database.
     */
    private RiskReportData collectRiskData() {
        RiskReportData data = new RiskReportData();
        data.reportDate = LocalDate.now();
        data.generatedAt = LocalDateTime.now();

        // Total exposure by currency
        data.exposureByCurrency = getCurrencyExposure();

        // Position summary per account
        data.accountPositions = getAccountPositions();

        // Trading volume summary
        data.tradingVolume = getTradingVolume();

        // Top positions by value
        data.topPositions = getTopPositions(10);

        // Account balance summary
        data.balanceSummary = getBalanceSummary();

        return data;
    }

    /**
     * Get total exposure grouped by currency.
     */
    private List<CurrencyExposure> getCurrencyExposure() {
        String sql = """
            SELECT a.currency,
                   SUM(a.cash_balance) as total_cash,
                   SUM(COALESCE(p.market_value, 0)) as total_position_value,
                   SUM(a.cash_balance) + SUM(COALESCE(p.market_value, 0)) as total_exposure,
                   COUNT(DISTINCT a.account_id) as account_count
            FROM ACCOUNTS a
            LEFT JOIN POSITIONS p ON a.account_id = p.account_id
            WHERE a.status = 'ACTIVE'
            GROUP BY a.currency
            ORDER BY total_exposure DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new CurrencyExposure(
                rs.getString("currency"),
                rs.getBigDecimal("total_cash"),
                rs.getBigDecimal("total_position_value"),
                rs.getBigDecimal("total_exposure"),
                rs.getInt("account_count")
        ));
    }

    /**
     * Get position summary per account.
     */
    private List<AccountPosition> getAccountPositions() {
        String sql = """
            SELECT a.account_id, a.account_number, a.account_name,
                   a.cash_balance, a.available_balance,
                   COALESCE(SUM(p.market_value), 0) as total_market_value,
                   COALESCE(SUM(p.unrealized_pnl), 0) as total_unrealized_pnl,
                   COALESCE(SUM(p.realized_pnl), 0) as total_realized_pnl,
                   COUNT(p.position_id) as position_count
            FROM ACCOUNTS a
            LEFT JOIN POSITIONS p ON a.account_id = p.account_id AND p.quantity != 0
            WHERE a.status = 'ACTIVE'
            GROUP BY a.account_id, a.account_number, a.account_name,
                     a.cash_balance, a.available_balance
            ORDER BY total_market_value DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new AccountPosition(
                rs.getString("account_id"),
                rs.getString("account_number"),
                rs.getString("account_name"),
                rs.getBigDecimal("cash_balance"),
                rs.getBigDecimal("available_balance"),
                rs.getBigDecimal("total_market_value"),
                rs.getBigDecimal("total_unrealized_pnl"),
                rs.getBigDecimal("total_realized_pnl"),
                rs.getInt("position_count")
        ));
    }

    /**
     * Get today's trading volume.
     */
    private TradingVolume getTradingVolume() {
        String sql = """
            SELECT COUNT(*) as trade_count,
                   COALESCE(SUM(total_value), 0) as total_value,
                   COALESCE(SUM(quantity), 0) as total_quantity
            FROM TRADES
            WHERE TRUNC(trade_date) = TRUNC(CURRENT_TIMESTAMP)
            """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new TradingVolume(
                rs.getInt("trade_count"),
                rs.getBigDecimal("total_value"),
                rs.getBigDecimal("total_quantity")
        ));
    }

    /**
     * Get top positions by market value.
     */
    private List<TopPosition> getTopPositions(int limit) {
        String sql = """
            SELECT p.account_id, a.account_number, i.symbol, i.name,
                   p.quantity, p.average_cost, p.market_value, p.unrealized_pnl
            FROM POSITIONS p
            JOIN ACCOUNTS a ON p.account_id = a.account_id
            JOIN INSTRUMENTS i ON p.instrument_id = i.instrument_id
            WHERE p.quantity != 0 AND p.market_value IS NOT NULL
            ORDER BY p.market_value DESC
            FETCH FIRST ? ROWS ONLY
            """;

        return jdbcTemplate.query(sql, new Object[]{limit}, (rs, rowNum) -> new TopPosition(
                rs.getString("account_number"),
                rs.getString("symbol"),
                rs.getString("name"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("average_cost"),
                rs.getBigDecimal("market_value"),
                rs.getBigDecimal("unrealized_pnl")
        ));
    }

    /**
     * Get overall balance summary.
     */
    private BalanceSummary getBalanceSummary() {
        String sql = """
            SELECT COUNT(*) as total_accounts,
                   SUM(cash_balance) as total_cash,
                   SUM(available_balance) as total_available,
                   AVG(cash_balance) as avg_balance
            FROM ACCOUNTS
            WHERE status = 'ACTIVE'
            """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new BalanceSummary(
                rs.getInt("total_accounts"),
                rs.getBigDecimal("total_cash"),
                rs.getBigDecimal("total_available"),
                rs.getBigDecimal("avg_balance")
        ));
    }

    /**
     * Generate CSV report.
     */
    private void generateCsvReport(RiskReportData data, String reportDate) throws IOException {
        Path reportDir = Paths.get(outputPath);
        Files.createDirectories(reportDir);

        Path csvPath = reportDir.resolve("risk_report_" + reportDate + ".csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(csvPath.toFile()))) {
            // Header
            writer.println("NEXUSTRADE DAILY RISK REPORT");
            writer.println("Report Date," + data.reportDate);
            writer.println("Generated At," + data.generatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            writer.println();

            // Currency Exposure Section
            writer.println("CURRENCY EXPOSURE");
            writer.println("Currency,Total Cash,Position Value,Total Exposure,Account Count");
            for (CurrencyExposure ce : data.exposureByCurrency) {
                writer.printf("%s,%.2f,%.2f,%.2f,%d%n",
                        ce.currency(), ce.totalCash(), ce.positionValue(),
                        ce.totalExposure(), ce.accountCount());
            }
            writer.println();

            // Account Positions Section
            writer.println("ACCOUNT POSITIONS");
            writer.println("Account Number,Account Name,Cash Balance,Market Value,Unrealized P&L,Position Count");
            for (AccountPosition ap : data.accountPositions) {
                writer.printf("%s,%s,%.2f,%.2f,%.2f,%d%n",
                        ap.accountNumber(), ap.accountName(), ap.cashBalance(),
                        ap.marketValue(), ap.unrealizedPnl(), ap.positionCount());
            }
            writer.println();

            // Trading Volume
            writer.println("TODAY'S TRADING VOLUME");
            writer.printf("Trade Count,%d%n", data.tradingVolume.tradeCount());
            writer.printf("Total Value,%.2f%n", data.tradingVolume.totalValue());
            writer.printf("Total Quantity,%.2f%n", data.tradingVolume.totalQuantity());
        }

        log.info("CSV report generated: {}", csvPath);
    }

    /**
     * Generate JSON report.
     */
    private void generateJsonReport(RiskReportData data, String reportDate) throws IOException {
        Path reportDir = Paths.get(outputPath);
        Files.createDirectories(reportDir);

        Path jsonPath = reportDir.resolve("risk_report_" + reportDate + ".json");

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"reportDate\": \"").append(data.reportDate).append("\",\n");
        json.append("  \"generatedAt\": \"").append(data.generatedAt).append("\",\n");

        // Currency exposure
        json.append("  \"currencyExposure\": [\n");
        for (int i = 0; i < data.exposureByCurrency.size(); i++) {
            CurrencyExposure ce = data.exposureByCurrency.get(i);
            json.append("    {\"currency\": \"").append(ce.currency())
                    .append("\", \"totalCash\": ").append(ce.totalCash())
                    .append(", \"positionValue\": ").append(ce.positionValue())
                    .append(", \"totalExposure\": ").append(ce.totalExposure())
                    .append(", \"accountCount\": ").append(ce.accountCount()).append("}");
            if (i < data.exposureByCurrency.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ],\n");

        // Balance summary
        json.append("  \"balanceSummary\": {\n");
        json.append("    \"totalAccounts\": ").append(data.balanceSummary.totalAccounts()).append(",\n");
        json.append("    \"totalCash\": ").append(data.balanceSummary.totalCash()).append(",\n");
        json.append("    \"avgBalance\": ").append(data.balanceSummary.avgBalance()).append("\n");
        json.append("  },\n");

        // Trading volume
        json.append("  \"tradingVolume\": {\n");
        json.append("    \"tradeCount\": ").append(data.tradingVolume.tradeCount()).append(",\n");
        json.append("    \"totalValue\": ").append(data.tradingVolume.totalValue()).append(",\n");
        json.append("    \"totalQuantity\": ").append(data.tradingVolume.totalQuantity()).append("\n");
        json.append("  }\n");

        json.append("}\n");

        Files.writeString(jsonPath, json.toString());
        log.info("JSON report generated: {}", jsonPath);
    }

    // Data classes
    static class RiskReportData {
        LocalDate reportDate;
        LocalDateTime generatedAt;
        List<CurrencyExposure> exposureByCurrency = new ArrayList<>();
        List<AccountPosition> accountPositions = new ArrayList<>();
        TradingVolume tradingVolume;
        List<TopPosition> topPositions = new ArrayList<>();
        BalanceSummary balanceSummary;
    }

    record CurrencyExposure(String currency, BigDecimal totalCash, BigDecimal positionValue,
                           BigDecimal totalExposure, int accountCount) {}

    record AccountPosition(String accountId, String accountNumber, String accountName,
                          BigDecimal cashBalance, BigDecimal availableBalance,
                          BigDecimal marketValue, BigDecimal unrealizedPnl,
                          BigDecimal realizedPnl, int positionCount) {}

    record TradingVolume(int tradeCount, BigDecimal totalValue, BigDecimal totalQuantity) {}

    record TopPosition(String accountNumber, String symbol, String name,
                      BigDecimal quantity, BigDecimal averageCost,
                      BigDecimal marketValue, BigDecimal unrealizedPnl) {}

    record BalanceSummary(int totalAccounts, BigDecimal totalCash,
                         BigDecimal totalAvailable, BigDecimal avgBalance) {}
}
