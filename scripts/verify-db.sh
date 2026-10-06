#!/usr/bin/env bash
# A-01 · DB 재현·업그레이드 검증
#
# 무엇을 확인하는가
#   1) 깨끗한 DB에 전체 마이그레이션이 적용되고, 엔티티와 스키마가 일치하는가(JPA ddl-auto=validate)
#   2) 직전 버전의 데이터가 들어 있는 DB에 최신 마이그레이션을 얹어도 데이터가 보존되는가
#   3) 제출물 연결 제약이 실제로 잘못된 연결을 거절하는가
#   4) 검토 회차 제약이 실제로 잘못된 회차를 거절하는가
#
# 사용
#   bash scripts/verify-db.sh                 # 검사 전용 DB를 띄우고 전체 검증
#   BB_SKIP_COMPOSE=1 bash scripts/verify-db.sh   # 이미 떠 있는 DB(예: CI service) 사용
#   BB_TEST_DB_PORT=25433 bash scripts/verify-db.sh   # 기본 포트(25432)가 막혔을 때. 컨테이너와 테스트 설정도 이 값을 따른다
#
# 안전
#   - 개발·데모 DB(docker-compose.yml, blackbox_db)는 건드리지 않는다. 컨테이너·볼륨·포트가 모두 다르다.
#   - `down -v`를 쓰지 않는다(docs/team/WORKFLOW.md §6).
#   - 검사용 데이터베이스 bb_fresh·bb_upgrade 두 개만 매 실행 시 다시 만든다. 그 외 데이터는 손대지 않는다.
set -euo pipefail

cd "$(dirname "$0")/.."

DB_HOST=${BB_TEST_DB_HOST:-localhost}
DB_PORT=${BB_TEST_DB_PORT:-25432}
DB_USER=${BB_TEST_DB_USER:-blackbox_test}
DB_PASSWORD=${BB_TEST_DB_PASSWORD:-blackbox_test}
ADMIN_DB=${BB_TEST_ADMIN_DB:-blackbox_test}
SKIP_COMPOSE=${BB_SKIP_COMPOSE:-0}
PSQL_IMAGE=${BB_PSQL_IMAGE:-postgres:16-alpine}

FRESH_DB=bb_fresh
UPGRADE_DB=bb_upgrade

# 기대값은 파일에서 계산한다. 새 마이그레이션이 추가돼도 이 스크립트를 고칠 필요가 없다.
MIGRATION_DIR=backend/src/main/resources/db/migration
MIGRATION_COUNT=$(ls "$MIGRATION_DIR"/V*__*.sql | wc -l | tr -d ' ')
LATEST_VERSION=$(ls "$MIGRATION_DIR"/V*__*.sql | sed -E 's/.*\/V([0-9]+)__.*/\1/' | sort -n | tail -1)
PREV_VERSION=$(ls "$MIGRATION_DIR"/V*__*.sql | sed -E 's/.*\/V([0-9]+)__.*/\1/' | sort -n | tail -2 | head -1)

# 고정 UUID — 업그레이드 후 같은 행이 남아 있는지 확인하는 데 쓴다.
U1=11111111-1111-4111-8111-111111111111
P1=22222222-2222-4222-8222-222222222222
P2=22222222-2222-4222-8222-333333333333
T1=33333333-3333-4333-8333-333333333333
T2=33333333-3333-4333-8333-444444444444
D1=44444444-4444-4444-8444-444444444444
R1=55555555-5555-4555-8555-555555555555
P3=22222222-2222-4222-8222-444444444444
D2=44444444-4444-4444-8444-555555555555
D3=44444444-4444-4444-8444-666666666666
F1=66666666-6666-4666-8666-666666666666
F2=66666666-6666-4666-8666-777777777777
V1=77777777-7777-4777-8777-777777777777

