#!/usr/bin/env bash
# =====================================================================
# Funciones comunes de los scripts de canal11-pipeline. No se ejecuta solo:
# lo incluyen los demás con «. "$(dirname "$0")/comun.sh"».
# Compatible con bash 3.2 (macOS) y bash 4/5 (Linux).
# =====================================================================

# Carpeta de la aplicación: en el servidor, la que tiene canal11-pipeline.jar;
# en la PC, la raíz del repositorio (el jar está en target/).
CANAL11_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$CANAL11_DIR/canal11.pid"
LOG_APP="$CANAL11_DIR/logs/canal11.0.log"
LOG_CONSOLA="$CANAL11_DIR/logs/consola.log"

# Jar: el del servidor o, si no está, el compilado en la PC.
buscar_jar() {
    if [ -f "$CANAL11_DIR/canal11-pipeline.jar" ]; then
        echo "$CANAL11_DIR/canal11-pipeline.jar"
    elif [ -f "$CANAL11_DIR/target/canal11-pipeline.jar" ]; then
        echo "$CANAL11_DIR/target/canal11-pipeline.jar"
    fi
}

# Java: JAVA_HOME, un JDK portable en <carpeta>/jdk, o el del sistema.
buscar_java() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        echo "$JAVA_HOME/bin/java"
    elif [ -x "$CANAL11_DIR/jdk/bin/java" ]; then
        echo "$CANAL11_DIR/jdk/bin/java"
    elif command -v java >/dev/null 2>&1; then
        command -v java
    fi
}

# PID de la aplicación si está corriendo; vacío si no.
pid_activo() {
    if [ -f "$PID_FILE" ]; then
        local pid
        pid="$(cat "$PID_FILE" 2>/dev/null)"
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            echo "$pid"
            return
        fi
    fi
}

# Permisos de un archivo en octal (Linux y macOS).
permisos() {
    stat -c %a "$1" 2>/dev/null || stat -f %Lp "$1" 2>/dev/null
}
