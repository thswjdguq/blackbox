# Garbage Collection — 불일치·위반·드리프트 방지 가이드

## 이 파일의 목적

개발이 진행되면 **기획서에 적힌 것과 실제 코드가 어긋나기 시작한다.** 이 파일은 주기적으로 (매 스프린트, 또는 PR 머지 전) 실행하여 **불일치를 조기에 발견하고 교정**하는 체크리스트이다.

AI 어시스턴트에게 "GC 돌려줘" 또는 "드리프트 체크해줘"라고 지시하면 이 파일을 기준으로 점검한다.

---

## 1. 불변 규칙 (Invariants) — 절대 위반 불가

아래 규칙이 깨진 코드는 **즉시 수정**해야 한다. PR 머지 전 반드시 확인.

### INV-01: activity_logs 기록 누락
```
규칙: 사용자 행동을 유발하는 모든 Service 메서드는
      반드시 ActivityLogService.log()를 호출해야 한다.

검증 방법:
  1. 다음 메서드들에 activity_logs INSERT가 있는지 확인:
     - TaskService.create(), updateStatus(), delete()
     - MeetingService.create(), checkin(), update()
     - VaultService.upload()
     - (확장 1) GitHubSyncService, DriveSyncService
  2. 새 Service 메서드 추가 시 → 활동 로그 호출 포함 여부 확인

위반 시 영향: Score Engine이 해당 행동을 반영하지 못함 → 기여도 왜곡
```

### INV-02: file_vault 불변성
```
규칙: file_vault 테이블에 UPDATE / DELETE 쿼리가 존재하면 안 된다.

검증 방법:
  1. 전체 코드에서 file_vault 대상 UPDATE/DELETE 검색
     grep -rn "file_vault" --include="*.java" | grep -i "update\|delete\|remove"
  2. Flyway 마이그레이션에 file_vault ALTER 존재 여부 확인
  3. DB 트리거(prevent_vault_modification) 존재 확인:
     SELECT tgname FROM pg_trigger WHERE tgrelid = 'file_vault'::regclass;

위반 시 영향: 위변조 방지 주장 자체가 무너짐 → 프로젝트 핵심 차별점 상실
```

### INV-03: 외부 API 쓰기 권한 요청 금지
```
규칙: GitHub App 권한에 write가 포함되면 안 된다.
      Google OAuth scope에 readonly가 아닌 scope가 있으면 안 된다.

검증 방법:
  1. GitHub App 설정에서 permissions 확인:
     contents: read (NOT read+write)
     pull_requests: read
     issues: read
  2. Google OAuth scope 확인:
     grep -rn "scope" --include="*.java" --include="*.yml"
     → drive.readonly만 허용, drive (full access) 금지

위반 시 영향: "읽기 전용" 설계 철학 위반 → 개인정보 방어 논리 붕괴
```

### INV-04: 교수 계정 데이터 수정 불가
```
규칙: role=OBSERVER인 사용자가 호출할 수 있는 API 중
      데이터를 변경하는 것은 가중치 조정(PUT /weights)뿐이어야 한다.

검증 방법:
  1. ProjectAccessChecker에서 OBSERVER 허용 범위 확인
  2. OBSERVER가 POST/PATCH/DELETE 가능한 엔드포인트 목록 점검
  3. 허용 목록: GET /*, PUT /professor/teams/:id/weights
     그 외 변경 API는 LEADER 또는 MEMBER만 가능해야 함

위반 시 영향: "교수는 관찰만 한다" 포지셔닝 위반
```

### INV-05: Docker 자체 완결성
```
규칙: docker compose up -d 한 줄로 전체 스택이 기동되어야 한다.
      외부 managed 서비스(Supabase, Vercel, Render, AWS RDS 등)에 연결하면 안 된다.

검증 방법:
  1. docker-compose.yml의 모든 서비스가 로컬 이미지/공식 이미지인지 확인
  2. application.yml에 외부 호스트(*.supabase.co, *.vercel.app 등) 없는지 확인
  3. 네트워크 차단 상태에서 docker compose up 성공 여부 테스트
     (이미지 캐시 있는 상태에서)

위반 시 영향: 캡스톤 발표 시 네트워크/외부 서비스 장애로 데모 실패 위험
```

### INV-06: 점수 정규화 상한
```
규칙: 정규화된 점수는 0~150 범위를 초과하면 안 된다.
      Math.min(150, normalized) 클리핑이 모든 항목에 적용되어야 한다.

검증 방법:
  1. ScoreEngine / Normalizer에서 모든 normalize() 호출에 상한 클리핑 확인
  2. contribution_scores에 150 초과 값 존재 여부:
     SELECT * FROM contribution_scores WHERE git_score > 150 OR doc_score > 150 ...

위반 시 영향: 1명이 점수를 독점하는 정규화 공정성 문제 재발
```

