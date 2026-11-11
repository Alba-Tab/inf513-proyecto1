#!/usr/bin/env bash
# =====================================================================
# Muestra si canal11-pipeline está corriendo y las últimas líneas del log.
#   scripts/estado.sh [cantidad_de_lineas]   (por defecto 20)
# =====================================================================
set -u
. "$(dirname "$0")/comun.sh"

PID="$(pid_activo)"
if [ -n "$PID" ]; then
    echo "canal11-pipeline está corriendo (PID $PID, hace $(ps -o etime= -p "$PID" | tr -d ' '))."
else
    echo "canal11-pipeline NO está corriendo."
fi

if [ -f "$LOG_APP" ]; then
    echo "--- Últimas líneas de $LOG_APP"
    tail -n "${1:-20}" "$LOG_APP"
else
    echo "Todavía no hay log en $LOG_APP."
fi
