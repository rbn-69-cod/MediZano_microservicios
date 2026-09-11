#!/usr/bin/env bash
set -Eeuo pipefail

readonly PROJECT_DIR="${MEDIZANO_PROJECT_DIR:-/opt/medizano}"
readonly DOMAIN="${MEDIZANO_DOMAIN:-medizano.sbs}"
readonly BASE_URL="https://${DOMAIN}"
readonly COMPOSE_FILES=(-f docker-compose.yml -f docker-compose.prod.yml)
readonly RESOLVE_ARGS=(--noproxy '*' --resolve "${DOMAIN}:443:127.0.0.1" --resolve "${DOMAIN}:80:127.0.0.1")

cd "${PROJECT_DIR}"

echo CONTAINERS
docker compose "${COMPOSE_FILES[@]}" ps --format '{{.Service}}|{{.Status}}' | sort

echo HTTP
for path in / /actuator/health /v3/api-docs/usuario /webjars/swagger-ui/index.html /.env; do
    code="$(curl "${RESOLVE_ARGS[@]}" --max-time 20 -sS -o /dev/null -w '%{http_code}' "${BASE_URL}${path}")"
    echo "${code}|${path}"
done
redirect="$(curl --noproxy '*' --resolve "${DOMAIN}:80:127.0.0.1" --max-time 20 -sSI "http://${DOMAIN}/" | awk 'tolower($1)=="location:"{gsub("\\r",""); print $2}')"
echo "REDIRECT|${redirect}"

echo HEADERS
curl "${RESOLVE_ARGS[@]}" --max-time 20 -sSI "${BASE_URL}/" \
    | grep -Ei '^(strict-transport-security|server|x-content-type-options|x-frame-options|content-security-policy):' || true

echo CORS
allowed="$(curl "${RESOLVE_ARGS[@]}" --max-time 20 -sSI -X OPTIONS "${BASE_URL}/api/auth/login" \
    -H "Origin: ${BASE_URL}" -H 'Access-Control-Request-Method: POST' \
    | awk -F': ' 'tolower($1)=="access-control-allow-origin"{gsub("\\r","",$2); print $2}')"
blocked="$(curl "${RESOLVE_ARGS[@]}" --max-time 20 -sSI -X OPTIONS "${BASE_URL}/api/auth/login" \
    -H 'Origin: https://evil.example' -H 'Access-Control-Request-Method: POST' \
    | awk -F': ' 'tolower($1)=="access-control-allow-origin"{gsub("\\r","",$2); print $2}')"
echo "ALLOWED_ORIGIN|${allowed:-none}"
echo "UNTRUSTED_ORIGIN|${blocked:-none}"

echo AUTH
set -a
# shellcheck disable=SC1091
source "${PROJECT_DIR}/.env"
set +a
login_payload="$(python3 -c 'import json,os; print(json.dumps({"username":"admin","password":os.environ["MEDIZANO_DEFAULT_PASSWORD"]}))')"
login_file="$(mktemp)"
config_file="$(mktemp)"
cleanup() {
    rm -f "${login_file}" "${config_file}"
}
trap cleanup EXIT
login_code="$(curl "${RESOLVE_ARGS[@]}" --max-time 20 -sS -o "${login_file}" -w '%{http_code}' \
    -H 'Content-Type: application/json' --data "${login_payload}" "${BASE_URL}/api/auth/login")"
token="$(python3 - "${login_file}" <<'PY'
import json
import sys

try:
    data = json.load(open(sys.argv[1], encoding="utf-8"))
    print(data.get("token") or data.get("accessToken") or "")
except Exception:
    print("")
PY
)"
if [[ -n "${token}" ]]; then
    token_status=present
else
    token_status=missing
fi
echo "LOGIN|${login_code}|token_${token_status}"
unauth="$(curl "${RESOLVE_ARGS[@]}" --max-time 20 -sS -o /dev/null -w '%{http_code}' "${BASE_URL}/api/admin/users")"
auth="$(curl "${RESOLVE_ARGS[@]}" --max-time 20 -sS -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer ${token}" "${BASE_URL}/api/admin/users")"
echo "USERS_NO_TOKEN|${unauth}"
echo "USERS_WITH_TOKEN|${auth}"
config_code="$(curl "${RESOLVE_ARGS[@]}" --max-time 20 -sS -o "${config_file}" -w '%{http_code}' \
    -H "Authorization: Bearer ${token}" "${BASE_URL}/api/v1/pagos/config")"
python3 - "${config_code}" "${config_file}" <<'PY'
import json
import sys

data = json.load(open(sys.argv[2], encoding="utf-8"))
print(
    f"PAYMENTS|{sys.argv[1]}|paypal={bool(data.get('payPalClientId'))}"
    f"|mercadopago={bool(data.get('mpPublicKey'))}"
)
PY

echo INFRA
ufw status | sed -n '1,20p'
echo "FAIL2BAN|$(systemctl is-active fail2ban)"
echo "UNATTENDED_UPGRADES|$(systemctl is-active unattended-upgrades)"
latest_backup="$(find /opt/medizano-backups -type f -name 'medizano-*.sql.gz' -print | sort | tail -1)"
gzip -t "${latest_backup}"
echo "BACKUP_OK|$(basename "${latest_backup}")"
echo "CRON|$(systemctl is-active cron)"

echo LOG_ERRORS
docker compose "${COMPOSE_FILES[@]}" logs --since=90s \
    eureka-server api-gateway usuario-ms catalogo-ms cliente-ms orden-ms \
    inventario-ms pago-ms facturacion-ms 2>&1 \
    | grep -E -i '(^|[^a-z])(ERROR|FATAL)([^a-z]|$)|Exception' \
    | tail -n 40 || true