step()  { printf '\n\033[1m── %s\033[0m\n' "$*"; }
run_log() { printf '   · %s\n' "$*"; }
ok()    { printf '   ✅ %s\n' "$*"; }
fail()  { printf '   ❌ %s\n' "$*"; exit 1; }

# psql 실행 방식은 환경에 따라 셋 중 하나를 고른다.
#  1) 호스트에 psql이 있으면 그것을 쓴다(CI 러너에는 기본 설치돼 있다).
#  2) 없으면 이미 떠 있는 검사 DB 컨테이너 안에서 docker exec으로 실행한다.
#  3) 둘 다 아니면 psql 이미지를 띄운다(host.docker.internal 경유).
# 2번을 3번보다 먼저 쓴다. SQL마다 새 컨테이너를 만들면 Docker Desktop에서 컨테이너가 멈춘 채 끝나지 않는 일이 반복됐다.
# exec은 새 컨테이너를 만들지 않는다.
PSQL_TIMEOUT=${BB_PSQL_TIMEOUT:-120}
TEST_DB_CONTAINER=${BB_TEST_DB_CONTAINER:-blackbox_test_db}
if command -v psql >/dev/null 2>&1; then
  PSQL_MODE=host
elif docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$TEST_DB_CONTAINER"; then
  PSQL_MODE=exec
else
  PSQL_MODE=run
fi
psql_cmd() {
  case "$PSQL_MODE" in
    host) PGPASSWORD="$DB_PASSWORD" timeout "$PSQL_TIMEOUT" psql -h "$DB_HOST" -p "$DB_PORT" "$@" ;;
    exec) timeout "$PSQL_TIMEOUT" docker exec -i -e PGPASSWORD="$DB_PASSWORD" "$TEST_DB_CONTAINER" \
            psql -h 127.0.0.1 -p 5432 "$@" ;;
    run)  timeout "$PSQL_TIMEOUT" docker run --rm -i --add-host=host.docker.internal:host-gateway \
            -e PGPASSWORD="$DB_PASSWORD" "$PSQL_IMAGE" \
            psql -h "$([ "$DB_HOST" = localhost ] && echo host.docker.internal || echo "$DB_HOST")" -p "$DB_PORT" "$@" ;;
  esac
}
psql_run() {  # psql_run <db> [psql 인자...]
  local db=$1; shift
  psql_cmd -U "$DB_USER" -d "$db" -v ON_ERROR_STOP=1 -q "$@"
}
psql_value() { # psql_value <db> <sql> → 값 한 개
  local db=$1 sql=$2
  psql_cmd -U "$DB_USER" -d "$db" -v ON_ERROR_STOP=1 -tAc "$sql" < /dev/null
}
expect_value() { # expect_value <db> <sql> <기대값> <설명>
  local actual; actual=$(psql_value "$1" "$2")
  [ "$actual" = "$3" ] && ok "$4 ($3)" || fail "$4 — 기대 $3, 실제 $actual"
}
expect_reject() { # expect_reject <db> <sql> <설명> — 제약 위반으로 거절되어야 하는 SQL
  # 0이 아닌 종료코드를 전부 '제약이 막았다'로 세면 타임아웃·접속 실패·SQL 오타까지 통과가 된다.
  # 그래서 종료코드와 stderr의 ERROR 문구를 함께 본다.
  local err rc
  err=$(psql_run "$1" -c "$2" 2>&1 >/dev/null) && rc=0 || rc=$?
  [ "$rc" = 0 ] && fail "$3 — 거절되어야 하는데 성공했다"
  [ "$rc" = 124 ] && fail "$3 — 타임아웃(${PSQL_TIMEOUT}초). 제약 위반이 아니다"
  case "$err" in
    *ERROR:*) ;;
    *) fail "$3 — 제약 위반이 아닌 이유로 실패했다: ${err:-원인 없음}" ;;
  esac
  case "$err" in
    *"violates foreign key constraint"*|*"violates check constraint"*|*"violates not-null constraint"*|*"violates unique constraint"*) ;;
    *) fail "$3 — 제약 위반 메시지가 아니다: $err" ;;
  esac
  ok "$3 (DB가 거절함)"
}
recreate_db() { # recreate_db <이름>
  psql_run "$ADMIN_DB" -c "DROP DATABASE IF EXISTS $1 WITH (FORCE)" >/dev/null
  psql_run "$ADMIN_DB" -c "CREATE DATABASE $1" >/dev/null
  ok "$1 재생성"
}
migrate() { # migrate <db> — 애플리케이션 자체 Flyway로 마이그레이션 + JPA 스키마 검증
  local db=$1
  ( cd backend && SPRING_DATASOURCE_URL="jdbc:postgresql://$DB_HOST:$DB_PORT/$db" \
      SPRING_DATASOURCE_USERNAME="$DB_USER" SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
      ./gradlew --no-daemon --console=plain -q test --tests com.blackbox.BlackboxApplicationTests --rerun )
}

