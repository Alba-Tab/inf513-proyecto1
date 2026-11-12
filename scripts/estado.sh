#!/usr/bin/env bash
# =====================================================================
# Muestra si canal11-pipeline está corriendo, los últimos correos atendidos
# (access.log) y las últimas fallas (error.log).
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

for LOG in "$LOG_ACCESO" "$LOG_ERROR"; do
    if [ -f "$LOG" ]; then
        echo "--- Últimas líneas de $LOG"
        tail -n "${1:-20}" "$LOG"
    else
        echo "--- Todavía no hay $LOG."
    fi
done
echo "(El log completo está en $LOG_APP.)"
