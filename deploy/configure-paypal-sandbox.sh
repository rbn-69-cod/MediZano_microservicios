#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${1:-/opt/medizano}"
ENV_FILE="${APP_DIR}/.env"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "No se encontro ${ENV_FILE}." >&2
  exit 1
fi

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Ejecuta este script como root." >&2
  exit 1
fi

read -r -p "PayPal Sandbox Client ID: " paypal_client_id
read -r -s -p "PayPal Sandbox Secret (entrada oculta): " paypal_client_secret
printf '\n'

if [[ ${#paypal_client_id} -lt 20 || ${#paypal_client_secret} -lt 20 ]]; then
  unset paypal_client_id paypal_client_secret
  echo "Las credenciales parecen incompletas; no se modifico la configuracion." >&2
  exit 1
fi

tmp_file="$(mktemp "${APP_DIR}/.env.paypal.XXXXXX")"
trap 'rm -f "${tmp_file}"; unset paypal_client_id paypal_client_secret' EXIT
chmod --reference="${ENV_FILE}" "${tmp_file}"

found_client_id=false
found_client_secret=false
found_base_url=false
found_currency=false

while IFS= read -r line || [[ -n "${line}" ]]; do
  case "${line}" in
    PAYPAL_CLIENT_ID=*)
      printf 'PAYPAL_CLIENT_ID=%s\n' "${paypal_client_id}" >> "${tmp_file}"
      found_client_id=true
      ;;
    PAYPAL_CLIENT_SECRET=*)
      printf 'PAYPAL_CLIENT_SECRET=%s\n' "${paypal_client_secret}" >> "${tmp_file}"
      found_client_secret=true
      ;;
    PAYPAL_BASE_URL=*)
      printf 'PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com\n' >> "${tmp_file}"
      found_base_url=true
      ;;
    PAYPAL_CURRENCY=*)
      printf 'PAYPAL_CURRENCY=USD\n' >> "${tmp_file}"
      found_currency=true
      ;;
    *)
      printf '%s\n' "${line}" >> "${tmp_file}"
      ;;
  esac
done < "${ENV_FILE}"

${found_client_id} || printf 'PAYPAL_CLIENT_ID=%s\n' "${paypal_client_id}" >> "${tmp_file}"
${found_client_secret} || printf 'PAYPAL_CLIENT_SECRET=%s\n' "${paypal_client_secret}" >> "${tmp_file}"
${found_base_url} || printf 'PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com\n' >> "${tmp_file}"
${found_currency} || printf 'PAYPAL_CURRENCY=USD\n' >> "${tmp_file}"

mv "${tmp_file}" "${ENV_FILE}"
trap - EXIT
unset paypal_client_id paypal_client_secret

cd "${APP_DIR}"
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-deps --force-recreate pago-ms

for _ in {1..30}; do
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' medizano-pago-ms 2>/dev/null || true)"
  if [[ "${health}" == "healthy" ]]; then
    echo "PayPal Sandbox configurado y pago-ms esta saludable."
    exit 0
  fi
  if [[ "${health}" == "unhealthy" || "${health}" == "exited" ]]; then
    echo "pago-ms quedo en estado ${health}. Revisa: docker logs --tail 100 medizano-pago-ms" >&2
    exit 1
  fi
  sleep 2
done

echo "Las credenciales se guardaron, pero pago-ms aun no reporta estado saludable." >&2
exit 1