### INV-07: 동의 없는 데이터 수집 금지
```
규칙: consent_platform=false인 멤버의 활동은 activity_logs에 기록하면 안 된다.
      consent_github=false인 멤버의 GitHub 데이터를 수집하면 안 된다.
      consent_ai_analysis=false인 멤버의 코드를 Claude API로 전송하면 안 된다.

검증 방법:
  1. ActivityLogService.log()에서 동의 여부 체크 로직 확인
  2. GitHubSyncService에서 consent_github 체크 확인
  3. ClaudeAnalyzer에서 consent_ai_analysis 체크 확인

위반 시 영향: PIPA(개인정보보호법) 위반 → 교수님 지적 대상
```

---

## 2. 크로스 파일 일관성 체크

서로 다른 파일에 같은 정보가 중복 기재되어 있으면, 한쪽만 수정하고 다른 쪽을 잊는 **드리프트**가 발생한다.

### SYNC-01: DB 스키마 ↔ Entity ↔ Flyway
```
체크 대상:
  - docs/database.md (설계 문서)
  - src/main/resources/db/migration/V*.sql (Flyway 실제 SQL)
  - src/main/java/**/entity/*.java (JPA Entity)

체크 항목:
  □ database.md의 테이블 목록 = Flyway SQL 파일 수
  □ 각 테이블의 컬럼명/타입이 3곳 모두 일치
  □ 새 컬럼 추가 시 → database.md + V{n}.sql + Entity 세 곳 모두 반영
  □ file_vault의 immutable 트리거가 Flyway SQL에 존재

자동화 힌트:
  # Entity에서 @Column 목록 추출 vs database.md 비교
  grep -rn "@Column\|@Id\|private.*;" --include="*.java" src/main/java/**/entity/
```

### SYNC-02: TypeScript 타입 ↔ Java DTO
```
체크 대상:
  - shared/types.md (인터페이스 정의)
  - frontend/types/*.ts (실제 TypeScript)
  - backend/**/dto/*.java (실제 Java DTO)

체크 항목:
  □ types.md에 정의된 필드 = TS interface 필드 = Java record 필드
  □ 필드 타입 매핑 일치 (UUID ↔ string, LocalDateTime ↔ string 등)
  □ enum 값 일치 (TaskStatus, Priority, AlertType 등)
  □ 새 API 추가 시 → types.md + TS + Java 세 곳 모두 반영

주요 불일치 패턴:
  - 백엔드에서 필드 추가했는데 types.md 미반영 → 프론트에서 타입 에러
  - TS에서 optional(?) 인데 Java에서 @NotNull → 런타임 에러
```

### SYNC-03: API 엔드포인트 ↔ Controller ↔ Axios 훅
```
체크 대상:
  - docs/api-design.md (API 설계 문서)
  - backend/**/controller/*.java (실제 Controller)
  - frontend/hooks/*.ts (실제 API 호출)

체크 항목:
  □ api-design.md에 정의된 모든 엔드포인트가 Controller에 구현되어 있는지
  □ URL 패턴 일치 (/api/projects/:id/tasks vs @GetMapping("/projects/{id}/tasks"))
  □ HTTP Method 일치 (PATCH vs PUT 혼용 없는지)
  □ 프론트 훅의 URL이 백엔드 Controller URL과 정확히 일치하는지
  □ 인증 필요 여부 일치 (api-design.md "인증: O" ↔ SecurityConfig 경로 설정)

자동화 힌트:
  # 백엔드에서 @RequestMapping 추출
  grep -rn "@GetMapping\|@PostMapping\|@PatchMapping\|@DeleteMapping\|@PutMapping" \
    --include="*.java" src/
  # 프론트에서 API 호출 URL 추출
  grep -rn "api\.get\|api\.post\|api\.patch\|api\.delete\|api\.put" \
    --include="*.ts" --include="*.tsx" app/ hooks/
```

### SYNC-04: Docker 환경변수 ↔ application.yml ↔ .env.example
```
체크 대상:
  - docker-compose.yml (환경변수 전달)
  - backend/src/main/resources/application.yml (Spring 설정)
  - .env.example (필요 변수 목록)

체크 항목:
  □ docker-compose.yml의 environment 키 = application.yml의 ${...} 참조
  □ .env.example에 모든 필요 변수가 기재되어 있는지
  □ 새 환경변수 추가 시 → 세 파일 모두 반영
  □ 기본값 불일치 없는지 (docker-compose default vs application.yml default)
```

