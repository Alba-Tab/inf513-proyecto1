#!/usr/bin/env bash
# =====================================================================
# Arranca canal11-pipeline en segundo plano; sigue corriendo al cerrar el SSH.
#
#   scripts/run.sh                 arranca (o avisa si ya está corriendo)
#   scripts/run.sh --si-no-corre   para crontab: arranca solo si no está corriendo, sin mensajes
#
# Variables opcionales:
#   CANAL11_CONFIG   archivo de configuración (por defecto: <carpeta>/config.properties)
#   CANAL11_MEMORIA  memoria máxima de la JVM (por defecto: 256m)
#   JAVA_HOME        JDK a usar (si no, <carpeta>/jdk o el java del sistema)
# =====================================================================
set -u
. "$(dirname "$0")/comun.sh"

SILENCIOSO=0
[ "${1:-}" = "--si-no-corre" ] && SILENCIOSO=1

decir() { [ "$SILENCIOSO" -eq 1 ] || echo "$@"; }

PID="$(pid_activo)"
if [ -n "$PID" ]; then
    decir "canal11-pipeline ya está corriendo (PID $PID)."
    exit 0
fi

JAR="$(buscar_jar)"
JAVA="$(buscar_java)"
CONFIG="${CANAL11_CONFIG:-$CANAL11_DIR/config.properties}"

if [ -z "$JAR" ]; then
    echo "Error: no se encontró canal11-pipeline.jar en $CANAL11_DIR (ni en target/)." >&2
    exit 1
fi
if [ -z "$JAVA" ]; then
    echo "Error: no hay Java. Instale un JDK 17 o súbalo a $CANAL11_DIR/jdk (ver docs/despliegue.md)." >&2
    exit 1
fi
if [ ! -f "$CONFIG" ]; then
    echo "Error: falta $CONFIG. Copie config.example.properties como config.properties y complételo." >&2
    exit 1
fi
if [ "$(permisos "$CONFIG")" != "600" ]; then
    decir "Aviso: $CONFIG tiene contraseñas; conviene: chmod 600 $CONFIG"
fi

cd "$CANAL11_DIR" || exit 1
mkdir -p logs
# La salida de consola de la corrida anterior se conserva una vez
[ -f "$LOG_CONSOLA" ] && mv -f "$LOG_CONSOLA" "$LOG_CONSOLA.anterior"

nohup "$JAVA" -Xmx"${CANAL11_MEMORIA:-256m}" -Duser.timezone=America/La_Paz -Dfile.encoding=UTF-8 \
    -jar "$JAR" "$CONFIG" >"$LOG_CONSOLA" 2>&1 </dev/null &
echo $! >"$PID_FILE"

sleep 3
PID="$(pid_activo)"
if [ -z "$PID" ]; then
    echo "Error: canal11-pipeline no arrancó. Últimas líneas de $LOG_CONSOLA:" >&2
    tail -n 15 "$LOG_CONSOLA" >&2
    rm -f "$PID_FILE"
    exit 1
fi
decir "canal11-pipeline iniciado (PID $PID) con $("$JAVA" -version 2>&1 | head -n 1)."
decir "Log: $LOG_APP    Estado: scripts/estado.sh    Detener: scripts/stop.sh"
