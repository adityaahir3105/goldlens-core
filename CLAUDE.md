# GoldLens Core

Spring Boot 4.0 / Java 21 / Maven wrapper. PostgreSQL via JPA. Backend for goldlens-ui.
Pulls FRED macro data, gold prices and news, computes gold risk signals, and explains them with Gemini.

## Map (`src/main/java/com/goldlens/`)
- `controller/` REST endpoints (listed in README "API Endpoints") → `service/` logic → `repository/` + `domain/` JPA
- `dto/` request/response types; `client/` external APIs (GoldAPI, news); `scheduler/` timed jobs incl. `FredClient`
- `ai/` Gemini client + ExplainService; `backfill/` one-off history loaders; `config/` CORS, cache, `AdminTokenFilter`
- Config: `src/main/resources/application.yml` plus `-dev`, `-local`, `-gcp`, `-prod` profiles
- Deploy: `.github/workflows/deploy.yml` → Cloud Run (asia-south1); `deploy/` holds GCP setup scripts

## Commands
- `./mvnw -q test` – CI does not run tests, so run them before pushing
- `./mvnw -q -DskipTests package` – build check
- Scheduler timings and first-start behaviour are documented in README; read that section rather than tracing code.

## Rules
- Env vars (README table) are the only source of secrets; never hard-code keys.
- `/api/admin/*` must stay behind `X-Admin-Token`.

## Token hygiene
- `target/`, `*.xlsx` (binary ETF data) and `.env` files are read-denied in `.claude/settings.json`.
- Use `-q` with Maven and grep failures (`grep -E "ERROR|FAIL|Tests run"`) instead of reading full logs.

## Compact instructions
When compacting, keep: the current task, files changed, decisions made, failing checks. Drop file dumps.
