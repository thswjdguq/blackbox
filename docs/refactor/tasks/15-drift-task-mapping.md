# Task 15 — Drift Task 매핑: Phase 2~6 작업지시서 목록 확정

## 1. 한 줄 요약
Task 14에서 확정된 DRIFT_INVENTORY.md를 읽어 D1 항목 → Phase 2~4 Task, D2 항목 → Phase 6 묶음, D3 항목 → DECISIONS.md 확인의 3방향 매핑을 완료하고 PLAN.md Phase 2~4 Task 후보 목록을 확정한다.

## 2. 변경 범위 (HARD LIMIT)

- **수정 파일:**
  - `docs/refactor/DRIFT_INVENTORY.md` — "Task" 컬럼 채움
  - `docs/refactor/PLAN.md` — Phase 2~4 Task 후보 목록에 Drift 기반 항목 추가·정렬
  - `docs/refactor/DECISIONS.md` — D3 항목 DEC-DRIFT-NN 누락분 추가 (해당 시)
- **수정 대상:**
  - DRIFT_INVENTORY.md: `Task` 컬럼만
  - PLAN.md: Phase 2~4 Task 후보 섹션에 행 추가 또는 기존 후보에 Drift ID 주석 추가
- **예상 변경 라인 수:** D1 행 수 × 1 + PLAN.md 추가 행
- **예상 변경 파일 수:** 2~3개

## 3. 절대 건드리지 말 것 (Out of Scope)

- DRIFT_INVENTORY.md의 ID·출처·문서진술·코드실제·등급·결정안·사용자결정 컬럼 수정 금지
- PLAN.md의 Phase 0·1·5·6 섹션 수정 금지 (Phase 2~4만)
- 작업지시서 파일(`docs/refactor/tasks/NN-xxx.md`) 신규 작성 금지 — 목록 확정만, 실제 작성은 다음 Claude 세션
- 코드·테스트 일체 수정 금지

## 4. 컨텍스트 / 의도

Phase 1의 마지막 단계다. 이 Task가 완료되면 Phase 2 작업지시서를 바로 작성할 수 있는 상태가 된다. 핵심 산출물은 두 가지:
1. **DRIFT_INVENTORY.md "Task" 컬럼**: 각 D1 항목이 어느 Task에서 수정될지
2. **PLAN.md Phase 2~4 Task 후보 목록**: Drift 결과가 반영된 확정 로드맵

- 관련 PRINCIPLES: §3 (Drift 처리 규칙), §5 (형상관리)
- 관련 PLAN.md: Phase 1 완료 조건 / Phase 2~4 Task 후보

## 4-1. 의존 관계

- **선행 Task:** 14 (`refactor/main`에 머지)
- **후행 Task:** Phase 2 첫 묶음 작업지시서 작성 (다음 Claude 세션)
- **API surface 변경 여부:** No

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §3 (Drift 처리 규칙):

- **D1** → Phase 2~4 Task로 매핑
- **D2** → Phase 6 문서화 작업에 묶음
- **D3** → DECISIONS.md 기록 (Task 13에서 이미 완료, 본 Task에서 누락분만 확인)

> PLAN.md Phase 2~4 Task 후보:

- 20~29: Cross-cutting (2A)
- 30~39: 백엔드 도메인 (2B)
- 40~49: 프론트 도메인 (2C)
- 50~69: Phase 3 품질
- 70~79: Phase 4 버그·UX

## 6. 작업 절차 (Claude 직접 실행)

> **실행 주체: Claude.** DRIFT_INVENTORY.md를 읽고 논리적 매핑을 수행.
> 판단 요소가 있어 Copilot에 맡기면 잘못된 Phase에 매핑될 위험이 있음.

### 6-1. 확정된 D1 항목 목록 추출

DRIFT_INVENTORY.md에서 "사용자 결정" = `D1 확정`인 행만 추출.
각 행에 대해:
- 어느 도메인인가 (Auth / Project / Task / Meeting / Vault / Score / Calendar / Notion / Discord / Report / Risk)
- 백엔드인가 프론트인가 (또는 양쪽)
- Cross-cutting인가 도메인-specific인가

### 6-2. 매핑 룰 (Task ID 할당 기준)

| D1 항목 특성 | 매핑 대상 Phase/범위 | 비고 |
|---|---|---|
| INV-01 누락 (activity_logs) — 여러 도메인 | `22-activity-log-coverage` | 기존 PLAN 후보에 흡수 |
| INV-02 (file_vault 뮤테이션) | `34-vault-module` 또는 신규 `70-fix-vault-mutation` | 버그면 Phase 4 즉시 |
| INV-03/04/06/07 — 단일 도메인 | 해당 도메인 Task (30~39 또는 40~49) | |
| INV-05 (외부 서비스 URL) | `20-exception-handling-unify` 또는 신규 | 위치에 따라 |
| SYNC-01 (Entity↔Flyway) | `24-db-legacy-score-columns` 또는 신규 V18+ Task | |
| SYNC-02 (TS 타입 누락) | `42-types-organize` | 기존 후보 흡수 |
| SYNC-04 (env 누락) | `chore:` 커밋으로 즉시 처리 가능 여부 판단 후 Task 배정 | 작을 경우 |
| CODE-01-E (하드코딩 시크릿) | `70-fix-hardcoded-secret` — **Phase 4 최우선** | CRITICAL |
| CODE-01-C/F (any, debug) | `62-typescript-strict`, `54-logger-replace-sysout` | Phase 3 batch |
| CODE-01-D (Entity 노출) | 해당 도메인 Controller Task 또는 `53-validation-pattern` | |
| CODE-03 (Flyway 끊김) | 신규 `chore/flyway-renumber` | 즉시 |

