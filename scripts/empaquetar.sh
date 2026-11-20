#!/usr/bin/env bash
# =====================================================================
# Arma el comprimido de entrega que pide la consigna:
#   2026-2_INF513-P1_grupo14sa.zip
# con el proyecto completo tal como está en HEAD (lo versionado en git:
# sin config.properties, target/ ni logs) y, si se indican, otros archivos
# (por ejemplo, el documento técnico), que quedan en la raíz del zip.
#
#   scripts/empaquetar.sh [documento.docx ...]
#
# El zip queda en la carpeta que contiene al repositorio, o en
# CANAL11_ZIP_DIR si está definida.
# =====================================================================
set -euo pipefail
. "$(dirname "$0")/comun.sh"

NOMBRE="2026-2_INF513-P1_grupo14sa"
DESTINO="${CANAL11_ZIP_DIR:-$(cd "$CANAL11_DIR/.." && pwd)}/$NOMBRE.zip"

cd "$CANAL11_DIR"
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo "Aviso: hay cambios sin commit; el zip lleva solo lo que está en HEAD." >&2
fi

EXTRA=()
for f in "$@"; do
    if [ ! -f "$f" ]; then
        echo "No existe: $f" >&2
        exit 1
    fi
    EXTRA+=("--add-file=$f")
done

rm -f "$DESTINO"
git archive --format=zip --prefix="$NOMBRE/" ${EXTRA[@]+"${EXTRA[@]}"} -o "$DESTINO" HEAD
echo "Listo: $DESTINO ($(du -h "$DESTINO" | cut -f1), commit $(git rev-parse --short HEAD))"
