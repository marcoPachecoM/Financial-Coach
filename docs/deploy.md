# Production deploy

Personal-use production setup, about **1–2 USD a month**. Nearly all of it is Gemini; everything else fits in free tiers.

```
GitHub (push to main, CI green)
  └─ .github/workflows/deploy.yml (Workload Identity Federation, no JSON keys)
       ├─ images ─► Artifact Registry (us-west1) ─► Cloud Run service "financial-coach-api"
       │                                           └► Cloud Run job "db-backup"
       └─ npm run build ─► Firebase Hosting (https://<project>.web.app)
Cloud Run ──► Supabase Postgres 17 (session pooler :5432, TLS)
   ├─ secrets from Secret Manager (GEMINI_API_KEY, JWT_SECRET_KEY, DB_PASSWORD)
   └─ stdout ─► Cloud Logging
Cloud Scheduler (03:00 Mexico City) ─► db-backup (pg_dump) ─► Cloud Storage (30 days)
Cloud Monitoring: uptime check of /actuator/health every 15 min ─► email
Budget alert: 5 USD/month
```

| Piece | Where | Cost |
|---|---|---|
| API | Cloud Run, `us-west1`, min 0 / max 1 instance, 1 vCPU / 1 GiB | free tier |
| Database | Supabase Free, West US (Oregon), PostgreSQL 17 | 0 |
| Frontend | Firebase Hosting | free tier |
| Images | Artifact Registry, last 3 kept per image | ~0 |
| Backups | Cloud Storage `us-west1`, 30 days | ~0 |
| Logs, secrets, scheduler, uptime | Cloud Logging, Secret Manager, Cloud Scheduler, Cloud Monitoring | free tier |
| Copilot and categorization | Gemini API, paid tier, Flash-Lite model | ~1–2 USD |

**Why `max-instances=1`:** the rate limits, the login throttle and the chat memory are kept in memory. One instance keeps them consistent, and it also caps the cost.

**Time zone:** `America/Mexico_City`. It is set both in the image (`TZ` and `-Duser.timezone`) and in the service. The app logs `Clock zone: …` at startup.

---

## First deploy

Do the steps in this order. The commands use these shell variables:

```bash
PROJECT=financial-coach-515803          # GCP project id (globally unique)
REGION=us-west1
REPO=marcoPachecoM/Financial-Coach
```

### 1. Supabase

1. **Connect** → **Session pooler**: copy the connection string. Use port **5432**, because session mode works with JDBC prepared statements and transaction mode (6543) does not. With it you get:
   - `DB_URL`, for example `jdbc:postgresql://aws-0-us-west-2.pooler.supabase.com:5432/postgres?sslmode=require` (copy the host from the dashboard);
   - `DB_USER`: `postgres.<project-ref>`.
2. **Database password:** set a long random one in Project Settings → Database. It goes in Secret Manager in step 3 and never in the repository.
3. **Turn off the Data API** (Project Settings → API / Data API). The app does not use it, and it would expose the `public` tables over REST.
4. **Defense in depth:** in the SQL editor, run the following. It also covers the tables the migrations create later:

   ```sql
   REVOKE ALL ON ALL TABLES    IN SCHEMA public FROM anon, authenticated;
   REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;
   REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM anon, authenticated;
   ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON TABLES    FROM anon, authenticated;
   ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon, authenticated;
   ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM anon, authenticated;
   ```

You don't need to create any tables. The first deploy runs the migrations. `beforeMigrate.sql` and `beforeEachMigrate.sql` adapt them to PostgreSQL 17, which lacks `uuidv7()` and named NOT NULL constraints.

### 2. GCP: project, billing, budget, APIs, Gemini

```bash
gcloud projects create "$PROJECT"
gcloud config set project "$PROJECT"
gcloud billing accounts list                                  # pick the billing account id
gcloud billing projects link "$PROJECT" --billing-account=XXXXXX-XXXXXX-XXXXXX

gcloud services enable run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com \
  cloudscheduler.googleapis.com iamcredentials.googleapis.com sts.googleapis.com monitoring.googleapis.com \
  logging.googleapis.com firebase.googleapis.com firebasehosting.googleapis.com billingbudgets.googleapis.com \
  generativelanguage.googleapis.com

# Budget alert: emails at 50 %, 90 % and 100 % of 5 USD. It only alerts; it does not stop spending.
gcloud billing budgets create --billing-account=XXXXXX-XXXXXX-XXXXXX --display-name="financial-coach" \
  --budget-amount=5USD --threshold-rule=percent=0.5 --threshold-rule=percent=0.9 --threshold-rule=percent=1.0
```

