# Distributed Bulk Email Sender

A scalable, fault-tolerant distributed bulk email delivery system built with Java, Spring Boot, Apache Kafka, and PostgreSQL. Designed as a final-year Distributed Systems assignment implementing concepts found in production systems like SendGrid and Mailgun.

---

## Assignment Requirements Checklist

| Requirement | Implementation |
|---|---|
| Distributed email processing | Kafka topics with multiple consumer partitions |
| Email status tracking (sent, delivered, bounced, failed) | `EmailStatus` enum + `email_status_logs` table |
| Distributed system for bulk email | Kafka-based producer/consumer architecture |
| Automatic failover between providers | `ProviderSelectionService` (Mailgun → SendGrid) |
| Retry with exponential backoff | `RetryService` + `email.retry` Kafka topic |
| Rate limiting / throttling | `RateLimiterService` using Bucket4j token buckets |
| Email templates with dynamic placeholders | `EmailTemplate` entity + `{{name}}`, `{{studentNumber}}`, `{{course}}` |
| Priority messaging | HIGH/NORMAL topics with different concurrency levels |
| Database storage | PostgreSQL with 4 tables + audit logs |

---

## Architecture

```
Client (JavaFX / REST)
        │
        ▼
┌─────────────────────┐
│  email-api-service  │  (REST API - port 8081)
│  Spring Boot        │
│  ● Validate request │
│  ● Store in DB      │
│  ● Publish to Kafka │
└─────────┬───────────┘
          │  Kafka Topics
          │  ● email.high-priority
          │  ● email.normal-priority
          ▼
┌───────────────────────┐
│  email-worker-service │  (Kafka Consumer - port 8082)
│  Spring Boot          │
│  ● Send via Mailgun   │
│    ↓ (on failure)     │
│  ● Send via SendGrid  │
│  ● Retry + backoff    │
│  ● Update DB status   │
│  ● Dead-letter topic  │
└───────────────────────┘
          │
          ▼
    ┌───────────┐
    │ PostgreSQL│
    │ ● campaigns
    │ ● tasks   │
    │ ● logs    │
    │ ● templates
    └───────────┘
```

### Kafka Topic Design

| Topic | Purpose | Consumers |
|---|---|---|
| `email.high-priority` | HIGH priority email tasks | 5 concurrent threads |
| `email.normal-priority` | NORMAL priority email tasks | 2 concurrent threads |
| `email.retry` | Failed tasks with retries remaining | 1 thread (sequential) |
| `email.status` | Status update events | Monitoring consumers |
| `email.dead-letter` | Tasks that exhausted all retries | Manual review |

---

## Project Structure

```
distributed-email-sender/
├── email-common/                          # Shared library
│   └── src/main/java/com/distributedemail/common/
│       ├── entity/                        # JPA entities (EmailCampaign, EmailTask, etc.)
│       ├── enums/                         # EmailStatus, EmailPriority, ProviderName
│       ├── kafka/                         # EmailTaskMessage, KafkaTopics
│       ├── provider/                      # EmailProvider interface
│       └── dto/                           # BulkEmailRequestDto, CampaignResponseDto
│
├── email-api-service/                     # REST API service
│   └── src/main/java/com/distributedemail/api/
│       ├── controller/                    # CampaignController, TemplateController, DashboardController
│       ├── service/                       # CampaignService, TemplateService
│       ├── repository/                    # Spring Data JPA repositories
│       ├── kafka/                         # EmailKafkaProducer
│       └── config/                        # KafkaProducerConfig
│
├── email-worker-service/                  # Kafka consumer / email sender
│   └── src/main/java/com/distributedemail/worker/
│       ├── consumer/                      # EmailConsumer (high/normal/retry)
│       ├── service/                       # EmailSendingService, ProviderSelectionService
│       │                                  # RateLimiterService, RetryService
│       ├── provider/                      # MailgunProvider, SendGridProvider
│       ├── repository/                    # Worker-side JPA repositories
│       └── config/                        # KafkaConsumerConfig, WorkerKafkaProducerConfig
│
├── email-client-javafx/                   # Desktop GUI (JavaFX + FXML)
│   └── src/main/
│       ├── java/com/distributedemail/client/
│       │   ├── MainApp.java               # Application entry point
│       │   ├── controller/                # DashboardController, ComposeBulkEmailController
│       │   │                              # TemplateManagerController, ReportsController
│       │   │                              # FailedEmailsController, MainLayoutController
│       │   └── service/                   # ApiService (HTTP client)
│       └── resources/
│           ├── fxml/                      # MainLayout.fxml, Dashboard.fxml,
│           │                              # ComposeBulkEmail.fxml, TemplateManager.fxml
│           │                              # Reports.fxml, FailedEmails.fxml
│           └── css/main.css               # Styling
│
├── database/
│   ├── schema.sql                         # PostgreSQL table definitions
│   └── seed.sql                           # Sample data
│
├── docker-compose.yml                     # All services + Zookeeper + Kafka + PostgreSQL
└── README.md
```

