#!/usr/bin/env bash
set -Eeuo pipefail

readonly PROJECT_DIR="${MEDIZANO_PROJECT_DIR:-/opt/medizano}"
readonly BACKUP_DIR="${MEDIZANO_BACKUP_DIR:-/opt/medizano-backups}"

umask 077
set -a
# shellcheck disable=SC1091
source "${PROJECT_DIR}/.env"
set +a

mkdir -p "${BACKUP_DIR}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="${BACKUP_DIR}/medizano-${timestamp}.sql.gz"
temporary_file="${backup_file}.tmp"

cleanup() {
    rm -f "${temporary_file}"
}
trap cleanup EXIT

docker exec medizano-postgres \
    pg_dumpall --clean --if-exists --username "${POSTGRES_USER}" \
    | gzip -9 >"${temporary_file}"

mv "${temporary_file}" "${backup_file}"
find "${BACKUP_DIR}" -type f -name 'medizano-*.sql.gz' -mtime +14 -delete

printf 'Backup completed: %s\n' "${backup_file}"