**Gemini:** in Google AI Studio, create an API key **in this project**, which already has billing, so the key uses the paid tier. On the free tier Google may use prompts to improve its products, and prompts carry your financial data. Also choose a Flash-Lite model for `GEMINI_MODEL` (step 5).

### 3. GCP: images, backups bucket, secrets, accounts, Workload Identity

```bash
# Artifact Registry, keeping only the last 3 versions of each image.
gcloud artifacts repositories create financial-coach --repository-format=docker --location="$REGION"
cat > /tmp/ar-cleanup.json <<'EOF'
[
  {"name": "keep-last-3", "action": {"type": "Keep"}, "mostRecentVersions": {"keepCount": 3}},
  {"name": "delete-older", "action": {"type": "Delete"}, "condition": {"tagState": "any", "olderThan": "1d"}}
]
EOF
gcloud artifacts repositories set-cleanup-policies financial-coach --location="$REGION" \
  --policy=/tmp/ar-cleanup.json --no-dry-run

# Backups bucket: private, deletes dumps after 30 days and leftover .partial files after 1 day.
gcloud storage buckets create "gs://$PROJECT-db-backups" --location="$REGION" \
  --default-storage-class=STANDARD --uniform-bucket-level-access --public-access-prevention
cat > /tmp/backup-lifecycle.json <<'EOF'
{"rule": [
  {"action": {"type": "Delete"}, "condition": {"age": 30}},
  {"action": {"type": "Delete"}, "condition": {"age": 1, "matchesSuffix": [".partial"]}}
]}
EOF

gcloud storage buckets update "gs://$PROJECT-db-backups" --lifecycle-file=/tmp/backup-lifecycle.json

# Secrets. They are read with `read -rs`, so they never reach the shell history.
read -rs -p "Gemini API key: " V && printf '%s' "$V" | gcloud secrets create GEMINI_API_KEY --data-file=- ; unset V; echo

read -rs -p "Supabase DB password: " V && printf '%s' "$V" | gcloud secrets create DB_PASSWORD --data-file=- ; unset V; echo

openssl rand -base64 48 | tr -d '\n' | gcloud secrets create JWT_SECRET_KEY --data-file=-

# Service accounts: one for the API, one for backups, one for GitHub deploys.
gcloud iam service-accounts create api-runtime     --display-name="Financial Coach API"
gcloud iam service-accounts create db-backup       --display-name="Financial Coach DB backup"
gcloud iam service-accounts create github-deployer --display-name="GitHub Actions deploy"
SA() { echo "$1@$PROJECT.iam.gserviceaccount.com"; }

for s in GEMINI_API_KEY JWT_SECRET_KEY DB_PASSWORD; do
  gcloud secrets add-iam-policy-binding "$s" --member="serviceAccount:$(SA api-runtime)" \
    --role=roles/secretmanager.secretAccessor
done
gcloud secrets add-iam-policy-binding DB_PASSWORD --member="serviceAccount:$(SA db-backup)" \
  --role=roles/secretmanager.secretAccessor
# objectUser, not objectCreator: the FUSE mount also lists and reads.
gcloud storage buckets add-iam-policy-binding "gs://$PROJECT-db-backups" \
  --member="serviceAccount:$(SA db-backup)" --role=roles/storage.objectUser

for role in roles/run.admin roles/firebasehosting.admin roles/serviceusage.serviceUsageConsumer; do
  gcloud projects add-iam-policy-binding "$PROJECT" --member="serviceAccount:$(SA github-deployer)" --role="$role"
done
gcloud artifacts repositories add-iam-policy-binding financial-coach --location="$REGION" \
  --member="serviceAccount:$(SA github-deployer)" --role=roles/artifactregistry.writer
# The deployer may run the API and the backup job as their own accounts, and nothing else.
for sa in api-runtime db-backup; do
  gcloud iam service-accounts add-iam-policy-binding "$(SA $sa)" \
    --member="serviceAccount:$(SA github-deployer)" --role=roles/iam.serviceAccountUser
done

# Workload Identity Federation: only GitHub Actions runs of this repository on main.
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')
gcloud iam workload-identity-pools create github --location=global --display-name="GitHub Actions"
gcloud iam workload-identity-pools providers create-oidc github --location=global \
  --workload-identity-pool=github --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
  --attribute-condition="assertion.repository == '$REPO' && assertion.ref == 'refs/heads/main'"
gcloud iam service-accounts add-iam-policy-binding "$(SA github-deployer)" \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/$REPO"
echo "GCP_WIF_PROVIDER=projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/github"
```

