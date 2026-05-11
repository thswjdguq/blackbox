# Task 11 — Drift Scan: SYNC (크로스파일 일관성)

## 1. 한 줄 요약
`md/gc.md` SYNC-01~05 검사를 사용자가 실행하고 발견된 불일치를 `docs/refactor/DRIFT_INVENTORY.md`의 SYNC 섹션에 누적한다.

## 2. 변경 범위 (HARD LIMIT)

- **수정 파일:**
  - `docs/refactor/DRIFT_INVENTORY.md` — SYNC 섹션의 placeholder를 표로 교체
- **수정 라인 수:** ~50~100줄 (5개 SYNC 결과)
- **변경 파일 수:** 1개

## 3. 절대 건드리지 말 것 (Out of Scope)

- 발견된 불일치의 **수정 시도 절대 금지** — 본 Task는 *기록*만
- DRIFT_INVENTORY.md의 INV(Task 10) / CODE(Task 12) 섹션 수정 금지
- 표 컬럼 정의·실행 로그 외 헤더는 그대로 유지
- 코드·테스트·다른 문서 일체 수정 금지

## 4. 컨텍스트 / 의도

SYNC 검사는 **문서 ↔ 코드 ↔ 다른 문서** 간 일관성을 본다. 발견된 불일치는 등급 분류 단계(Task 13)에서 Spec(코드 버그) / State(문서 stale) / Convention 중 하나로 판단됨.

- 관련 SYNC: SYNC-01~05 전체
- 관련 PRINCIPLES: §3 (Drift 처리 규칙), §13 (오케스트레이터 패턴)

## 4-1. 의존 관계

- **선행 Task:** 10 (DRIFT_INVENTORY.md 스켈레톤 생성)
- **후행 Task:** 12 (CODE 섹션), 13 (분류)
- **API surface 변경 여부:** No

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §3 (Drift 처리 규칙):

`md/gc.md`의 SYNC-01~05 / CODE-01~03 검사를 실제 실행하여 표를 채운다.

> PRINCIPLES.md §13 (오케스트레이터 패턴):

명령은 사용자가 실행. Copilot은 안내·정리만.

## 6. 작업 절차 (오케스트레이터 + 사용자 실행 패턴)

> **Copilot은 검색·비교를 직접 실행하지 않는다.** 사용자에게 명령 실행을 요청하고 받은 출력을 표로 정리.
> 환경: **WSL Ubuntu bash**.

### 6-1. SYNC 섹션 placeholder 교체 준비

DRIFT_INVENTORY.md의 SYNC 섹션 placeholder를 표 헤더로 교체:

```markdown
## SYNC (크로스파일 일관성) — Task 11

| ID | 출처 | 문서 진술 | 코드 실제 | 등급 | 결정안 | 사용자 결정 | Task |
|---|---|---|---|---|---|---|---|
```

### 6-2. SYNC-01 — DB 스키마 ↔ Entity ↔ Flyway

Copilot 발화:
> "다음 명령들 결과 부탁드려요:
> ```bash
> # 1. Flyway 파일 카운트
> ls backend/src/main/resources/db/migration/V*.sql | wc -l
>
> # 2. Entity 클래스 카운트
> ls backend/src/main/java/com/blackbox/entity/*.java | wc -l
>
> # 3. database.md 존재 여부 + 테이블 언급 카운트
> ls docs/database.md 2>/dev/null && grep -cE 'CREATE TABLE|^## ' docs/database.md
>
> # 4. file_vault immutable 트리거 SQL에 존재
> grep -l 'prevent_vault_modification\\|vault_immutable' backend/src/main/resources/db/migration/V*.sql
> ```
> 결과를 모두 알려주세요. (3번에서 docs/database.md가 없으면 'none'.)"

Copilot 처리:
- Flyway vs Entity 카운트 차이 → D-SYN-01-A 행 추가 (Convention 등급 1차)
- database.md 부재 → D-SYN-01-B 행 (State 등급, Phase 6 문서화 후보)
- vault_immutable 트리거 누락 → INV-02와 연계 (이미 INV에 잡혔을 가능성)

### 6-3. SYNC-02 — TypeScript 타입 ↔ Java DTO

Copilot 발화:
> "두 명령 결과 부탁드려요:
> ```bash
> # 1. Java DTO 카운트
> ls backend/src/main/java/com/blackbox/dto/*.java | wc -l
>
> # 2. TypeScript types 파일·인터페이스 수
> ls frontend/src/types/*.ts 2>/dev/null
> grep -c 'export.*interface\\|export.*type' frontend/src/types/*.ts 2>/dev/null
> ```
> 결과 + 'shared/types.md'가 존재하는지(`ls shared/types.md 2>/dev/null`)도 알려주세요."

Copilot 처리:
- DTO vs TS 타입 카운트 비교 → 차이 크면 D-SYN-02 행
- shared/types.md 부재 → State 등급 (없는 게 정상일 가능성)

