#!/usr/bin/env bash
# One-time (and re-runnable) GCP setup for goldlens-core on Cloud Run + Cloud SQL.
#
# Run it with gcloud (locally or in Cloud Shell), logged in as a project owner:
#   bash deploy/gcp-setup.sh
#
# It creates what the GitHub Actions deploy expects, skipping anything that already exists:
#   - APIs, the Artifact Registry repo, a Postgres Cloud SQL instance + database + app user
#   - Secret Manager secrets (DB password, admin token, API keys)
#   - a runtime service account for Cloud Run
#   - a deploy service account that GitHub Actions uses through Workload Identity Federation,
#     plus the roles it and Cloud Build need
# At the end it prints the WIF_PROVIDER and SA_EMAIL values to store as GitHub secrets.
#
# Secrets are read with hidden prompts and never printed or written to disk.

set -euo pipefail

PROJECT_ID="${PROJECT_ID:-goldlens-prod-2026}"
REGION="${REGION:-asia-south1}"
AR_REPO="${AR_REPO:-goldlens-repo}"
SQL_INSTANCE="${SQL_INSTANCE:-goldlens-db}"
SQL_TIER="${SQL_TIER:-db-f1-micro}"
DB_NAME="${DB_NAME:-goldlens}"
DB_USER="${DB_USER:-goldlens_app}"
RUNTIME_SA_NAME="${RUNTIME_SA_NAME:-goldlens-core-run}"
RUNTIME_SA="${RUNTIME_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
DEPLOY_SA_NAME="${DEPLOY_SA_NAME:-github-deployer}"
DEPLOY_SA="${DEPLOY_SA:-${DEPLOY_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com}"
GITHUB_OWNER="${GITHUB_OWNER:-adityaahir3105}"
GITHUB_REPOS="${GITHUB_REPOS:-goldlens-core goldlens-ui}"
WIF_POOL="${WIF_POOL:-github}"
WIF_PROVIDER_ID="${WIF_PROVIDER_ID:-github-oidc}"

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

gcloud config set project "$PROJECT_ID" >/dev/null
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"

project_role() { # member role
  gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="$1" --role="$2" --condition=None >/dev/null
}

log "Enabling APIs"
gcloud services enable \
  run.googleapis.com sqladmin.googleapis.com secretmanager.googleapis.com \
  artifactregistry.googleapis.com cloudbuild.googleapis.com iamcredentials.googleapis.com \
  sts.googleapis.com iam.googleapis.com compute.googleapis.com

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
until [[ "$(gcloud sql instances describe "$SQL_INSTANCE" --format='value(state)')" == "RUNNABLE" ]]; do
  echo "  waiting for $SQL_INSTANCE to become RUNNABLE..."
  sleep 20
done

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
  project_role "serviceAccount:$RUNTIME_SA" "$role"
done

log "Deploy service account $DEPLOY_SA"
gcloud iam service-accounts describe "$DEPLOY_SA" >/dev/null 2>&1 \
  || gcloud iam service-accounts create "$DEPLOY_SA_NAME" --display-name="GitHub Actions deployer"

# run.admin: deploy + make the service public. The rest: submit Cloud Build jobs from the workflow.
for role in roles/run.admin roles/cloudbuild.builds.editor roles/artifactregistry.writer \
            roles/storage.admin roles/serviceusage.serviceUsageConsumer roles/logging.viewer; do
  project_role "serviceAccount:$DEPLOY_SA" "$role"
done
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
  --member="serviceAccount:$DEPLOY_SA" --role=roles/iam.serviceAccountUser >/dev/null

log "Cloud Build service account (new projects build as the Compute Engine default account)"
BUILD_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
for role in roles/artifactregistry.writer roles/logging.logWriter roles/storage.objectViewer; do
  project_role "serviceAccount:$BUILD_SA" "$role"
done
gcloud iam service-accounts add-iam-policy-binding "$BUILD_SA" \
  --member="serviceAccount:$DEPLOY_SA" --role=roles/iam.serviceAccountUser >/dev/null

log "Workload Identity Federation for GitHub Actions ($GITHUB_OWNER: $GITHUB_REPOS)"
gcloud iam workload-identity-pools describe "$WIF_POOL" --location=global >/dev/null 2>&1 \
  || gcloud iam workload-identity-pools create "$WIF_POOL" --location=global --display-name="GitHub Actions"
gcloud iam workload-identity-pools providers describe "$WIF_PROVIDER_ID" \
    --location=global --workload-identity-pool="$WIF_POOL" >/dev/null 2>&1 \
  || gcloud iam workload-identity-pools providers create-oidc "$WIF_PROVIDER_ID" \
       --location=global --workload-identity-pool="$WIF_POOL" \
       --issuer-uri="https://token.actions.githubusercontent.com" \
       --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner" \
       --attribute-condition="assertion.repository_owner == '${GITHUB_OWNER}'"
POOL_NAME="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${WIF_POOL}"
for repo in $GITHUB_REPOS; do
  gcloud iam service-accounts add-iam-policy-binding "$DEPLOY_SA" \
    --role=roles/iam.workloadIdentityUser \
    --member="principalSet://iam.googleapis.com/${POOL_NAME}/attribute.repository/${GITHUB_OWNER}/${repo}" >/dev/null
done

log "Done"
cat <<EOF
GitHub secrets (Settings -> Secrets and variables -> Actions) for each of: $GITHUB_REPOS
  WIF_PROVIDER = ${POOL_NAME}/providers/${WIF_PROVIDER_ID}
  SA_EMAIL     = ${DEPLOY_SA}

Next:
  1. Merge the goldlens-core PR (or run the "Deploy to Cloud Run" workflow manually).
  2. Watch the first boot:  gcloud run services logs tail goldlens-core --region $REGION
  3. Admin token for /api/admin/* calls:
       gcloud secrets versions access latest --secret=goldlens-admin-token
EOF