### 4. Firebase

In the Firebase console, choose **Add project** and pick the existing GCP project (`$PROJECT`). Then go to **Hosting → Get started**; you don't need to install anything locally.

That creates the default site `https://$PROJECT.web.app`, which is the origin the deploy puts in `CORS_ALLOWED_ORIGINS`.

### 5. GitHub variables

Go to Settings → Secrets and variables → Actions → **Variables**. None of these are secrets; the secrets live in Secret Manager.

| Variable | Value |
|---|---|
| `GCP_PROJECT_ID` | `$PROJECT` |
| `GCP_WIF_PROVIDER` | the `projects/…/providers/github` value printed in step 3 |
| `GCP_DEPLOY_SA` | `github-deployer@$PROJECT.iam.gserviceaccount.com` |
| `DB_URL` | the JDBC URL from step 1 |
| `DB_USER` | `postgres.<project-ref>` |
| `GEMINI_MODEL` | a Flash-Lite model, e.g. `gemini-flash-lite-latest` |

### 6. First deploy

Go to Actions → **Deploy** → *Run workflow* on `main`. The workflow:

1. builds and pushes the `api` and `db-backup` images;
2. creates the Cloud Run service (the migrations run against Supabase at startup) and the backup job;
3. checks `/actuator/health`;
4. builds the frontend with the API URL it just got, and publishes it to Firebase Hosting.

From then on, every push to `main` with a green CI deploys by itself.

### 7. Checks

```bash
URL=$(gcloud run services describe financial-coach-api --region "$REGION" --format 'value(status.url)')
curl -s "$URL/actuator/health"          # {"status":"UP",...}
gcloud logging read 'resource.type="cloud_run_revision" AND textPayload:"Clock zone"' --limit 1 --format 'value(textPayload)'
```

Then open `https://$PROJECT.web.app`:

- sign up and sign in;
- import a CSV, open the dashboard, ask the copilot a question (the answer should stream) and open the goals;
- confirm the browser console shows **no CSP or CORS errors**.

### 8. The first admin

1. Sign up from the app as a normal user, and **save your recovery codes**.
2. In Supabase's SQL editor, promote that user:

   ```sql
   UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';
   ```

3. Sign out and back in, so the new token carries the role.

### 9. Backups and monitoring

```bash
# Daily backup at 03:00 Mexico City time. Scheduler starts the job as db-backup, which may run it.
gcloud run jobs add-iam-policy-binding db-backup --region "$REGION" \
  --member="serviceAccount:$(SA db-backup)" --role=roles/run.invoker
gcloud scheduler jobs create http db-backup-daily --location "$REGION" \
  --schedule "0 3 * * *" --time-zone "America/Mexico_City" --http-method POST \
  --uri "https://run.googleapis.com/v2/projects/$PROJECT/locations/$REGION/jobs/db-backup:run" \
  --oauth-service-account-email "$(SA db-backup)"

# Run it once now and check the file.
gcloud run jobs execute db-backup --region "$REGION" --wait
gcloud storage ls -l "gs://$PROJECT-db-backups/"
```

