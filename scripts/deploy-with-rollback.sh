#!/usr/bin/env bash
# ============================================================================
# Deploy do DIGITADO com monitoramento e rollback automático (regra 10).
#
# Fluxo:
#   1. Guarda o jar atualmente em produção como candidato a rollback;
#   2. Sobe a nova versão;
#   3. Monitora o health check (/management/health/readiness) por até
#      HEALTH_RETRIES tentativas;
#   4. Se a nova versão não ficar saudável, derruba, restaura o jar anterior
#      e sobe de volta a versão antiga (rollback automático).
#
# Uso:
#   ./deploy-with-rollback.sh caminho/para/digitado-novo.jar
#
# Variáveis configuráveis (via ambiente):
#   APP_DIR         diretório da aplicação   (padrão: /opt/digitado)
#   HEALTH_URL      URL do readiness check   (padrão: http://localhost:8080/management/health/readiness)
#   HEALTH_RETRIES  tentativas de health     (padrão: 30)
#   HEALTH_INTERVAL segundos entre tentativas (padrão: 5)
#   JAVA_OPTS       opções extras da JVM
# ============================================================================
set -euo pipefail

NEW_JAR="${1:?Uso: $0 caminho/para/novo.jar}"
APP_DIR="${APP_DIR:-/opt/digitado}"
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/management/health/readiness}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_INTERVAL="${HEALTH_INTERVAL:-5}"

CURRENT_JAR="$APP_DIR/digitado.jar"
PREVIOUS_JAR="$APP_DIR/digitado-previous.jar"
PID_FILE="$APP_DIR/digitado.pid"
LOG_FILE="$APP_DIR/digitado.log"

log() { echo "[deploy] $(date -Iseconds) $*"; }

stop_app() {
  if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    log "Parando processo $(cat "$PID_FILE")..."
    kill "$(cat "$PID_FILE")"
    # Espera o graceful shutdown (server.shutdown: graceful)
    for _ in $(seq 1 30); do
      kill -0 "$(cat "$PID_FILE")" 2>/dev/null || break
      sleep 1
    done
    kill -9 "$(cat "$PID_FILE")" 2>/dev/null || true
  fi
  rm -f "$PID_FILE"
}

start_app() {
  local jar="$1"
  log "Iniciando $jar..."
  nohup java ${JAVA_OPTS:-} -jar "$jar" --spring.profiles.active=prod >>"$LOG_FILE" 2>&1 &
  echo $! >"$PID_FILE"
}

wait_healthy() {
  log "Aguardando health check em $HEALTH_URL (até $HEALTH_RETRIES x ${HEALTH_INTERVAL}s)..."
  for i in $(seq 1 "$HEALTH_RETRIES"); do
    status=$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" || true)
    if [[ "$status" == "200" ]]; then
      log "Health check OK na tentativa $i."
      return 0
    fi
    sleep "$HEALTH_INTERVAL"
  done
  return 1
}

mkdir -p "$APP_DIR"

# 1. Preserva a versão atual para rollback
if [[ -f "$CURRENT_JAR" ]]; then
  cp -f "$CURRENT_JAR" "$PREVIOUS_JAR"
  log "Versão atual preservada em $PREVIOUS_JAR."
fi

# 2. Sobe a nova versão
stop_app
cp -f "$NEW_JAR" "$CURRENT_JAR"
start_app "$CURRENT_JAR"

# 3. Monitora; 4. rollback automático em caso de falha
if wait_healthy; then
  log "✅ Deploy concluído com sucesso."
  exit 0
fi

log "❌ Nova versão não ficou saudável. Iniciando ROLLBACK automático..."
stop_app

if [[ -f "$PREVIOUS_JAR" ]]; then
  cp -f "$PREVIOUS_JAR" "$CURRENT_JAR"
  start_app "$CURRENT_JAR"
  if wait_healthy; then
    log "↩️ Rollback concluído - versão anterior restaurada e saudável."
    exit 1
  fi
  log "🔥 Rollback subiu, mas o health check da versão anterior também falhou. Intervenção manual necessária."
  exit 2
fi

log "🔥 Não há versão anterior para rollback ($PREVIOUS_JAR inexistente). Intervenção manual necessária."
exit 2