---

## Option 1: Run with Docker Compose (Recommended)

### Prerequisites
- Docker and Docker Compose installed
- Java 17+ (only needed for the JavaFX client)
- Maven (only needed to build from source)

### Step 1: Set Up Credentials

Create a `.env` file in the `distributed-email-sender/` folder:

```bash
# .env - DO NOT commit this file to Git
MAILGUN_API_KEY=your-real-mailgun-api-key
MAILGUN_DOMAIN=mail.yourdomain.com
SENDGRID_API_KEY=your-real-sendgrid-api-key
```

### Step 2: Build and Start

```bash
cd distributed-email-sender

# Build the Java services (requires Maven and Java 17)
cd email-common && mvn install -DskipTests && cd ..
cd email-api-service && mvn package -DskipTests && cd ..
cd email-worker-service && mvn package -DskipTests && cd ..

# Start everything with Docker Compose
docker-compose up -d

# Check all services are running
docker-compose ps
```

### Step 3: Verify

```bash
# API service health
curl http://localhost:8081/actuator/health

# Worker service health  
curl http://localhost:8082/actuator/health

# Get dashboard stats
curl http://localhost:8081/api/dashboard/stats
```

---

## Option 2: Run Locally (Without Docker for Services)

### Prerequisites

1. **Java 17+** — Download from https://adoptium.net/
2. **Maven 3.8+** — https://maven.apache.org/download.cgi
3. **PostgreSQL 14+** — https://www.postgresql.org/download/
4. **Apache Kafka 3.5+** — https://kafka.apache.org/downloads

### Step 1: Set Up PostgreSQL

```sql
-- Run as postgres superuser
CREATE DATABASE emaildb;
CREATE USER emailuser WITH PASSWORD 'emailpass';
GRANT ALL PRIVILEGES ON DATABASE emaildb TO emailuser;
\c emaildb
```

```bash
psql -U emailuser -d emaildb -f database/schema.sql
psql -U emailuser -d emaildb -f database/seed.sql
```

### Step 2: Start Kafka

```bash
# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties

# Start Kafka (in a new terminal)
bin/kafka-server-start.sh config/server.properties
```

### Step 3: Configure Environment Variables

```bash
export DB_URL=jdbc:postgresql://localhost:5432/emaildb
export DB_USERNAME=emailuser
export DB_PASSWORD=emailpass
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export MAILGUN_API_KEY=your-real-mailgun-api-key
export MAILGUN_DOMAIN=mail.yourdomain.com
export SENDGRID_API_KEY=your-real-sendgrid-api-key
```

### Step 4: Build and Run

```bash
# Build shared module first
cd email-common && mvn install -DskipTests

# Terminal 1: Start API service
cd email-api-service
mvn spring-boot:run

# Terminal 2: Start Worker service
cd email-worker-service
mvn spring-boot:run

# Terminal 3: Start JavaFX client
cd email-client-javafx
mvn javafx:run
```

---

## Running the JavaFX Desktop Client

> **Note:** JavaFX requires a local desktop environment. This cannot run on Replit.

### Prerequisites for JavaFX
- Java 17+ with JavaFX modules (or OpenJFX)
- `email-api-service` must be running on port 8081

### Run

```bash
cd email-client-javafx
mvn javafx:run
```

Or with a custom API URL:

```bash
mvn javafx:run -Djavafx.args="-Dapi.url=http://your-server:8081"
```

### GUI Views

| View | Description |
|---|---|
| Dashboard | Live stats: sent, delivered, failed, dead-lettered |
| Compose Bulk Email | Create campaigns with recipients and priority |
| Template Manager | Create/edit templates with `{{placeholder}}` support |
| Reports / Logs | Campaign and task-level delivery logs |
| Failed Emails | View and retry failed/dead-lettered tasks |

---

## Sample API Requests

### Create a Bulk Email Campaign

```bash
curl -X POST http://localhost:8081/api/campaigns \
  -H "Content-Type: application/json" \
  -d '{
    "campaignName": "Assignment Due Reminder - CS401",
    "subject": "Reminder: Your {{course}} assignment is due!",
    "priority": "HIGH",
    "senderEmail": "admin@university.edu",
    "senderName": "CS Department",
    "rawBody": "Hello {{name}},\n\nYour assignment for {{course}} is due in 48 hours.\nStudent Number: {{studentNumber}}\n\nGood luck!",
    "recipients": [
      {
        "email": "alice@student.edu",
        "name": "Alice Smith",
        "templateVariables": {
          "studentNumber": "ST001",
          "course": "CS401 Distributed Systems"
        }
      },
      {
        "email": "bob@student.edu",
        "name": "Bob Jones",
        "templateVariables": {
          "studentNumber": "ST002",
          "course": "CS401 Distributed Systems"
        }
      }
    ]
  }'
```

