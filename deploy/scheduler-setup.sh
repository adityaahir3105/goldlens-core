#!/usr/bin/env bash
# Creates (or updates) the Cloud Scheduler jobs that replace goldlens-core's in-app timers.
#
# goldlens-core runs on Cloud Run with request-based billing, so CPU is only available while a
# request is being served and the @Scheduled timers are switched off there. These jobs call the
# admin job endpoints instead (UTC, same times as the old timers):
#   goldlens-gold-price  every 6 hours   POST /api/admin/jobs/gold-price
#   goldlens-daily       06:00 daily     POST /api/admin/jobs/daily  (real yield, DXY, then gold risk)
#
# Run it once, logged in as a project owner:
#   bash deploy/scheduler-setup.sh
# The admin token is read from Secret Manager (goldlens-admin-token) and never printed.

set -euo pipefail

export CLOUDSDK_CORE_DISABLE_PROMPTS=1
PROJECT_ID="${PROJECT_ID:-goldlens-prod-2026}"
REGION="${REGION:-asia-south1}"
SERVICE="${SERVICE:-goldlens-core}"
export CLOUDSDK_BILLING_QUOTA_PROJECT="$PROJECT_ID"

gcloud services enable cloudscheduler.googleapis.com --project "$PROJECT_ID"

URL=$(gcloud run services describe "$SERVICE" --region "$REGION" --project "$PROJECT_ID" --format='value(status.url)')
# Strip line endings: a secret saved on Windows can end in \r, which HTTP drops from the header.
TOKEN=$(gcloud secrets versions access latest --secret goldlens-admin-token --project "$PROJECT_ID" | tr -d '\r\n')

upsert_job() {
  local name="$1" schedule="$2" path="$3"
  local verb=create
  if gcloud scheduler jobs describe "$name" --location "$REGION" --project "$PROJECT_ID" >/dev/null 2>&1; then
    verb=update
  fi
  local header_flag=--headers
  [ "$verb" = update ] && header_flag=--update-headers
  gcloud scheduler jobs "$verb" http "$name" --location "$REGION" --project "$PROJECT_ID" \
    --schedule "$schedule" --time-zone "Etc/UTC" \
    --uri "${URL}${path}" --http-method POST \
    "$header_flag" "X-Admin-Token=${TOKEN}" \
    --attempt-deadline 300s --max-retry-attempts 2 --min-backoff 60s >/dev/null
  echo "$verb: $name ($schedule UTC) -> $path"
}

upsert_job goldlens-gold-price "0 */6 * * *" /api/admin/jobs/gold-price
upsert_job goldlens-daily "0 6 * * *" /api/admin/jobs/daily