### SYNC-05: 기획서 ↔ 실제 구현
```
체크 대상:
  - TeamBlackbox_기획서_v3.md (기획서)
  - 실제 코드 전체

체크 항목:
  □ 기획서의 기여도 수식 = ScoreEngine 실제 로직
  □ 기획서의 가중치 기본값 = Score Engine 초기값
  □ 기획서의 경보 조건 (편차 40%, 이탈 14일 등) = AlertEngine 실제 임계값
  □ 기획서의 동의 4단계 = 프론트 온보딩 UI 실제 단계
  □ 기획서의 기술 스택 = 실제 사용 라이브러리

주의: 기획서는 발표 자료의 근거이므로, 구현이 바뀌면 기획서도 반드시 업데이트.
```

---

## 3. 코드 레벨 위반 탐지

주기적으로 아래 명령어를 실행하여 위반 사항을 탐지한다.

### CODE-01: 금지된 패턴 검색
```bash
# 1. file_vault UPDATE/DELETE 시도
grep -rn "file_vault" --include="*.java" | grep -iE "update|delete|remove|set"
# → 결과가 있으면 INV-02 위반

# 2. 외부 managed 서비스 URL
grep -rn "supabase\|vercel\.app\|render\.com\|railway\.app\|amazonaws\.com" \
  --include="*.java" --include="*.yml" --include="*.ts" --include="*.tsx" \
  --include="*.env*" --include="*.json"
# → 결과가 있으면 INV-05 위반

# 3. any 타입 사용 (TypeScript)
grep -rn ": any" --include="*.ts" --include="*.tsx" | grep -v "node_modules\|.next"
# → 결과가 있으면 conventions.md 위반

# 4. Entity 직접 노출 (Controller에서 Entity 반환)
grep -rn "ResponseEntity<.*Entity\|return.*Entity>" --include="*.java" \
  src/main/java/**/controller/
# → 결과가 있으면 DTO 분리 원칙 위반

# 5. 하드코딩된 비밀번호/시크릿
grep -rn "password\s*=\s*\"[^$]" --include="*.java" --include="*.yml" --include="*.ts" \
  | grep -v "test\|spec\|mock\|example"
# → 결과가 있으면 보안 위반

# 6. console.log 잔류 (프론트)
grep -rn "console\.log" --include="*.ts" --include="*.tsx" | grep -v "node_modules\|.next"
# → 배포 전 제거 필요

# 7. System.out.println 잔류 (백엔드)
grep -rn "System\.out\.print" --include="*.java" | grep -v "test\|Test"
# → Logger 사용으로 교체 필요
```

### CODE-02: 활동 로그 커버리지 점검
```bash
# Service 파일에서 public 메서드 수 vs activityLogService.log() 호출 수 비교
echo "=== Service 메서드 수 ==="
grep -rn "public .*(UUID\|String\|Long" --include="*.java" \
  src/main/java/**/service/ | wc -l

echo "=== activity_logs 기록 호출 수 ==="
grep -rn "activityLogService\.log\|ActivityLogService" --include="*.java" \
  src/main/java/**/service/ | wc -l

# 비율이 너무 낮으면 로그 누락 의심
```

### CODE-03: Flyway 마이그레이션 검증
```bash
# Flyway 파일 번호 연속성 확인
ls src/main/resources/db/migration/V*.sql | sort -V
# V1, V2, V3, ... 순서가 끊기면 안 됨

# 마이그레이션 실행 상태 확인 (Docker 내)
docker compose exec db psql -U blackbox -d blackbox_db \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY version;"
```

---

## 4. 도커 환경 건강 체크

### DOCKER-01: 컨테이너 상태
```bash
# 모든 서비스가 healthy/running인지
docker compose ps

# 기대 상태:
#   blackbox-db        running (healthy)
#   blackbox-backend   running
#   blackbox-frontend  running
#   blackbox-nginx     running
```

### DOCKER-02: 볼륨 영속성
```bash
# 볼륨 존재 확인
docker volume ls | grep -E "pgdata|uploads"

# 컨테이너 재시작 후 데이터 유지 확인
docker compose down
docker compose up -d
docker compose exec db psql -U blackbox -d blackbox_db -c "SELECT count(*) FROM users;"
# → 이전에 넣은 데이터가 남아있어야 함
```