# ── 0. 검사 전용 DB 기동 ────────────────────────────────────────────────
if [ "$SKIP_COMPOSE" != "1" ]; then
  step "검사 전용 PostgreSQL 기동 (포트 $DB_PORT · 개발 DB와 분리)"
  docker compose -f docker-compose.test-db.yml -p blackbox-test up -d
fi
step "DB 접속 대기 (psql 실행 방식: $PSQL_MODE)"
for i in $(seq 1 40); do
  if psql_value "$ADMIN_DB" "SELECT 1" >/dev/null 2>&1; then ok "접속 가능 ($DB_HOST:$DB_PORT)"; break; fi
  [ "$i" = 40 ] && fail "DB에 접속하지 못했다"
  sleep 2
done

# ── 1. 깨끗한 DB 전체 마이그레이션 ──────────────────────────────────────
step "1. 깨끗한 DB에 V1~V$LATEST_VERSION 전체 적용 + JPA 스키마 검증"
recreate_db "$FRESH_DB"
migrate "$FRESH_DB"
ok "Flyway 마이그레이션 + Spring 컨텍스트 기동 성공 (ddl-auto=validate 통과)"
expect_value "$FRESH_DB" "SELECT count(*) FROM flyway_schema_history WHERE success" "$MIGRATION_COUNT" "적용된 마이그레이션 수"
expect_value "$FRESH_DB" "SELECT count(*) FROM flyway_schema_history WHERE NOT success" 0 "실패한 마이그레이션 수"
expect_value "$FRESH_DB" "SELECT count(*) FROM information_schema.tables WHERE table_name IN ('deliverables','deliverable_requirements')" 2 "제출물 테이블"
expect_value "$FRESH_DB" "SELECT count(*) FROM information_schema.columns WHERE table_name='tasks' AND column_name IN ('deliverable_id','requirement_id','completion_criteria')" 3 "tasks 신규 컬럼"

# ── 2. 기존 데이터 업그레이드 ───────────────────────────────────────────
step "2. 기존 V$PREV_VERSION 데이터가 있는 DB를 V$LATEST_VERSION으로 업그레이드"
recreate_db "$UPGRADE_DB"

SPRING_FLYWAY_TARGET="$PREV_VERSION" SPRING_JPA_HIBERNATE_DDL_AUTO=none migrate "$UPGRADE_DB"
expect_value "$UPGRADE_DB" "SELECT max(version::int) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$'" "$PREV_VERSION" "업그레이드 전 스키마 버전"