### 6-3. 기존 PLAN.md 후보와 비교·통합

PLAN.md Phase 2~4 Task 후보 목록을 읽고:
- Drift 항목이 **기존 후보에 흡수 가능**하면: 기존 후보 설명에 `(D-XXX 포함)` 주석 추가
- **신규 Task가 필요**하면: 적절한 범위(20~79)에 신규 항목 추가

신규 Task 추가 형식 (PLAN.md 내):
```
- `NN-<short>` — <한 줄 설명> *(Drift D-XXX 기반)*
```

### 6-4. DRIFT_INVENTORY.md "Task" 컬럼 채우기

각 D1 항목에 §6-3에서 결정한 Task ID 기재:
```
| D-INV-02-A | INV-02 | ... | ... | Spec | D1 | D1 확정 | 34-vault-module |
| D-CODE-E-01 | CODE-01-E | ... | ... | Spec | D1 | D1 확정 | 70-fix-hardcoded-secret |
```

D2 항목: `Task` 컬럼에 `Phase 6` 기재.
D3 항목: `Task` 컬럼에 `DECISIONS.md DEC-DRIFT-NN` 기재.
유예 항목: `Task` 컬럼에 `DEFERRED.md` 기재.

### 6-5. Phase 1 완료 조건 체크

PLAN.md Phase 1 완료 조건 6개를 확인:
```
- [x] INV/SYNC/CODE 3배치 모두 스캔 완료 (Task 10/11/12)
- [x] DRIFT_INVENTORY.md에 모든 항목 기재 (Task 10/11/12)
- [x] 모든 항목에 등급 + 결정 1차안 (Task 13)
- [x] 모든 항목에 사용자 확정 결정 (Task 14)
- [x] D1 항목들이 Phase 2~4 Task로 매핑됨 (본 Task)
- [x] D2 항목들이 Phase 6 묶음으로 들어감 (본 Task)
- [x] D3 항목들이 DECISIONS.md에 기록됨 (Task 13 + 본 Task)
```

모든 [x] 채워지면 Phase 1 완료 조건 충족.

### 6-6. Session End 프로토콜 준비 (사용자에게 보고)

Phase 1 완료 후 다음 세션 진입점을 사용자에게 보고:
- Phase 2 첫 묶음 작업지시서: `20-exception-handling-unify`, `21-webclient-error-pattern`, `22-activity-log-coverage` (1~3개 묶음)
- CRITICAL 항목 있으면 Phase 4 `70-fix-hardcoded-secret`을 Phase 2보다 먼저 처리 권고

SESSION_LOG.md에 본 세션 entry 추가 (§6 Session End 프로토콜, CLAUDE.md §4).

## 7. Pre-write 프로토콜 적용 여부

- [x] **Required** — 매핑 전에 Claude가 다음을 먼저 보고:
  1. D1 항목 목록 + 도메인별 분포 요약
  2. CRITICAL 항목 (CODE-01-E) 존재 시 별도 강조
  3. 기존 PLAN 후보 흡수 가능 항목 vs 신규 Task 필요 항목 구분
  사용자 확인 후 §6-4 실행.

## 8. 검수 기준 (Acceptance Criteria)

- [ ] DRIFT_INVENTORY.md 모든 비-"none" 행에 "Task" 컬럼 채워짐
- [ ] D1 확정 행: 구체적 Task ID (예: `34-vault-module`) 또는 신규 Task 이름
- [ ] D2 확정 행: `Phase 6`
- [ ] D3 확정 행: `DECISIONS.md DEC-DRIFT-NN`
- [ ] 유예 행: `DEFERRED.md`
- [ ] PLAN.md Phase 2~4 후보에 Drift 기반 Task 반영 (흡수 또는 신규 추가)
- [ ] PLAN.md Phase 1 완료 조건 모두 [x] 처리
- [ ] CODE-01-E(CRITICAL) 발견 시 Phase 4 최우선 Task로 명시됨
- [ ] 등급·결정안·사용자결정 등 기존 컬럼 변경 0건
- [ ] 코드 변경 0줄
- [ ] CI(refactor-guard) 통과

## 9. PR 정보

- **Branch:** `refactor/15-drift-task-mapping`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-15] Drift Task 매핑: Phase 2~4 로드맵 확정`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 15](../docs/refactor/tasks/15-drift-task-mapping.md)
  - 의존: Task 14 머지

  ## 변경 요약
  - DRIFT_INVENTORY.md "Task" 컬럼 전체 채움
  - PLAN.md Phase 2~4 Task 후보 Drift 결과 반영
  - Phase 1 완료 조건 전체 [x] 처리

  ## 매핑 결과 요약
  - D1 → Phase 2 (20~29): <N>건
  - D1 → Phase 2 (30~39): <N>건
  - D1 → Phase 2 (40~49): <N>건
  - D1 → Phase 3 (50~69): <N>건
  - D1 → Phase 4 (70~79): <N>건 (CRITICAL: <Y/N>)
  - D2 → Phase 6: <N>건
  - 신규 Task 추가: <N>건

  ## Phase 1 완료 선언
  - DRIFT_INVENTORY.md 모든 행 사용자 확정 + Task 매핑 완료
  - Phase 2 진입 준비 완료

  ## 다음 세션 진입점
  - (CRITICAL 있으면) Phase 4 `70-fix-hardcoded-secret` 먼저
  - Phase 2A 첫 묶음: `20`, `21`, `22`

  ## HARD LIMIT 준수
  - 변경 파일: DRIFT_INVENTORY.md + PLAN.md + (있으면) DECISIONS.md
  - 코드 변경 0줄

  ## 검수
  - [x] 모든 행 Task 컬럼 채워짐
  - [x] Phase 1 완료 조건 전체 [x]
  - [x] CI 통과
  ```
