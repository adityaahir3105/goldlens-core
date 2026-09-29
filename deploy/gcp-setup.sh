#!/usr/bin/env bash
# One-time (and re-runnable) GCP setup for goldlens-core on Cloud Run + Cloud SQL.
#
# Run it in Cloud Shell, logged in as a project owner:
#   DEPLOY_SA=<value of the SA_EMAIL GitHub secret> bash deploy/gcp-setup.sh
#
# It creates what the GitHub Actions deploy expects, skipping anything that already exists:
#   - APIs, the Artifact Registry repo, a Postgres Cloud SQL instance + database + app user
#   - Secret Manager secrets (DB password, admin token, API keys)
#   - a runtime service account for Cloud Run, and the roles the deploy account needs
#
# Secrets are read with hidden prompts and never printed or written to disk.

set -euo pipefail

PROJECT_ID="${PROJECT_ID:-goldlens-app-2026}"
REGION="${REGION:-asia-south1}"
AR_REPO="${AR_REPO:-goldlens-repo}"
SQL_INSTANCE="${SQL_INSTANCE:-goldlens-db}"
SQL_TIER="${SQL_TIER:-db-f1-micro}"
DB_NAME="${DB_NAME:-goldlens}"
DB_USER="${DB_USER:-goldlens_app}"
RUNTIME_SA_NAME="${RUNTIME_SA_NAME:-goldlens-core-run}"
RUNTIME_SA="${RUNTIME_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

# Secret name -> what to ask for. Must match --set-secrets in .github/workflows/deploy.yml.
API_KEY_SECRETS=(
  "fred-api-key:FRED API key"
  "gemini-api-key:Gemini API key"
  "gold-api-key:GoldAPI.io key"
  "news-api-key:NewsAPI key"
  "gnews-api-key:GNews API key"
)

log() { printf '\n\033[1;33m==> %s\033[0m\n' "$*"; }

secret_exists() { gcloud secrets describe "$1" --project "$PROJECT_ID" >/dev/null 2>&1; }

# Adds a new version to a secret (creating the secret first if needed), reading the value from stdin.
put_secret() {
  local name="$1"
  if ! secret_exists "$name"; then
    gcloud secrets create "$name" --project "$PROJECT_ID" --replication-policy=automatic >/dev/null
  fi
  gcloud secrets versions add "$name" --project "$PROJECT_ID" --data-file=- >/dev/null
}

if [[ -z "${DEPLOY_SA:-}" ]]; then
  read -rp "Deploy service account email (the SA_EMAIL GitHub secret): " DEPLOY_SA
fi

gcloud config set project "$PROJECT_ID" >/dev/null

log "Enabling APIs"
gcloud services enable \
  run.googleapis.com sqladmin.googleapis.com secretmanager.googleapis.com \
  artifactregistry.googleapis.com cloudbuild.googleapis.com iamcredentials.googleapis.com

log "Checking Workload Identity Federation (used by GitHub Actions)"
if [[ -z "$(gcloud iam workload-identity-pools list --location=global --format='value(name)' 2>/dev/null)" ]]; then
  echo "WARNING: no workload identity pools found. The GitHub deploy authenticates through one"
  echo "(WIF_PROVIDER secret), so it will fail until that is recreated."
fi
gcloud iam service-accounts describe "$DEPLOY_SA" >/dev/null \
  || { echo "Deploy service account $DEPLOY_SA not found"; exit 1; }

log "Artifact Registry repo $AR_REPO"
gcloud artifacts repositories describe "$AR_REPO" --location "$REGION" >/dev/null 2>&1 \
  || gcloud artifacts repositories create "$AR_REPO" --repository-format=docker --location "$REGION"

log "Cloud SQL instance $SQL_INSTANCE (creating one takes several minutes)"
if ! gcloud sql instances describe "$SQL_INSTANCE" >/dev/null 2>&1; then
  gcloud sql instances create "$SQL_INSTANCE" \
    --database-version=POSTGRES_16 \
    --edition=ENTERPRISE \
    --tier="$SQL_TIER" \
    --region="$REGION" \
    --storage-size=10GB \
    --storage-auto-increase \
    --backup-start-time=20:00
fi

gcloud sql databases describe "$DB_NAME" --instance "$SQL_INSTANCE" >/dev/null 2>&1 \
  || gcloud sql databases create "$DB_NAME" --instance "$SQL_INSTANCE"

log "Database user $DB_USER and its password secret"
if secret_exists goldlens-db-password && gcloud sql users list --instance "$SQL_INSTANCE" \
     --format='value(name)' | grep -qx "$DB_USER"; then
  echo "User and password secret already exist - leaving them as they are."
else
  DB_PASSWORD="$(openssl rand -base64 32 | tr -d '/+=' | cut -c1-32)"
  if gcloud sql users list --instance "$SQL_INSTANCE" --format='value(name)' | grep -qx "$DB_USER"; then
    gcloud sql users set-password "$DB_USER" --instance "$SQL_INSTANCE" --password "$DB_PASSWORD"
  else
    gcloud sql users create "$DB_USER" --instance "$SQL_INSTANCE" --password "$DB_PASSWORD"
  fi
  printf '%s' "$DB_PASSWORD" | put_secret goldlens-db-password
  unset DB_PASSWORD
fi

log "Admin token secret (protects /api/admin/*)"
if secret_exists goldlens-admin-token; then
  echo "Already exists - leaving it as it is."
else
  openssl rand -hex 32 | tr -d '\n' | put_secret goldlens-admin-token
fi

log "API key secrets (press Enter to keep an existing value)"
for entry in "${API_KEY_SECRETS[@]}"; do
  name="${entry%%:*}"
  label="${entry#*:}"
  existing=""
  secret_exists "$name" && existing=" [set]"
  read -rsp "  $label$existing: " value
  echo
  if [[ -n "$value" ]]; then
    printf '%s' "$value" | put_secret "$name"
  elif [[ -z "$existing" ]]; then
    echo "  $name is required by the deploy; re-run this script to set it."
  fi
  unset value
done

log "Runtime service account $RUNTIME_SA"
gcloud iam service-accounts describe "$RUNTIME_SA" >/dev/null 2>&1 \
  || gcloud iam service-accounts create "$RUNTIME_SA_NAME" --display-name="goldlens-core Cloud Run runtime"

for role in roles/cloudsql.client roles/secretmanager.secretAccessor; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:$RUNTIME_SA" --role="$role" --condition=None >/dev/null
done

log "Letting the deploy account deploy as the runtime account and make the service public"
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
  --member="serviceAccount:$DEPLOY_SA" --role=roles/iam.serviceAccountUser >/dev/null
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$DEPLOY_SA" --role=roles/run.admin --condition=None >/dev/null

log "Done"
cat <<EOF
Next:
  1. Merge the goldlens-core PR (or run the "Deploy to Cloud Run" workflow manually).
  2. Watch the first boot:  gcloud run services logs tail goldlens-core --region $REGION
  3. Admin token for /api/admin/* calls:
       gcloud secrets versions access latest --secret=goldlens-admin-token
EOF