psql_run "$UPGRADE_DB" <<SQL
INSERT INTO users (id, email, name, password_hash) VALUES ('$U1', 'a01@example.com', '검증계정', 'not-a-real-hash');
INSERT INTO projects (id, name, created_by) VALUES ('$P1', '기존 프로젝트', '$U1'), ('$P2', '다른 프로젝트', '$U1');
INSERT INTO project_members (project_id, user_id, role) VALUES ('$P1', '$U1', 'LEADER'), ('$P2', '$U1', 'LEADER');
INSERT INTO tasks (id, project_id, title, created_by) VALUES ('$T1', '$P1', '기존 업무', '$U1'), ('$T2', '$P2', '다른 프로젝트 업무', '$U1');
INSERT INTO file_vault (id, project_id, uploader_id, file_name, file_hash, file_size, storage_path) VALUES
  ('$F1', '$P1', '$U1', '보고서.pdf', repeat('a', 64), 10, '/tmp/f1'),
  ('$F2', '$P2', '$U1', '보고서.pdf', repeat('b', 64), 10, '/tmp/f2');
SQL
ok "V$PREV_VERSION 시점 샘플 데이터 적재 (사용자 1 · 프로젝트 2 · 업무 2 · 파일 2)"

migrate "$UPGRADE_DB"
ok "V$LATEST_VERSION 적용 + JPA 스키마 검증 통과"
expect_value "$UPGRADE_DB" "SELECT count(*) FROM tasks" 2 "기존 업무 보존"
expect_value "$UPGRADE_DB" "SELECT count(*) FROM users" 1 "기존 사용자 보존"
expect_value "$UPGRADE_DB" "SELECT count(*) FROM file_vault WHERE (id = '$F1' AND file_hash = repeat('a', 64)) OR (id = '$F2' AND file_hash = repeat('b', 64))" 2 "기존 파일 보존(행 변경 없음)"
expect_value "$UPGRADE_DB" "SELECT deliverable_id IS NULL AND requirement_id IS NULL AND completion_criteria IS NULL FROM tasks WHERE id='$T1'" t "기존 업무는 제출물 미연결 상태"

# ── 3. 제출물 연결 제약이 실제로 동작하는가 ─────────────────────────────
step "3. 제출물 연결 제약 동작 확인 (서비스 검증을 우회해도 DB가 막는가)"
psql_run "$UPGRADE_DB" <<SQL
INSERT INTO deliverables (id, project_id, title, due_date) VALUES ('$D1', '$P1', '중간 보고서', DATE '2026-10-18');
INSERT INTO deliverable_requirements (id, deliverable_id, content, required) VALUES ('$R1', '$D1', '출처 표기', TRUE);
UPDATE tasks SET deliverable_id = '$D1', requirement_id = '$R1' WHERE id = '$T1';
SQL
ok "같은 프로젝트 제출물·요구사항 연결 성공"
expect_reject "$UPGRADE_DB" "UPDATE tasks SET deliverable_id = '$D1' WHERE id = '$T2'" "타 프로젝트 업무에 제출물 연결"
expect_reject "$UPGRADE_DB" "UPDATE tasks SET deliverable_id = NULL WHERE id = '$T1'" "요구사항을 남긴 채 제출물만 해제"
expect_reject "$UPGRADE_DB" "UPDATE tasks SET requirement_id = '$R1' WHERE id = '$T2'" "제출물 없이 요구사항만 연결"
expect_reject "$UPGRADE_DB" "DELETE FROM deliverables WHERE id = '$D1'" "업무가 연결된 제출물 삭제"

psql_run "$UPGRADE_DB" -c "UPDATE tasks SET deliverable_id = NULL, requirement_id = NULL WHERE id = '$T1'" >/dev/null
psql_run "$UPGRADE_DB" -c "DELETE FROM deliverables WHERE id = '$D1'" >/dev/null
ok "연결 해제 후에는 제출물 삭제 가능"
expect_value "$UPGRADE_DB" "SELECT count(*) FROM deliverable_requirements WHERE id='$R1'" 0 "제출물 삭제 시 요구사항 연쇄 삭제"

