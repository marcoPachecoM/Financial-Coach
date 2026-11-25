#!/bin/sh
# Daily backup of the production database (Cloud Run Job "db-backup", docs/deploy.md).
#
# Environment:
#   DB_URL       the app's JDBC URL, e.g. jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require
#                (pg_dump takes the same URL without the "jdbc:" prefix)
#   PGUSER       database user (postgres.<project-ref> on Supabase)
#   PGPASSWORD   from Secret Manager
#   BACKUP_DIR   the Cloud Storage bucket mounted as a volume (default /backups)
#
# The dump is written to /tmp and only copied to the bucket once it is complete and readable, so a
# failure never leaves a truncated backup behind: at most a .partial file, which the bucket's
# lifecycle rule deletes. Any failing step fails the job (set -e), which triggers the alert.
set -eu

: "${DB_URL:?DB_URL is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
backup_dir="${BACKUP_DIR:-/backups}"
name="financial-coach-$(date -u +%Y-%m-%dT%H%M%SZ).dump"
tmp="/tmp/$name"

# pg_dump sets statement_timeout = 0 for its own session, so role timeouts do not cut it short.
pg_dump --format=custom --no-owner --no-privileges --dbname="${DB_URL#jdbc:}" --file="$tmp"

# A dump that pg_restore cannot list is not a backup.
pg_restore --list "$tmp" > /dev/null

# Plain cp (no -p/-a): file attributes and owners do not exist on a Cloud Storage FUSE mount.
cp "$tmp" "$backup_dir/$name.partial"
expected=$(wc -c < "$tmp")
copied=$(wc -c < "$backup_dir/$name.partial")
if [ "$expected" -ne "$copied" ]; then
    echo "Copy to the bucket is incomplete: $copied of $expected bytes" >&2
    exit 1
fi
mv "$backup_dir/$name.partial" "$backup_dir/$name"
rm -f "$tmp"
echo "Backup written: $name ($expected bytes)"
