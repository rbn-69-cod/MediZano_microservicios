# ============================================================
# Frontend MediZano POS - Servidor Web Nginx de Producción
# ============================================================
FROM nginx:alpine

# Copiar artefactos de la aplicación Angular compilada
COPY dist/medical-store-pos/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=5s --retries=3 \
  CMD wget -q -O - http://127.0.0.1/ || exit 1

CMD ["nginx", "-g", "daemon off;"]