psql_run "$UPGRADE_DB" <<SQL
INSERT INTO projects (id, name, created_by) VALUES ('$P3', '삭제 확인용 프로젝트', '$U1');
INSERT INTO deliverables (id, project_id, title, due_date) VALUES ('$D2', '$P3', '삭제될 제출물', DATE '2026-11-29');
DELETE FROM projects WHERE id = '$P3';
SQL
expect_value "$UPGRADE_DB" "SELECT count(*) FROM deliverables WHERE id='$D2'" 0 "프로젝트 삭제 시 제출물 연쇄 삭제"

# ── 4. 검토 회차 제약이 실제로 동작하는가 (K-10 6장) ──────────────────────
step "4. 검토 회차 제약 동작 확인"
psql_run "$UPGRADE_DB" <<SQL
INSERT INTO deliverables (id, project_id, title, due_date) VALUES ('$D3', '$P1', '검토 대상', DATE '2026-10-18');
INSERT INTO review_rounds (id, project_id, deliverable_id, round_no, file_id, opened_by) VALUES ('$V1', '$P1', '$D3', 1, '$F1', '$U1');
INSERT INTO review_rounds (project_id, deliverable_id, round_no, opened_by) VALUES ('$P1', '$D3', 2, '$U1');
UPDATE review_rounds SET decision = 'APPROVED', decided_by = '$U1', decided_at = NOW() WHERE id = '$V1';
SQL
ok "같은 프로젝트 파일로 회차, 파일 없는 회차, 결정 기록 성공"
expect_reject "$UPGRADE_DB" "INSERT INTO review_rounds (project_id, deliverable_id, round_no, file_id, opened_by) VALUES ('$P1', '$D3', 3, '$F2', '$U1')" "다른 프로젝트의 파일을 가리키는 회차"
expect_reject "$UPGRADE_DB" "INSERT INTO review_rounds (project_id, deliverable_id, round_no, opened_by) VALUES ('$P2', '$D3', 3, '$U1')" "제출물과 다른 프로젝트 id를 섞은 회차"
expect_reject "$UPGRADE_DB" "INSERT INTO review_rounds (project_id, deliverable_id, round_no, opened_by) VALUES ('$P1', '$D3', 1, '$U1')" "같은 제출물에 같은 회차 번호"
expect_reject "$UPGRADE_DB" "INSERT INTO review_rounds (project_id, deliverable_id, round_no, opened_by, decision) VALUES ('$P1', '$D3', 3, '$U1', 'APPROVED')" "결정자 없는 결정"
expect_reject "$UPGRADE_DB" "INSERT INTO review_rounds (project_id, deliverable_id, round_no, opened_by, decision, decided_by, decided_at) VALUES ('$P1', '$D3', 3, '$U1', 'REJECTED', '$U1', NOW())" "정해지지 않은 결정 값"
expect_reject "$UPGRADE_DB" "DELETE FROM deliverables WHERE id = '$D3'" "회차가 있는 제출물 삭제"

# 참고(실패 아님) — V19 이전부터 있던 사실이다. 아래 테이블에 행이 있으면 프로젝트 삭제가 거절된다.
step "참고 · projects를 참조하면서 연쇄 삭제가 없는 기존 테이블"
printf '   ⚠️  %s\n' "$(psql_value "$UPGRADE_DB" "SELECT string_agg(c.conrelid::regclass::text, ', ' ORDER BY c.conrelid::regclass::text) FROM pg_constraint c WHERE c.contype='f' AND c.confrelid='projects'::regclass AND c.confdeltype='a'")"
echo "       점수 스케줄러가 모든 멤버에게 contribution_scores 행을 만들기 때문에, 첫 점수 계산 뒤 프로젝트는 삭제되지 않는다(V19 무관)."

step "결과"
echo "   깨끗한 DB 전체 마이그레이션 · 기존 데이터 업그레이드 · 제약 동작 모두 통과"
echo "   검사 DB는 그대로 둔다. 필요 없으면: docker compose -f docker-compose.test-db.yml -p blackbox-test down"
