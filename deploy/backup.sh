#!/bin/sh
# 배포 DB를 하루 1회 백업한다(ARCHITECTURE 10절). cron 등록은 deploy/README.md.
# 백업은 저장소 밖(기본 ~/passform-backups)에 두고 7일이 지난 파일은 지운다.
set -eu

cd "$(dirname "$0")"
backup_dir="${BACKUP_DIR:-$HOME/passform-backups}"
umask 077
mkdir -p "$backup_dir"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
tmp="$backup_dir/.passform-$stamp.dump.part"
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$tmp"
mv "$tmp" "$backup_dir/passform-$stamp.dump"

find "$backup_dir" -name 'passform-*.dump' -mtime +7 -delete
find "$backup_dir" -name '.passform-*.part' -mtime +1 -delete
