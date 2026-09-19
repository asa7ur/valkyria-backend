#!/usr/bin/env bash
# Copia diaria de la base de datos y de las imágenes subidas. La lanza cron cada noche.
set -euo pipefail
cd "$(dirname "$0")"

DEST="$HOME/backups"
KEEP_DAYS=14
STAMP=$(date +%F_%H%M)

mkdir -p "$DEST"
chmod 700 "$DEST"
umask 077

docker exec db sh -c 'exec mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" --single-transaction --routines --triggers "$MARIADB_DATABASE"' \
    | gzip > "$DEST/db-$STAMP.sql.gz"
# Un dump cortado a medias no termina con esta línea
zcat "$DEST/db-$STAMP.sql.gz" | tail -n 1 | grep -q 'Dump completed' || { echo "ERROR: dump incompleto"; exit 1; }

tar czf "$DEST/uploads-$STAMP.tar.gz" -C data uploads

# Borrar las copias de más de KEEP_DAYS días
find "$DEST" -name 'db-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
find "$DEST" -name 'uploads-*.tar.gz' -mtime +"$KEEP_DAYS" -delete

echo "$(date '+%F %T') OK db-$STAMP.sql.gz uploads-$STAMP.tar.gz"
