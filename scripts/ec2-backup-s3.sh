#!/bin/bash
# 서버 백업(DB 덤프와 업로드 볼륨)을 S3로 보낸다. 서버(Amazon Linux)에서 돈다. blackbox-backup.timer가 부른다.
# 손으로 돌려도 된다(서버를 끄기 직전 등): bash ~/blackbox/scripts/ec2-backup-s3.sh   기록: journalctl -u blackbox-backup
# .env의 BACKUP_S3_URI(예: s3://blackbox-backups-bucket/backups)가 비어 있으면 아무것도 하지 않는다.
# 서버의 EC2 역할로 올린다(액세스 키 없음). 역할에는 삭제 권한이 없고, 오래된 백업은 버킷의 수명 주기 규칙이 지운다.
# 절차는 docs/deploy-aws.md 8장
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
umask 077   # 임시 사본에는 계정 정보(비밀번호 해시)가 있다. 서버 사용자만 읽게 한다

log() { echo "[S3 백업] $*"; }
envval() { grep -E "^$1=" .env 2>/dev/null | tail -1 | cut -d= -f2-; }
COMPOSE_FILE=$(envval COMPOSE_FILE); export COMPOSE_FILE=${COMPOSE_FILE:-docker-compose.yml:docker-compose.prod.yml}
DEST=$(envval BACKUP_S3_URI); DEST=${DEST%/}
[ -n "$DEST" ] || { log "BACKUP_S3_URI가 비어 있어 건너뛴다"; exit 0; }

# 자동 갱신(ec2-update.sh)과 같은 잠금을 쓴다. 이미지를 바꾸는 중에는 끝날 때까지 기다린다
exec 9>/tmp/blackbox-update.lock
flock -w 900 9 || { log "자동 갱신이 15분 넘게 끝나지 않아 이번 백업은 건너뛴다"; exit 1; }

for s in db backend; do
  [ -n "$(docker compose ps -q --status running "$s")" ] || { log "$s가 떠 있지 않아 백업하지 못했다"; exit 1; }
done

# 반쯤 쓴 파일이 S3에 남지 않도록(서버는 지울 수 없다) 서버에서 끝까지 만든 뒤 올린다
tmp=$(mktemp -d) || exit 1
trap 'rm -rf "$tmp"' EXIT
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$tmp/db.dump" \
  || { log "DB를 덤프하지 못했다"; exit 1; }
docker compose exec -T backend tar -C /data/uploads -czf - . > "$tmp/uploads.tar.gz" \
  || { log "업로드 폴더를 압축하지 못했다"; exit 1; }

prefix="$DEST/$(date +%Y%m%d-%H%M%S)"
for f in db.dump uploads.tar.gz; do
  aws s3 cp "$tmp/$f" "$prefix/$f" --only-show-errors || { log "$f를 올리지 못했다($prefix)"; exit 1; }
done
log "올렸다: $prefix (DB $(du -h "$tmp/db.dump" | cut -f1), 업로드 $(du -h "$tmp/uploads.tar.gz" | cut -f1))"
