# GoldLens Core

Backend service for gold risk analysis and macroeconomic indicator tracking.

## Quick Start

### Local Development

```bash
# Start PostgreSQL (Docker)
docker run -d --name goldlens-db \
  -e POSTGRES_DB=goldlens \
  -e POSTGRES_USER=goldlens \
  -e POSTGRES_PASSWORD=goldlens \
  -p 5432:5432 postgres:15

# Run application
./mvnw spring-boot:run
```

### Build JAR

```bash
./mvnw clean package -DskipTests
java -jar target/goldlens-core-0.0.1-SNAPSHOT.jar
```

## Environment Variables

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `DB_URL` | Yes | PostgreSQL JDBC URL | `jdbc:postgresql://host:5432/goldlens` |
| `DB_USERNAME` | Yes | Database username | `goldlens` |
| `DB_PASSWORD` | Yes | Database password | `secret` |
| `FRED_API_KEY` | Yes | FRED API key for macro indicators | `abc123...` |
| `GOLD_API_KEY` | Yes | GoldAPI key for gold prices | `goldapi-xxx` |
| `GEMINI_API_KEY` | No | Google Gemini API key for AI explanations | `AIza...` |
| `ADMIN_TOKEN` | No | Required in the `X-Admin-Token` header for `/api/admin/*`. Unset = admin endpoints disabled | `openssl rand -hex 32` |
| `PORT` | No | Server port (default: 8081) | `8080` |

### Railway/Render Deployment

Set environment variables in your platform dashboard. For Railway with managed PostgreSQL:

```
DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
DB_USERNAME=${{Postgres.PGUSER}}
DB_PASSWORD=${{Postgres.PGPASSWORD}}
```

### Google Cloud Run + Cloud SQL

Pushes to `main` deploy via `.github/workflows/deploy.yml`. Secrets live in Secret Manager,
not in the workflow. Before the first deploy (or after the project was cleaned up), run the
setup script once in Cloud Shell as a project owner:

```bash
DEPLOY_SA=<value of the SA_EMAIL GitHub secret> bash deploy/gcp-setup.sh
```

It creates the Cloud SQL Postgres instance, database and app user, the Secret Manager secrets
(it prompts for each API key), a runtime service account, and the IAM bindings the deploy needs.
It is safe to re-run.

After the service is up, fill the database up to today (FRED indicators, gold prices, signals,
risk snapshot) and load the World Gold Council ETF file:

```bash
URL=$(gcloud run services describe goldlens-core --region asia-south1 --format='value(status.url)')
TOKEN=$(gcloud secrets versions access latest --secret=goldlens-admin-token)
curl -X POST -H "X-Admin-Token: $TOKEN" "$URL/api/admin/backfill-macro"
curl -X POST -H "X-Admin-Token: $TOKEN" -F file=@ETF_Flows_December_2025.xlsx "$URL/api/admin/backfill-wgc"
```

Gold price backfill makes at most 50 GoldAPI calls per run (newest dates first), so a full year
takes several runs and may exceed the GoldAPI free-tier quota.

## Scheduler Timings (UTC)

| Scheduler | Cron | Description |
|-----------|------|-------------|
| RealYieldScheduler | `0 0 6 * * *` | Fetches US 10Y Real Yield from FRED |
| DxyScheduler | `0 5 6 * * *` | Fetches US Dollar Index from FRED |
| GoldRiskScheduler | `0 10 6 * * *` | Aggregates gold risk signals |
| GoldPriceScheduler | `0 15 6 * * *` | Fetches gold spot price from GoldAPI |

## First Start Behavior

On first startup (or when data is insufficient):

1. **Historical Backfill** - Fetches last 90 days of macro indicator data from FRED
2. **Gold Price Backfill** - Fetches last 90 days of gold prices from GoldAPI
3. **Signal Computation** - Generates signals for all indicators

This runs automatically via `HistoricalBackfillService` on `ApplicationReadyEvent`.

## API Endpoints

### Health
- `GET /actuator/health` - Health check

### Indicators
- `GET /api/indicators` - List all indicators
- `GET /api/indicators/{code}` - Get indicator details
- `GET /api/indicators/{code}/history?days=30` - Get indicator history

### Signals
- `GET /api/signals/latest` - Get latest signals for all indicators

### Gold Risk
- `GET /api/gold-risk/latest` - Get aggregated gold risk assessment

### Gold Price
- `GET /api/gold-price/latest` - Get current gold spot price
- `GET /api/gold/price/history?days=30` - Get historical gold prices

### AI Explanations
- `POST /api/ai/explain/indicator` - Explain indicator trend
- `POST /api/ai/explain/signal` - Explain signal
- `POST /api/ai/explain/gold-risk` - Explain gold risk assessment

### Summary
- `GET /api/summary/weekly` - Get weekly summary

## Tech Stack

- Java 21
- Spring Boot 4.x
- PostgreSQL
- WebFlux (for external API calls)

## License

Proprietary
