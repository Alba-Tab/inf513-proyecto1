#!/usr/bin/env bash
# =====================================================================
# Detiene canal11-pipeline ordenadamente: termina el correo en curso, envía las
# respuestas pendientes y escribe «Aplicación detenida» en el log.
# Si en 45 segundos no terminó, la fuerza a terminar.
# =====================================================================
set -u
. "$(dirname "$0")/comun.sh"

PID="$(pid_activo)"
if [ -z "$PID" ]; then
    echo "canal11-pipeline no está corriendo."
    rm -f "$PID_FILE"
    exit 0
fi

echo "Deteniendo canal11-pipeline (PID $PID)…"
kill -TERM "$PID"
for _ in $(seq 1 45); do
    if ! kill -0 "$PID" 2>/dev/null; then
        rm -f "$PID_FILE"
        echo "Detenido."
        exit 0
    fi
    sleep 1
done

echo "Aviso: no terminó en 45 s; se fuerza (kill -9). Revise $LOG_APP." >&2
kill -KILL "$PID" 2>/dev/null
rm -f "$PID_FILE"
exit 1
