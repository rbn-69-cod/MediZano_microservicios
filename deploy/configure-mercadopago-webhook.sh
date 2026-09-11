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

read -r -s -p "Mercado Pago Webhook Secret (entrada oculta): " mp_webhook_secret
printf '\n'

if [[ ${#mp_webhook_secret} -lt 20 ]]; then
  unset mp_webhook_secret
  echo "La clave secreta parece incompleta; no se modifico la configuracion." >&2
  exit 1
fi

tmp_file="$(mktemp "${APP_DIR}/.env.mercadopago-webhook.XXXXXX")"
trap 'rm -f "${tmp_file}"; unset mp_webhook_secret' EXIT
chmod --reference="${ENV_FILE}" "${tmp_file}"

found_webhook_secret=false
while IFS= read -r line || [[ -n "${line}" ]]; do
  case "${line}" in
    MERCADOPAGO_WEBHOOK_SECRET=*)
      printf 'MERCADOPAGO_WEBHOOK_SECRET=%s\n' "${mp_webhook_secret}" >> "${tmp_file}"
      found_webhook_secret=true
      ;;
    *)
      printf '%s\n' "${line}" >> "${tmp_file}"
      ;;
  esac
done < "${ENV_FILE}"

${found_webhook_secret} || printf 'MERCADOPAGO_WEBHOOK_SECRET=%s\n' "${mp_webhook_secret}" >> "${tmp_file}"

mv "${tmp_file}" "${ENV_FILE}"
trap - EXIT
unset mp_webhook_secret

cd "${APP_DIR}"
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-deps --force-recreate pago-ms

for _ in {1..30}; do
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' medizano-pago-ms 2>/dev/null || true)"
  if [[ "${health}" == "healthy" ]]; then
    echo "Webhook de Mercado Pago configurado y pago-ms esta saludable."
    exit 0
  fi
  if [[ "${health}" == "unhealthy" || "${health}" == "exited" ]]; then
    echo "pago-ms quedo en estado ${health}. Revisa: docker logs --tail 100 medizano-pago-ms" >&2
    exit 1
  fi
  sleep 2
done

echo "La clave se guardo, pero pago-ms aun no reporta estado saludable." >&2
exit 1
