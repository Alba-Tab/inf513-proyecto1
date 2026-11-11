#!/usr/bin/env bash
# =====================================================================
# Paso 0 del despliegue: revisa el servidor antes de arrancar la aplicación.
#
#   scripts/verificar-servidor.sh su.correo@gmail.com
#
# 1. Sistema: Java (17 o más), disco, screen/tmux/crontab.
# 2. Servicios, con el propio jar y las credenciales de config.properties:
#    la base de datos, el buzón POP3 (no borra nada) y si el SMTP deja
#    responder a un correo externo (no envía nada: corta antes de DATA).
#
# Variable opcional: CANAL11_CONFIG (por defecto <carpeta>/config.properties)
# =====================================================================
set -u
. "$(dirname "$0")/comun.sh"

DESTINO="${1:-}"
CONFIG="${CANAL11_CONFIG:-$CANAL11_DIR/config.properties}"
PROBLEMAS=0

titulo() { printf '\n=== %s\n' "$1"; }
ok() { printf '  [OK]    %s\n' "$1"; }
aviso() { printf '  [AVISO] %s\n' "$1"; }
mal() { printf '  [FALLA] %s\n' "$1"; PROBLEMAS=$((PROBLEMAS + 1)); }

if [ -z "$DESTINO" ]; then
    echo "Uso: $0 su.correo@gmail.com   (un correo externo del grupo, para probar el envío)" >&2
    exit 2
fi

titulo "Sistema"
echo "  $(uname -s) $(uname -r) $(uname -m)"
[ -f /etc/os-release ] && echo "  $(. /etc/os-release && echo "${PRETTY_NAME:-}")"
echo "  Hora del sistema: $(date '+%Y-%m-%d %H:%M %Z (%z)')"

titulo "Java (17 o superior)"
JAVA="$(buscar_java)"
JAVA_OK=0
if [ -z "$JAVA" ]; then
    mal "No hay Java. Subir un JDK 17 portable a $CANAL11_DIR/jdk (ver docs/despliegue.md)."
else
    VERSION="$("$JAVA" -version 2>&1 | head -n 1)"
    # «1.8.0_x» es Java 8; «11.0.2», «17», «21.0.1»… es la versión mayor
    MAYOR="$(echo "$VERSION" | sed -E 's/.*version "([0-9]+)(\.([0-9]+))?.*/\1 \3/' | awk '{ print ($1 == 1) ? $2 : $1 }')"
    if [ -n "$MAYOR" ] && [ "$MAYOR" -ge 17 ] 2>/dev/null; then
        ok "$VERSION ($JAVA)"
        JAVA_OK=1
    else
        mal "$VERSION es anterior a 17. Subir un JDK 17 portable a $CANAL11_DIR/jdk (ver docs/despliegue.md)."
    fi
fi

titulo "Disco"
df -Ph "$HOME" 2>/dev/null | tail -n 1 | awk '{ print "  Libre en " $6 ": " $4 " de " $2 }'
if command -v quota >/dev/null 2>&1; then
    quota -s 2>/dev/null | sed 's/^/  /' || true
fi

titulo "Herramientas para dejarla corriendo"
for h in nohup screen tmux; do
    if command -v "$h" >/dev/null 2>&1; then ok "$h"; else aviso "no hay $h"; fi
done
if command -v crontab >/dev/null 2>&1; then
    ok "crontab (sirve para que arranque sola si reinician el servidor)"
else
    aviso "no hay crontab: si reinician el servidor habrá que arrancarla a mano"
fi

titulo "Servicios (con el jar y config.properties)"
JAR="$(buscar_jar)"
if [ "$JAVA_OK" -ne 1 ]; then
    aviso "se omite: primero hace falta Java 17"
elif [ -z "$JAR" ]; then
    mal "no está canal11-pipeline.jar en $CANAL11_DIR"
elif [ ! -f "$CONFIG" ]; then
    mal "falta $CONFIG: copiar config.example.properties y completarlo (docs/despliegue.md, paso 3)"
else
    "$JAVA" -Duser.timezone=America/La_Paz -jar "$JAR" --verificar "$CONFIG" "$DESTINO"
    FALLAS_JAR=$?
    PROBLEMAS=$((PROBLEMAS + FALLAS_JAR))
fi

titulo "Resultado"
if [ "$PROBLEMAS" -eq 0 ]; then
    echo "  Todo listo para arrancar: scripts/run.sh"
else
    echo "  $PROBLEMAS punto(s) para resolver antes de arrancar (marcados con [FALLA])."
fi
exit "$PROBLEMAS"