### Get Dashboard Stats

```bash
curl http://localhost:8081/api/dashboard/stats
```

### Get All Templates

```bash
curl http://localhost:8081/api/templates
```

### Create a Template

```bash
curl -X POST http://localhost:8081/api/templates \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Welcome Email",
    "subjectTemplate": "Welcome to {{course}}, {{name}}!",
    "bodyTemplate": "<h1>Welcome {{name}}</h1><p>Student number: {{studentNumber}}</p>",
    "description": "Sent to new students"
  }'
```

### Preview Template with Variables

```bash
curl -X POST http://localhost:8081/api/templates/1/preview \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice", "studentNumber": "ST001", "course": "CS401"}'
```

---

## Exponential Backoff Details

Retry delay formula: `delay = min(baseDelay × 2^retryCount, maxDelay)`

| Attempt | Formula | Delay |
|---|---|---|
| 1 (first retry) | 1000 × 2⁰ = 1000ms | ~1 second |
| 2 | 1000 × 2¹ = 2000ms | ~2 seconds |
| 3 | 1000 × 2² = 4000ms | ~4 seconds |
| 4 | 1000 × 2³ = 8000ms | ~8 seconds |
| Max | capped at 32000ms | 32 seconds |

After max retries (default: 3), the task is moved to `email.dead-letter`.

---

## Provider Failover

```
Worker receives email task
         │
         ▼
Try MAILGUN (primary)
         │
  Success? ──YES──► Mark SENT, log success
         │
        NO
         │
Try SENDGRID (failover)
         │
  Success? ──YES──► Mark SENT, log success
         │
        NO
         │
Retries remaining? ──YES──► Publish to email.retry (with incremented retry count)
         │
        NO
         │
Publish to email.dead-letter, mark DEAD_LETTERED
```

---

## Rate Limiting

| Provider | Default Limit | Configurable Via |
|---|---|---|
| Mailgun | 5 emails/second | `MAILGUN_RATE_LIMIT` env var |
| SendGrid | 10 emails/second | `SENDGRID_RATE_LIMIT` env var |

Uses Bucket4j's token bucket algorithm. Threads block when the limit is reached and resume when a token becomes available.

---

## Getting Mailgun and SendGrid API Keys

### Mailgun
1. Sign up at https://www.mailgun.com/
2. Add and verify a domain
3. Go to API Keys → Create API Key
4. Note your domain (e.g., `mail.yourdomain.com`)

### SendGrid
1. Sign up at https://sendgrid.com/
2. Go to Settings → API Keys → Create API Key
3. Grant "Mail Send" permissions

---

## Migrating from Replit to GitHub/Laptop

1. Push the project to GitHub:
   ```bash
   git init
   git add .
   git commit -m "Initial commit: Distributed Bulk Email Sender"
   git remote add origin https://github.com/yourusername/distributed-email-sender.git
   git push -u origin main
   ```

2. Clone on your laptop:
   ```bash
   git clone https://github.com/yourusername/distributed-email-sender.git
   cd distributed-email-sender
   ```

3. Set up your `.env` file with real credentials

4. Run with Docker Compose as described above

---

## Distributed Systems Design Decisions

### 1. Outbox Pattern
Campaigns and tasks are persisted to PostgreSQL **before** publishing to Kafka. This ensures no tasks are lost if Kafka is temporarily unavailable — a recovery job could re-publish all `PENDING` tasks.

### 2. At-Least-Once Delivery
Manual Kafka offset acknowledgment ensures tasks are never silently dropped. If a worker crashes mid-processing, Kafka re-delivers the message to another worker instance.

### 3. Idempotency Check
Before processing, the worker checks if the task is already `SENT` or `DELIVERED`. This prevents duplicate emails if a message is re-delivered by Kafka.

### 4. Priority via Separate Topics
Using separate Kafka topics (not just priority fields) ensures HIGH-priority messages are processed by dedicated consumer threads, regardless of how many NORMAL messages are queued.

### 5. Circuit Breaker (Failover)
The `ProviderSelectionService` implements a simple failover pattern. For production, Resilience4j's `@CircuitBreaker` would automatically open the circuit after N failures and probe for recovery.

### 6. Distributed Rate Limiting
The current Bucket4j implementation is per-instance. For true distributed rate limiting across multiple worker nodes, use Bucket4j's Redis backend to share token buckets across all instances.