### DOCKER-03: 네트워크 격리
```bash
# 외부에서 DB 직접 접근 불가 확인 (프로덕션)
# docker-compose.yml에서 db의 ports: 삭제 또는 주석 처리 확인
grep -A5 "db:" docker-compose.yml | grep "ports"
# → 프로덕션에서는 결과 없어야 함 (내부 네트워크만 허용)
```

### DOCKER-04: 빌드 재현성
```bash
# 클린 빌드 테스트 (캐시 없이)
docker compose build --no-cache
docker compose up -d
# → 에러 없이 전체 스택 기동 확인
```

---

## 5. 점수 엔진 무결성 체크

Score Engine은 프로젝트의 핵심 비즈니스 로직이므로, 별도로 검증한다.

### SCORE-01: 정규화 검증
```sql
-- 모든 점수가 0~150 범위인지
SELECT * FROM contribution_scores 
WHERE git_score < 0 OR git_score > 150
   OR doc_score < 0 OR doc_score > 150
   OR meeting_score < 0 OR meeting_score > 150
   OR task_score < 0 OR task_score > 150;
-- → 결과 0건이어야 함
```

### SCORE-02: 가중치 합계 검증
```sql
-- 가중치 합이 1.00인지
SELECT * FROM contribution_scores 
WHERE ABS(weight_git + weight_doc + weight_meeting + weight_task - 1.00) > 0.01;
-- → 결과 0건이어야 함

-- weight_configs도 동일 검증
SELECT * FROM weight_configs
WHERE ABS(weight_git + weight_doc + weight_meeting + weight_task - 1.00) > 0.01;
```

### SCORE-03: 총합 점수 재현성
```sql
-- total_score가 항목별 점수 × 가중치 합과 일치하는지
SELECT id, total_score,
       ROUND((git_score * weight_git + doc_score * weight_doc 
            + meeting_score * weight_meeting + task_score * weight_task)::numeric, 2) AS expected
FROM contribution_scores
WHERE ABS(total_score - (git_score * weight_git + doc_score * weight_doc 
    + meeting_score * weight_meeting + task_score * weight_task)) > 0.1;
-- → 결과 0건이어야 함
```

### SCORE-04: 활동 로그 ↔ 점수 정합성
```sql
-- activity_logs가 있는데 점수가 0인 팀원 (로그 누락이 아닌 엔진 버그 의심)
SELECT pm.user_id, COUNT(al.id) AS log_count, cs.total_score
FROM project_members pm
LEFT JOIN activity_logs al ON al.project_id = pm.project_id AND al.user_id = pm.user_id
LEFT JOIN contribution_scores cs ON cs.project_id = pm.project_id AND cs.user_id = pm.user_id
WHERE pm.role != 'OBSERVER'
GROUP BY pm.user_id, cs.total_score
HAVING COUNT(al.id) > 5 AND (cs.total_score IS NULL OR cs.total_score = 0);
-- → 결과 0건이어야 함 (활동이 5건 이상인데 점수가 0이면 이상)
```

---

## 6. Hash Vault 무결성 체크

### VAULT-01: DB 해시 ↔ 실제 파일 일치
```bash
# 주기적으로 실행: 저장된 파일을 다시 해시하여 DB 값과 비교
# (확장: 스케줄러로 자동화 가능)

docker compose exec backend java -cp app.jar \
  com.blackbox.api.vault.VaultIntegrityChecker
# 또는 직접 SQL + 파일 해시 비교 스크립트

# 수동 검증:
# 1. DB에서 storage_path + file_hash 조회
# 2. 해당 경로 파일을 sha256sum으로 재해시
# 3. 불일치 시 → tamper_detection_log에 기록
```

### VAULT-02: 트리거 존재 확인
```sql
-- immutable 트리거가 살아있는지
SELECT tgname, tgenabled FROM pg_trigger 
WHERE tgrelid = 'file_vault'::regclass;
-- → vault_immutable이 'O' (enabled) 상태여야 함
```

### VAULT-03: 업로드 디렉토리 권한
```bash
# Docker 볼륨 내 파일 권한 확인
docker compose exec backend ls -la /data/uploads/
# → backend 프로세스가 읽기/쓰기 가능해야 함
# → 외부에서 직접 수정 불가 상태 확인
```

---

## 7. 주기적 GC 실행 스케줄

| 시점 | 체크 항목 | 담당 |
|------|-----------|------|
| **매 PR 머지 전** | INV-01~07 (불변 규칙), CODE-01 (금지 패턴) | PR 작성자 |
| **매주 1회 (스프린트 종료)** | SYNC-01~05 (크로스 파일 일관성) | 팀 전체 |
| **격주 1회** | SCORE-01~04 (점수 무결성), VAULT-01~03 (해시 무결성) | 손정협 (Score), 송승준 (Vault) |
| **매 Phase 완료 시** | DOCKER-01~04 (도커 건강 체크), 전체 통합 검증 | 송병철 |
| **중간발표/최종발표 전** | **전체 항목 Full GC** | 전원 |