**Restore that backup locally before calling the deploy done.** See [Restore a backup](#restore-a-backup) below.

**Uptime check and alerts** (Cloud Monitoring console):

1. **Alerting → Edit notification channels:** add your email.
2. **Uptime checks → Create:**
   - protocol HTTPS, the host of `$URL`, path `/actuator/health`;
   - check every **15 minutes**;
   - response content contains `"status":"UP"`.

   Create the alert it offers, and send it to your email.
3. **Alerting → Create policy:**
   - metric *Cloud Run Job → Completed executions* (`run.googleapis.com/job/completed_execution_count`);
   - filter `job_name = db-backup` and `result = failed`;
   - condition: above 0 in 1 hour;
   - send it to your email.

   A backup that stops working must not go unnoticed.

---

## Operations

### Roll back a deploy

```bash
gcloud run revisions list --service financial-coach-api --region "$REGION"
gcloud run services update-traffic financial-coach-api --region "$REGION" --to-revisions <previous-revision>=100
```

For the frontend, use Firebase console → Hosting → release history → **Rollback** on the previous release.

Database migrations are never rolled back. If a deploy included a new migration, the previous revision must still work with the new schema; otherwise restore a backup.

The next push to `main` deploys again and sends 100 % of traffic to it.

### Restore a backup

```bash
gcloud storage ls "gs://$PROJECT-db-backups/"                       # pick a dump
gcloud storage cp "gs://$PROJECT-db-backups/financial-coach-<date>.dump" /tmp/restore.dump

# Locally (test): an empty PostgreSQL 17 and the dump.
docker run -d --name restore-test -e POSTGRES_PASSWORD=restore -p 127.0.0.1:5433:5432 postgres:17-alpine
sleep 5
docker cp /tmp/restore.dump restore-test:/tmp/restore.dump
docker exec restore-test pg_restore -U postgres --no-owner -d postgres /tmp/restore.dump
docker exec restore-test psql -U postgres -c \
  "SELECT (SELECT count(*) FROM users) AS users, (SELECT count(*) FROM transactions) AS transactions,
          (SELECT max(version) FROM flyway_schema_history) AS schema_version"
# Optional: start the backend against it (DB_URL=jdbc:postgresql://localhost:5433/postgres,
# DB_USER=postgres, DB_PASSWORD=restore) and sign in with your account.
docker rm -f restore-test
```

To restore **production**:

1. Use a new Supabase project, or empty the current one, as the target.
2. `pg_restore --no-owner --dbname "<pooler URL without jdbc:>"`, with `PGUSER` and `PGPASSWORD` set.
3. Update `DB_URL`, `DB_USER` and the `DB_PASSWORD` secret if the project changed, then redeploy.

### Rotate a secret

```bash
read -rs -p "New value: " V && printf '%s' "$V" | gcloud secrets versions add DB_PASSWORD --data-file=- ; unset V; echo
```

Then redeploy (Actions → Deploy → Run workflow): the service reads `:latest` when a new revision starts.

- **`JWT_SECRET_KEY`:** rotating it signs everyone out.
- **`DB_PASSWORD`:** change it in Supabase first.

### Change the uptime check interval

Each check wakes the instance if it was idle. That avoids cold starts and keeps Supabase active, but every cold start (Spring Boot + JVM, CPU boost) uses vCPU-seconds.

- **During the first two weeks:** compare *Cloud Run → Metrics* (billable instance time, CPU and memory) against the free tier (180k vCPU-s and 360k GiB-s a month).
- **If it runs high:** raise the interval to 30 or 60 minutes in the uptime check settings.

### Client IP (per-IP rate limits)

`server.forward-headers-strategy: native` (in `application-prod.yml`) lets Tomcat read `X-Forwarded-For` **from right to left**, taking the address Google appended. A forged value sent by the client is on the left and is ignored.

Check it after the first deploy and after any infrastructure change:

1. Sign up 4 times from the same network. The fourth attempt must get `429`.
2. With that IP blocked, `curl -H 'X-Forwarded-For: 1.2.3.4' -X POST "$URL/api/v1/auth/register" …` must also get `429`, so the forged header is ignored.
3. From a different network (the phone on mobile data), signing up must still work. Otherwise all users would be sharing one IP.

If step 2 or 3 fails, the peer address Cloud Run hands to the container is not trusted by Tomcat. Fix it in `application-prod.yml`:

- log `request.getRemoteAddr()` temporarily to see that address;
- set `server.tomcat.remoteip.internal-proxies` to match its range;
- deploy and repeat the checks.

### After upgrading PostgreSQL (17 → 18)

On 17, `beforeMigrate.sql` created `public.uuidv7()`. After an upgrade to 18:

- **Nothing breaks.** Unqualified calls resolve to the built-in `pg_catalog.uuidv7()`, because `pg_catalog` is searched first.
- **The existing column defaults keep using the old function.** They reference it by OID, so they still call `public.uuidv7()`, which still generates valid v7 values.
- **Optional cleanup:** a new migration that re-points every `DEFAULT uuidv7()` column, for example `ALTER TABLE users ALTER COLUMN id SET DEFAULT uuidv7();`, then `DROP FUNCTION public.uuidv7();`.
- **The backup image:** switch `backend/ops/Dockerfile.db-backup` to `postgres:18-alpine`, because `pg_dump` must be at least the server's version.

### Known limitations

- **Cold start:** a few seconds for the first request after the instance stopped. The uptime check makes this rare.
- **Chat memory** is lost when the instance stops; the next message starts a new conversation context.
- **Supabase Free pauses after 7 days without activity.** The uptime check and the daily backup connect to the database, which should prevent it, but that is not guaranteed. If the app answers 5xx and `/actuator/health` shows the database DOWN, restore the project from the Supabase dashboard; no data is lost while paused.
- **Supabase Free has no backups of its own.** The daily `db-backup` job is the only copy.
