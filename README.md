# NexusTrade - High-Throughput Order Management & Settlement System

A comprehensive order management system demonstrating enterprise Java patterns for high-frequency trading environments. Built with Spring Boot, Oracle, Apache Kafka, and concurrent processing.

## Features

- **Real-time Order Matching Engine** - Price-time priority algorithm with concurrent order processing
- **Immutable Domain Models** - Thread-safe design for high-frequency trading scenarios
- **ACID-Compliant Settlement** - Serializable transaction isolation for trade settlement
- **Async Order Processing** - Apache Kafka integration for scalable order flow
- **Batch Processing** - Spring Batch jobs for trade archival, reconciliation, and risk reporting
- **RESTful API** - Complete API for order submission, position tracking, and account management

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.4.1 |
| Database | Oracle XE / H2 (testing) |
| Message Queue | Apache Kafka |
| Batch Processing | Spring Batch |
| Build Tool | Maven |
| Testing | JUnit 5, Mockito |

## Project Structure

```
nexustrade/
├── src/main/java/com/nexustrade/
│   ├── model/           # Immutable domain models
│   │   ├── Order.java
│   │   ├── Trade.java
│   │   ├── Account.java
│   │   ├── Position.java
│   │   └── Instrument.java
│   ├── engine/          # Core matching engine
│   │   ├── MatchingEngine.java
│   │   ├── OrderBook.java
│   │   └── PriceTimePriority.java
│   ├── controller/      # REST API endpoints
│   ├── service/         # Business logic layer
│   ├── repository/      # Data access layer
│   ├── kafka/           # Kafka producers/consumers
│   └── batch/           # Spring Batch jobs
├── src/main/resources/
│   ├── application.yml
│   ├── schema.sql
│   └── procedures/      # PL/SQL stored procedures
└── src/test/            # Unit & integration tests
```

## Core Components

### Matching Engine

The matching engine implements price-time priority ordering:
- **Buy Orders**: Highest price first, earliest timestamp breaks ties
- **Sell Orders**: Lowest price first, earliest timestamp breaks ties

```java
// Concurrent order processing with ExecutorService
ExecutorService executor = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors()
);
```

### Immutable Order Model

Orders are immutable for thread-safety in concurrent environments:

```java
public final class Order {
    private final String orderId;
    private final String accountId;
    private final OrderSide side;
    private final OrderType type;
    private final BigDecimal quantity;
    private final BigDecimal price;
    // No setters - state changes return new instances
}
```

### ACID-Compliant Settlement

Trade settlement uses serializable isolation to ensure data integrity:

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void settleTrade(Trade trade) {
    // Atomic: debit buyer, credit seller, update positions
}
```

## API Endpoints

### Orders
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/orders` | Submit new order |
| GET | `/api/v1/orders/{orderId}` | Get order by ID |
| GET | `/api/v1/orders/account/{accountId}` | Get orders by account |
| DELETE | `/api/v1/orders/{orderId}` | Cancel order |

### Positions
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/positions/{accountId}` | Get positions for account |
| GET | `/api/v1/positions/{accountId}/summary` | Get portfolio summary |

### Accounts
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/accounts` | Create account |
| GET | `/api/v1/accounts/{accountId}` | Get account details |
| POST | `/api/v1/accounts/{accountId}/deposit` | Deposit funds |
| POST | `/api/v1/accounts/{accountId}/withdraw` | Withdraw funds |

## Building & Running

### Prerequisites

- Java 21+
- Maven 3.8+
- Oracle XE (for production) or H2 (for testing)
- Apache Kafka

### Build

```bash
mvn clean install
```

### Run Tests

```bash
mvn test
```

### Run Application

```bash
mvn spring-boot:run
```

## Configuration

Configure the application in `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:XE
    username: nexustrade
    password: ${DB_PASSWORD}

  kafka:
    bootstrap-servers: localhost:9092

nexustrade:
  matching-engine:
    thread-pool-size: 8
    order-queue-capacity: 100000
```

## Batch Jobs

| Job | Schedule | Description |
|-----|----------|-------------|
| Trade Archive | Every 5 min | Archives filled trades to history table |
| Reconciliation | EOD | Reconciles account balances |
| Risk Report | Daily | Generates risk exposure reports |

## Testing

The project includes comprehensive tests:

- **Unit Tests**: MatchingEngine, OrderService, ValidationService
- **Integration Tests**: Full order flow with embedded Kafka and H2

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=MatchingEngineTest
```

## License

MIT License