---

## 8. GC 실행 요약 명령 (한 번에 돌리기)

AI 어시스턴트에게 아래와 같이 지시하면 전체 GC를 수행한다:

```
"GC 돌려줘" 또는 "드리프트 체크해줘"

→ 이 파일(gc.md)을 읽고 다음을 순서대로 수행:
  1. CODE-01 금지 패턴 검색 실행
  2. CODE-02 활동 로그 커버리지 점검
  3. SYNC-01~04 크로스 파일 diff 체크
  4. DOCKER-01 컨테이너 상태 확인
  5. 결과를 요약하여 보고
```

### 수동 원커맨드 스크립트
```bash
#!/bin/bash
# scripts/gc-check.sh

echo "========== [GC] 금지 패턴 검색 =========="
echo "--- file_vault UPDATE/DELETE ---"
grep -rn "file_vault" --include="*.java" src/ | grep -iE "update|delete|remove|set" || echo "✅ 없음"

echo "--- 외부 서비스 URL ---"
grep -rn "supabase\|vercel\.app\|render\.com\|railway\.app" \
  --include="*.java" --include="*.yml" --include="*.ts" --include="*.tsx" \
  src/ app/ || echo "✅ 없음"

echo "--- any 타입 ---"
grep -rn ": any" --include="*.ts" --include="*.tsx" app/ hooks/ components/ || echo "✅ 없음"

echo "--- 하드코딩 비밀번호 ---"
grep -rn 'password\s*=\s*"[^$]' --include="*.java" --include="*.yml" src/ \
  | grep -v "test\|spec\|mock" || echo "✅ 없음"

echo ""
echo "========== [GC] Docker 상태 =========="
docker compose ps 2>/dev/null || echo "⚠️ Docker Compose 미실행"

echo ""
echo "========== [GC] Flyway 상태 =========="
docker compose exec -T db psql -U blackbox -d blackbox_db \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY version;" \
  2>/dev/null || echo "⚠️ DB 접속 불가"

echo ""
echo "========== [GC] 점수 범위 검증 =========="
docker compose exec -T db psql -U blackbox -d blackbox_db \
  -c "SELECT count(*) AS violations FROM contribution_scores WHERE git_score > 150 OR doc_score > 150 OR meeting_score > 150 OR task_score > 150;" \
  2>/dev/null || echo "⚠️ DB 접속 불가"

echo ""
echo "========== [GC] Vault 트리거 확인 =========="
docker compose exec -T db psql -U blackbox -d blackbox_db \
  -c "SELECT tgname, tgenabled FROM pg_trigger WHERE tgrelid = 'file_vault'::regclass;" \
  2>/dev/null || echo "⚠️ DB 접속 불가"

echo ""
echo "========== [GC] 완료 =========="
```

---

## 9. 드리프트 발생 시 대응 프로세스

```
드리프트 발견
    │
    ├── 코드가 기획서보다 앞서감 (기능 추가/변경)
    │   └→ 기획서 + 컨텍스트 .md 파일 업데이트
    │
    ├── 기획서가 코드보다 앞서감 (미구현)
    │   └→ todo.md에 태스크 추가 → 다음 스프린트에 구현
    │
    ├── 타입/스키마 불일치
    │   └→ 3곳 동시 수정 (database.md + Entity + types.md)
    │      → Flyway 신규 마이그레이션 파일 작성
    │
    ├── 환경변수 불일치
    │   └→ 4곳 동시 수정 (docker-compose.yml + application.yml + .env.example + conventions.md)
    │
    └── 불변 규칙 위반
        └→ 즉시 수정 → PR 블록 → 리뷰 후 머지
```

---

## 10. 컨텍스트 파일 자체의 GC

이 프로젝트의 `.md` 컨텍스트 파일들도 코드와 함께 관리해야 한다.

```
체크 항목:
  □ claude.md의 기술 스택 = 실제 package.json + build.gradle
  □ todo.md의 완료 항목 [x] 표시가 실제 구현 상태와 일치
  □ api-design.md의 엔드포인트 = 실제 Controller 목록
  □ database.md의 테이블 = 실제 Flyway 파일
  □ docker.md의 docker-compose.yml = 실제 파일
  □ 삭제된 기능의 .md 내용이 아직 남아있지 않은지
  □ 새로 추가된 모듈의 .md 파일이 생성되었는지
```
