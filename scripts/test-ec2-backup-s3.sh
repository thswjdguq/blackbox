#!/bin/bash
# scripts/ec2-backup-s3.sh의 회귀 검사. 실제 Docker·AWS 대신 가짜 docker·aws 명령으로 돌린다(서버·S3·운영 자료에 닿지 않는다).
# 실행: bash scripts/test-ec2-backup-s3.sh   CI의 backend 작업에서도 돈다
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

work=$(mktemp -d) || exit 1
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/repo/scripts" "$work/bin" "$work/tmp" "$work/bucket"
# 서버의 잠금 파일을 건드리지 않도록 잠금 경로만 바꾼 사본을 검사한다
sed "s#/tmp/blackbox-update.lock#$work/lock#" scripts/ec2-backup-s3.sh > "$work/repo/scripts/ec2-backup-s3.sh"
grep -q "$work/lock" "$work/repo/scripts/ec2-backup-s3.sh" && ! grep -q "/tmp/blackbox-update.lock" "$work/repo/scripts/ec2-backup-s3.sh" \
  || { echo "잠금 경로를 바꾸지 못했다. ec2-backup-s3.sh의 잠금 경로가 바뀌었으면 이 검사도 맞춘다"; exit 1; }

# 가짜 docker: compose ps는 DOWN에 적은 서비스만 꺼진 것으로, compose exec는 FAIL에 적은 서비스만 실패로 답한다
cat > "$work/bin/docker" <<'EOF'
#!/bin/bash
if [ "$2" = ps ]; then [ "${DOWN:-}" = "${!#}" ] || echo "id-${!#}"; exit 0; fi
if [ "$2" = exec ]; then [ "${FAIL:-}" = "$4" ] && exit 1; echo "data-$4"; exit 0; fi
exit 1
EOF
# 가짜 aws: s3 cp 원본 대상. 대상 파일 이름이 FAIL_UPLOAD이면 실패한다. 올린 순서와 권한을 기록하고 가짜 버킷에 복사한다
cat > "$work/bin/aws" <<'EOF'
#!/bin/bash
[ "$1 $2" = "s3 cp" ] || exit 1
name=$(basename "$4")
[ "${FAIL_UPLOAD:-}" = "$name" ] && exit 1
echo "$name $(stat -c %a "$3") $(stat -c %a "$(dirname "$3")") $4" >> "$UPLOADS"
cp "$3" "$BUCKET/$name"
EOF
chmod +x "$work/bin/docker" "$work/bin/aws"

failures=0
check() { if [ "$2" = "$3" ]; then echo "  통과: $1"; else echo "  실패: $1 (기대 '$3', 실제 '$2')"; failures=$((failures + 1)); fi; }
run() {   # 환경 변수=값 ... 을 받아 백업을 한 번 돌리고, 종료 코드를 rc에 남긴다
  : > "$work/uploads"; rm -f "$work/bucket"/*
  (cd "$work/repo" && env "PATH=$work/bin:$PATH" "TMPDIR=$work/tmp" "UPLOADS=$work/uploads" "BUCKET=$work/bucket" "$@" \
    bash scripts/ec2-backup-s3.sh > "$work/out" 2>&1)
  rc=$?
}
uploaded() { cut -d' ' -f1 "$work/uploads" | paste -sd' '; }

echo "1) BACKUP_S3_URI가 비어 있으면 건너뛴다"
: > "$work/repo/.env"; run
check "종료 0" "$rc" 0; check "올린 것 없음" "$(uploaded)" ""

echo "BACKUP_S3_URI=s3://blackbox-backups-bucket/backups/" > "$work/repo/.env"

echo "2) 정상: 두 파일 뒤에 MANIFEST.sha256을 마지막으로 올린다"
run
check "종료 0" "$rc" 0
check "올린 순서" "$(uploaded)" "db.dump uploads.tar.gz MANIFEST.sha256"
check "MANIFEST로 두 파일 확인" "$(cd "$work/bucket" && sha256sum -c --quiet MANIFEST.sha256 >/dev/null 2>&1 && echo ok)" ok
check "임시 사본 권한(파일 600, 폴더 700)" "$(cut -d' ' -f2,3 "$work/uploads" | sort -u)" "600 700"
# 설정값 끝의 /는 지우고, 세 파일은 같은 <날짜-시각> 폴더 하나에 들어간다(backups//… 나 시각 폴더 누락이면 실패)
check "세 파일이 한 시각 폴더에" "$(cut -d' ' -f4 "$work/uploads" | sed -E 's#/[^/]+$##' | sort -u | grep -cE '^s3://blackbox-backups-bucket/backups/[0-9]{8}-[0-9]{6}$')" 1
check "폴더가 하나뿐" "$(cut -d' ' -f4 "$work/uploads" | sed -E 's#/[^/]+$##' | sort -u | wc -l)" 1
check "서버에 임시 사본이 남지 않음" "$(ls "$work/tmp" | wc -l)" 0

echo "3) DB 덤프 실패: 아무것도 올리지 않는다"
run FAIL=db
check "종료 1" "$rc" 1; check "올린 것 없음" "$(uploaded)" ""

echo "4) 두 번째 파일(uploads.tar.gz) 업로드 실패: db.dump만 남고 MANIFEST는 없다"
run FAIL_UPLOAD=uploads.tar.gz
check "종료 1" "$rc" 1; check "올린 것" "$(uploaded)" "db.dump"
check "완성되지 않았다고 기록" "$(grep -c 'MANIFEST.sha256이 없어 완성된 백업이 아니다' "$work/out")" 1

echo "5) MANIFEST 업로드 실패: 두 파일만 남고 완성된 백업으로 보이지 않는다"
run FAIL_UPLOAD=MANIFEST.sha256
check "종료 1" "$rc" 1; check "올린 것" "$(uploaded)" "db.dump uploads.tar.gz"

echo "6) DB가 떠 있지 않으면 백업하지 않는다"
run DOWN=db
check "종료 1" "$rc" 1; check "올린 것 없음" "$(uploaded)" ""

check "모든 경우에 임시 사본이 남지 않음" "$(ls "$work/tmp" | wc -l)" 0
[ "$failures" = 0 ] && { echo "S3 백업 스크립트 검사 통과"; exit 0; }
echo "S3 백업 스크립트 검사 실패 $failures건"; exit 1
