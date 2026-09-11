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

read -r -s -p "Mercado Pago Access Token (entrada oculta): " mp_access_token
printf '\n'
read -r -s -p "Mercado Pago Public Key (entrada oculta): " mp_public_key
printf '\n'

if [[ ${#mp_access_token} -lt 30 || ${#mp_public_key} -lt 30 ]]; then
  unset mp_access_token mp_public_key
  echo "Las credenciales parecen incompletas; no se modifico la configuracion." >&2
  exit 1
fi

if command -v curl >/dev/null 2>&1; then
  validation_status="$(curl -sS -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer ${mp_access_token}" \
    https://api.mercadopago.com/users/me || true)"
  if [[ "${validation_status}" != "200" ]]; then
    unset mp_access_token mp_public_key
    echo "Mercado Pago rechazo el Access Token (HTTP ${validation_status}). No se modifico la configuracion." >&2
    exit 1
  fi
fi

tmp_file="$(mktemp "${APP_DIR}/.env.mercadopago.XXXXXX")"
trap 'rm -f "${tmp_file}"; unset mp_access_token mp_public_key' EXIT
chmod --reference="${ENV_FILE}" "${tmp_file}"

found_access_token=false
found_public_key=false
found_base_url=false
found_currency=false

while IFS= read -r line || [[ -n "${line}" ]]; do
  case "${line}" in
    MERCADOPAGO_ACCESS_TOKEN=*)
      printf 'MERCADOPAGO_ACCESS_TOKEN=%s\n' "${mp_access_token}" >> "${tmp_file}"
      found_access_token=true
      ;;
    MERCADOPAGO_PUBLIC_KEY=*)
      printf 'MERCADOPAGO_PUBLIC_KEY=%s\n' "${mp_public_key}" >> "${tmp_file}"
      found_public_key=true
      ;;
    MERCADOPAGO_BASE_URL=*)
      printf 'MERCADOPAGO_BASE_URL=https://api.mercadopago.com\n' >> "${tmp_file}"
      found_base_url=true
      ;;
    MERCADOPAGO_CURRENCY=*)
      printf 'MERCADOPAGO_CURRENCY=PEN\n' >> "${tmp_file}"
      found_currency=true
      ;;
    *)
      printf '%s\n' "${line}" >> "${tmp_file}"
      ;;
  esac
done < "${ENV_FILE}"

${found_access_token} || printf 'MERCADOPAGO_ACCESS_TOKEN=%s\n' "${mp_access_token}" >> "${tmp_file}"
${found_public_key} || printf 'MERCADOPAGO_PUBLIC_KEY=%s\n' "${mp_public_key}" >> "${tmp_file}"
${found_base_url} || printf 'MERCADOPAGO_BASE_URL=https://api.mercadopago.com\n' >> "${tmp_file}"
${found_currency} || printf 'MERCADOPAGO_CURRENCY=PEN\n' >> "${tmp_file}"

mv "${tmp_file}" "${ENV_FILE}"
trap - EXIT
unset mp_access_token mp_public_key

cd "${APP_DIR}"
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-deps --force-recreate pago-ms

for _ in {1..30}; do
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' medizano-pago-ms 2>/dev/null || true)"
  if [[ "${health}" == "healthy" ]]; then
    echo "Mercado Pago configurado y pago-ms esta saludable."
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
