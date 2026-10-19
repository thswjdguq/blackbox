#!/bin/bash
# 서버 자동 갱신. 서버(Amazon Linux)에서 돈다. systemd 타이머가 정해진 시각과 서버가 켜진 직후에 부른다.
# 손으로 돌려도 된다: bash ~/blackbox/scripts/ec2-update.sh   기록: journalctl -u blackbox-update
#   1. 배포 설정을 받는다(.env의 CONFIG_BRANCH, 기본 main). 이미지와 달리 설정은 늘 이 브랜치에서 받는다
#   2. .env의 IMAGE_REPO가 있으면 레지스트리에서 IMAGE_TAG의 이미지를 받는다. 커밋이 붙은 태그로 고정해 두면 바뀌는 것이 없다
#   3. 바뀐 것이 있을 때만 DB를 백업하고 띄운 뒤 상태를 본다. 상태가 나쁘면 이전 이미지로 되돌린다
# 절차는 docs/deploy-aws.md 7장
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
exec 9>/tmp/blackbox-update.lock
flock -n 9 || { echo "다른 갱신이 돌고 있어 끝낸다"; exit 0; }

log() { echo "[갱신] $*"; }
envval() { grep -E "^$1=" .env 2>/dev/null | tail -1 | cut -d= -f2-; }
# -f 없이 부르는 docker compose가 배포용 설정을 읽게 한다. .env에 적었으면(엑스포용 override를 붙인 서버 등) 그 값을 쓴다
COMPOSE_FILE=$(envval COMPOSE_FILE); export COMPOSE_FILE=${COMPOSE_FILE:-docker-compose.yml:docker-compose.prod.yml}
CONFIG_BRANCH=$(envval CONFIG_BRANCH); CONFIG_BRANCH=${CONFIG_BRANCH:-main}
IMAGE_REPO=$(envval IMAGE_REPO)
IMAGE_TAG=$(envval IMAGE_TAG); IMAGE_TAG=${IMAGE_TAG:-latest}
SITE_URL=$(envval SITE_URL); SITE_URL=${SITE_URL:-https://localhost}   # 상태 확인에 쓰는 nginx 주소. 서버 안에서 본다
BACKUP_DIR=$(envval BACKUP_DIR); BACKUP_DIR=${BACKUP_DIR:-$HOME/blackbox-backups}
SERVICES="backend frontend nginx"
FAILED="$BACKUP_DIR/.failed-images"   # 교체했다가 상태가 나빠 되돌린 이미지. 같은 이미지가 다시 오면 건너뛴다

# ── 1. 배포 설정 ─────────────────────────────────────────────
prev_branch=$(git rev-parse --abbrev-ref HEAD); prev_sha=$(git rev-parse HEAD)
# 받은 설정이 올바르지 않으면 받기 전으로 돌려놓는다. 디스크의 설정 파일이 옛 것으로 남아 다음 손작업을 깨뜨리지 않게 한다
restore() {
  if [ "$prev_branch" = HEAD ]; then git checkout -q "$prev_sha"; else git checkout -q "$prev_branch" && git reset -q --keep "$prev_sha"; fi
  log "설정을 받기 전으로 돌려놓았다: $(git rev-parse --abbrev-ref HEAD) $(git rev-parse --short HEAD)"
}
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  log "서버 저장소에 고친 파일이 있어 설정을 받지 않는다: $(git status --porcelain --untracked-files=no | tr '\n' ' ')"
elif git fetch -q origin "$CONFIG_BRANCH"; then
  if [ "$(git rev-parse --abbrev-ref HEAD)" != "$CONFIG_BRANCH" ]; then
    if git checkout -q "$CONFIG_BRANCH" 2>/dev/null || git checkout -q -b "$CONFIG_BRANCH" "origin/$CONFIG_BRANCH"; then
      log "설정 브랜치를 $CONFIG_BRANCH(으)로 바꿨다"
    else
      log "설정 브랜치($CONFIG_BRANCH)로 바꾸지 못했다"
    fi
  fi
  git merge -q --ff-only "origin/$CONFIG_BRANCH" || log "설정을 빨리 감기로 받을 수 없어 그대로 둔다"
else
  log "저장소를 받지 못해 설정은 그대로 둔다"
fi
log "설정: $(git rev-parse --abbrev-ref HEAD) $(git rev-parse --short HEAD) / 이미지: ${IMAGE_REPO}blackbox-*:$IMAGE_TAG"
if ! docker compose config -q; then log "compose 설정이 올바르지 않아 아무것도 바꾸지 않는다"; restore; exit 1; fi
if ! docker compose config --images | grep -qx "${IMAGE_REPO}blackbox-backend:$IMAGE_TAG"; then
  log "compose가 고른 이미지가 .env와 다르다(배포용 설정이 아닌 브랜치를 받았는가). 아무것도 바꾸지 않는다"; restore; exit 1
fi

# ── 2. 이미지 ────────────────────────────────────────────────
running() { local c; c=$(docker compose ps -q "$1" 2>/dev/null); [ -n "$c" ] && docker inspect -f '{{.Image}}' "$c"; }
# 받기 전에 지금 이미지에 이름을 붙여 둔다. 같은 태그로 새 이미지를 받으면 옛 이미지는 이름을 잃어 지워질 수 있다
declare -A before
for s in $SERVICES; do
  before[$s]=$(running "$s")
  [ -z "${before[$s]}" ] && continue
  if docker tag "${before[$s]}" "blackbox-$s:previous" 2>/dev/null \
     && [ "$(docker image inspect -f '{{.Id}}' "blackbox-$s:previous")" = "${before[$s]}" ]; then
    log "되돌릴 이미지: $s ${before[$s]:7:12}"
  else
    before[$s]=""; log "$s의 지금 이미지에 이름을 붙이지 못했다. 실패해도 $s는 되돌리지 못한다"
  fi
done
if [ -n "$IMAGE_REPO" ]; then
  # 일부만 받고 실패하면 서비스마다 다른 커밋이 섞여 뜰 수 있다. 다 받지 못했으면 이름을 지금 이미지로 되돌리고 바꾸지 않는다
  if ! out=$(docker compose pull -q $SERVICES 2>&1); then
    for s in $SERVICES; do [ -n "${before[$s]}" ] && docker tag "blackbox-$s:previous" "${IMAGE_REPO}blackbox-$s:$IMAGE_TAG"; done
    log "이미지를 다 받지 못해 바꾸지 않는다: $(echo "$out" | grep -v -E 'Pull(ing|ed)' | tail -3 | tr '\n' ' ')"
    exit 1
  fi
fi

# ── 3. 띄우기 ────────────────────────────────────────────────
changed=""; declare -A want
for s in $SERVICES; do
  want[$s]=$(docker image inspect -f '{{.Id}}' "${IMAGE_REPO}blackbox-$s:$IMAGE_TAG" 2>/dev/null)
  [ -z "${want[$s]}" ] || [ "${want[$s]}" = "${before[$s]}" ] && continue
  if grep -qx "$s ${want[$s]}" "$FAILED" 2>/dev/null; then
    log "$s: 지난번에 상태가 나빠 되돌린 이미지(${want[$s]:7:12})라 건너뛴다. 새 커밋의 이미지가 오면 다시 시도한다"
    [ -n "${before[$s]}" ] && docker tag "blackbox-$s:previous" "${IMAGE_REPO}blackbox-$s:$IMAGE_TAG"
  else
    changed="$changed $s"
  fi
done
ids_before=$(docker compose ps -q | sort)
if [ -n "$changed" ] && [ -n "$(docker compose ps -q db)" ]; then
  mkdir -p "$BACKUP_DIR"
  f="$BACKUP_DIR/auto-$(date +%Y%m%d-%H%M%S).dump"
  if docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$f"; then
    log "새 이미지:$changed. 바꾸기 전에 DB를 백업했다: $f"
    ls -1t "$BACKUP_DIR"/auto-*.dump 2>/dev/null | tail -n +11 | xargs -r rm --   # 자동 백업은 최근 10개만 둔다
  else
    rm -f "$f"; log "DB를 백업하지 못해 바꾸지 않는다"; exit 1
  fi
fi
docker compose up -d --remove-orphans 2>&1 | grep -v -E "Running$" || true
[ "$ids_before" = "$(docker compose ps -q | sort)" ] && { log "바뀐 것이 없다"; exit 0; }
# nginx는 backend·frontend의 주소를 뜰 때 한 번만 찾는다. 그쪽만 새로 떴으면 IP가 바뀌었을 수 있으니 설정을 다시 읽게 한다(끊김 없음)
reload_nginx() { docker compose exec -T nginx nginx -s reload >/dev/null 2>&1 || log "nginx 설정을 다시 읽지 못했다(떠 있지 않은 서비스가 있으면 그 이름을 찾지 못한다). nginx는 앞 설정으로 계속 돈다"; }
reload_nginx

# 네 서비스가 모두 떠 있고(재시작 반복 아님), nginx를 거쳐 서버(로그인 전 401)와 화면(/login 200)이 모두 답해야 정상이다.
# 나쁘면 요청마다 5초 제한까지 기다리므로 최대 약 5분 본다
up_all() { [ "$(docker compose ps --status running -q db backend frontend nginx | wc -l)" = 4 ]; }
healthy() {
  for _ in $(seq 1 30); do
    up_all && [ "$(curl -sk -m 5 -o /dev/null -w '%{http_code}' "$SITE_URL/api/auth/profile")" = 401 ] \
      && [ "$(curl -sk -m 5 -o /dev/null -w '%{http_code}' "$SITE_URL/login")" = 200 ] && return 0
    sleep 5
  done
  return 1
}
if healthy; then
  log "새로 띄웠고 상태가 정상이다"
  docker image prune -f >/dev/null   # 이름 없는 옛 이미지를 지운다. 바로 앞 이미지는 blackbox-*:previous로 남는다
  exit 0
fi
log "상태 확인 실패(서비스 $(docker compose ps --status running -q | wc -l)/4개 실행 중, API $(curl -sk -m 5 -o /dev/null -w '%{http_code}' "$SITE_URL/api/auth/profile"), 화면 $(curl -sk -m 5 -o /dev/null -w '%{http_code}' "$SITE_URL/login")). 이전 이미지로 되돌린다:$changed"
mkdir -p "$BACKUP_DIR"; for s in $changed; do echo "$s ${want[$s]}" >> "$FAILED"; done
for s in $changed; do
  [ -n "${before[$s]}" ] && docker tag "blackbox-$s:previous" "${IMAGE_REPO}blackbox-$s:$IMAGE_TAG" && log "$s → ${before[$s]:7:12}"
done
docker compose up -d 2>&1 | grep -v -E "Running$" || true
reload_nginx
if healthy; then log "되돌렸고 상태가 정상이다. 새 이미지는 쓰지 않았다"; else log "되돌린 뒤에도 상태가 나쁘다. 손으로 확인해야 한다"; fi
exit 1