### 6-4. SYNC-03 — API 엔드포인트 ↔ Controller ↔ Axios 호출

Copilot 발화:
> "다음 명령 결과 부탁드려요:
> ```bash
> # 1. Controller 엔드포인트
> grep -rEn '@(Get|Post|Patch|Put|Delete)Mapping' \
>   backend/src/main/java/com/blackbox/controller/ \
>   --include='*.java' | wc -l
>
> # 2. Frontend API 호출
> grep -rEn 'api\\.(get|post|patch|put|delete)' \
>   frontend/src/ --include='*.ts' --include='*.tsx' | wc -l
>
> # 3. handover_log API 표 행 수 (대략)
> grep -cE '^\\| (POST|GET|PATCH|PUT|DELETE)' md/handover_log.md
> ```
> 세 숫자를 알려주세요. handover_log API 표가 정본 후보."

Copilot 처리:
- 세 카운트 비교 → 큰 차이가 있으면 D-SYN-03 행 (handover_log가 stale일 가능성 ↑ — State 등급)

### 6-5. SYNC-04 — Docker 환경변수 ↔ application.yml ↔ .env.example

Copilot 발화:
> "다음 명령 결과 부탁드려요:
> ```bash
> # 1. docker-compose.yml의 environment 키들
> grep -E '^\\s+[A-Z_]+:' docker-compose*.yml 2>/dev/null | sort -u
>
> # 2. application.yml의 \${...} 참조
> grep -oE '\\$\\{[A-Z_]+' backend/src/main/resources/application*.yml 2>/dev/null | sort -u
>
> # 3. .env.example 키들
> grep -E '^[A-Z_]+=' .env.example 2>/dev/null
> ```
> 세 결과 모두 붙여주세요."

Copilot 처리:
- 세 셋 간 차집합 → 누락 있으면 D-SYN-04 행 (Spec/State 혼재 가능 — Task 13에서 분류)

### 6-6. SYNC-05 — 기획서 ↔ 실제 구현

Copilot 발화:
> "수동 비교가 필요한 항목입니다. 다음 5가지에 대해 사용자가 알고 있는 한 답변 부탁드려요:
> 1. 기획서의 기여도 수식 vs 실제 ScoreEngine — 일치? (Y/N/모름)
> 2. 기획서의 가중치 기본값 vs 실제 — 일치? (Y/N/모름)
> 3. 기획서의 경보 임계값(편차 40%, 이탈 14일 등) vs 실제 — 일치? (Y/N/모름)
> 4. 기획서의 동의 4단계 vs 실제 온보딩 UI — 일치? (Y/N/모름)
> 5. 기획서 기술 스택 vs 실제 — 일치? (Y/N/모름)
> '모름'은 답변 OK. 모름인 항목은 D4(사용자 결정 필요)로 기록."

Copilot 처리:
- N으로 답한 항목 → D-SYN-05-N 행 (Spec 또는 State, 사용자 응답 그대로 기록)
- 모름 → D4 결정안

### 6-7. 실행 로그 갱신 + 사용자 검증

Copilot 발화:
> "SYNC-01~05 검사 완료. 실행 로그에 행 추가, SYNC 섹션에 N개 행 추가. 검토 부탁드립니다."

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — 절차 명확.
- **검색은 Copilot이 직접 실행하지 않음**

## 8. 검수 기준 (Acceptance Criteria)

- [ ] DRIFT_INVENTORY.md SYNC 섹션 채워짐
- [ ] **모든 행 데이터는 사용자 실행/응답 기반** (환각 0)
- [ ] SYNC-01~05 5개 모두 검사 (불일치 없으면 "none" 명시)
- [ ] 등급은 1차안 (Task 13에서 재검토)
- [ ] 사용자 결정·Task 칸은 비어있음 (Task 14·15에서 채움)
- [ ] INV(Task 10)·CODE(Task 12) 섹션 수정 0건
- [ ] 코드 변경 0줄
- [ ] CI 통과

## 9. PR 정보

- **Branch:** `refactor/11-drift-scan-SYNC`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-11] Drift Scan SYNC-01~05`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 11](../docs/refactor/tasks/11-drift-scan-SYNC.md)
  - 의존: Task 10 머지

  ## 검사 결과 요약
  - SYNC-01 (DB↔Entity↔Flyway): <N>건
  - SYNC-02 (TS↔Java DTO): <N>건
  - SYNC-03 (API↔Controller↔호출): <N>건
  - SYNC-04 (env): <N>건
  - SYNC-05 (기획서↔구현): <N>건 (사용자 응답 기반)

  ## HARD LIMIT 준수
  - 변경 파일: DRIFT_INVENTORY.md만
  - 코드 변경 0줄

  ## 검수
  - [x] 환각 0
  - [x] CI 통과
  ```
