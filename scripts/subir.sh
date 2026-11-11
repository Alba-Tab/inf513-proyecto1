#!/usr/bin/env bash
# =====================================================================
# Desde la PC: compila, prueba y sube la aplicación al servidor por SSH.
#
#   scripts/subir.sh usuario@www.tecnoweb.org.bo [carpeta_remota]
#   scripts/subir.sh --simular usuario@www.tecnoweb.org.bo   (muestra los comandos sin ejecutarlos)
#
# Sube: canal11-pipeline.jar, scripts/, db/, docs/despliegue.md y la plantilla
# config.example.properties. NUNCA sube config.properties (tiene las
# contraseñas): ese archivo se crea en el servidor (docs/despliegue.md).
# La carpeta remota por defecto es «canal11» (en el home del usuario).
# =====================================================================
set -u
. "$(dirname "$0")/comun.sh"

SIMULAR=0
if [ "${1:-}" = "--simular" ]; then
    SIMULAR=1
    shift
fi
DESTINO="${1:-}"
CARPETA="${2:-canal11}"
if [ -z "$DESTINO" ]; then
    echo "Uso: $0 [--simular] usuario@www.tecnoweb.org.bo [carpeta_remota]" >&2
    exit 2
fi

correr() {
    echo "+ $*"
    [ "$SIMULAR" -eq 1 ] || "$@" || { echo "Error en: $*" >&2; exit 1; }
}

cd "$CANAL11_DIR" || exit 1
correr ./mvnw -q -B verify
correr ssh "$DESTINO" "mkdir -p $CARPETA/scripts $CARPETA/db $CARPETA/logs && chmod 700 $CARPETA"
correr scp target/canal11-pipeline.jar config.example.properties docs/despliegue.md "$DESTINO:$CARPETA/"
correr scp scripts/comun.sh scripts/run.sh scripts/stop.sh scripts/estado.sh scripts/cargar-bd.sh \
    scripts/verificar-servidor.sh "$DESTINO:$CARPETA/scripts/"
correr scp db/script_db_grupo14sa.sql db/datos_prueba.sql db/consultas_negocio.sql db/pruebas_integridad.sql \
    db/README.md "$DESTINO:$CARPETA/db/"
correr ssh "$DESTINO" "chmod +x $CARPETA/scripts/*.sh && ls -l $CARPETA"
echo "Subido a $DESTINO:$CARPETA. Para aplicar la versión nueva: ssh $DESTINO \"cd $CARPETA && scripts/stop.sh && scripts/run.sh\""
