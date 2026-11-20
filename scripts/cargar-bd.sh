#!/usr/bin/env bash
# =====================================================================
# Carga la base de datos: script (BORRA y crea las tablas), datos de
# prueba y las 21 pruebas de integridad.
#
#   scripts/cargar-bd.sh
#
# Conexión con las variables de psql (por defecto: localhost, db_grupo14sa,
# usuario grupo14sa). psql pide la contraseña, o la toma de ~/.pgpass.
#   PGHOST, PGPORT, PGDATABASE, PGUSER
# =====================================================================
set -u
. "$(dirname "$0")/comun.sh"

export PGHOST="${PGHOST:-localhost}"
export PGDATABASE="${PGDATABASE:-db_grupo14sa}"
export PGUSER="${PGUSER:-grupo14sa}"
DB_DIR="$CANAL11_DIR/db"

for f in script_db_grupo14sa.sql datos_prueba.sql pruebas_integridad.sql; do
    if [ ! -f "$DB_DIR/$f" ]; then
        echo "Error: falta $DB_DIR/$f" >&2
        exit 1
    fi
done
if ! command -v psql >/dev/null 2>&1; then
    echo "Error: no está psql." >&2
    exit 1
fi

echo "ATENCIÓN: esto BORRA todas las tablas de $PGDATABASE en $PGHOST y carga los datos de prueba."
printf "Para seguir, escriba el nombre de la base (%s): " "$PGDATABASE"
read -r CONFIRMA
if [ "$CONFIRMA" != "$PGDATABASE" ]; then
    echo "Cancelado: no se tocó nada."
    exit 1
fi

echo "1/3 Script de la base…"
psql -q -v ON_ERROR_STOP=1 -f "$DB_DIR/script_db_grupo14sa.sql" 2>&1 | grep -v NOTICE
[ "${PIPESTATUS[0]}" -eq 0 ] || { echo "Error al correr el script." >&2; exit 1; }

echo "2/3 Datos de prueba…"
psql -q -v ON_ERROR_STOP=1 -f "$DB_DIR/datos_prueba.sql" || { echo "Error al cargar los datos." >&2; exit 1; }

echo "3/3 Pruebas de integridad…"
RESULTADO="$(psql -v ON_ERROR_STOP=1 -f "$DB_DIR/pruebas_integridad.sql" 2>&1)"
OK="$(echo "$RESULTADO" | grep -c '^ OK')"
FALLAS="$(echo "$RESULTADO" | grep -c 'FALLA')"
echo "    $OK pruebas OK, $FALLAS con FALLA (deben ser 21 y 0)."
if [ "$OK" -ne 21 ] || [ "$FALLAS" -ne 0 ]; then
    echo "$RESULTADO" | grep -E 'FALLA|ERROR' >&2
    exit 1
fi

echo "Listo. Ahora cambie los correos @canal11.test por los reales del grupo (docs/despliegue.md, paso 4)."
