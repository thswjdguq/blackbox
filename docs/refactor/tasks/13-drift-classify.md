# Task 13 — Drift 분류: 등급 + 결정 1차안 제시

> **실행 주체: Claude (예외 실행, PRINCIPLES §13 + CLAUDE_WORKFLOW §4).** Copilot 비사용.
> Task 10/11/12에서 Copilot + 사용자가 수집한 grep 결과를 Claude가 읽고 해석·분류한다.
> 해석·판단이 필요한 작업이라 Copilot(저수준 모델)에 맡기면 등급 오분류 위험이 크다.

## 1. 한 줄 요약
`docs/refactor/DRIFT_INVENTORY.md`의 INV/SYNC/CODE 모든 행에 등급(Spec/State/Convention) + 결정 1차안(D1~D4)을 Claude가 채우고, D4 항목은 Task 14용 사용자 질문 묶음으로 정리한다.

## 2. 변경 범위 (HARD LIMIT)

- **수정 파일:**
  - `docs/refactor/DRIFT_INVENTORY.md` — "등급", "결정안" 두 컬럼만 채움 + D4 질문 묶음 섹션 추가
  - `docs/refactor/DECISIONS.md` — D3(의도적 차이) 항목을 DEC-DRIFT-NN으로 추가 (해당 시)
- **수정 대상 컬럼:** `등급`, `결정안` (이외 컬럼 — ID·출처·문서진술·코드실제·사용자결정·Task — 수정 금지)
- **예상 변경 라인 수:** 행당 2컬럼 × N행 (Task 10~12 결과 행 수에 의존)
- **예상 변경 파일 수:** 1~2개

## 3. 절대 건드리지 말 것 (Out of Scope)

- DRIFT_INVENTORY.md의 ID·출처·문서진술·코드실제 컬럼 — 사용자가 실행해서 채운 팩트. 수정 금지
- "사용자 결정", "Task" 컬럼 — Task 14/15에서 채움. 비워둠
- 코드·테스트·기타 문서 일체 수정 금지
- DECISIONS.md의 기존 항목 수정 금지 (신규 DEC-DRIFT-NN 추가만)
- **코드 수정 0줄** — 본 Task는 분류 기록만. 위반 수정은 Phase 2~4

## 4. 컨텍스트 / 의도

Task 10/11/12는 grep 출력을 그대로 표에 기록하는 기계적 수집이었다. 본 Task는 그 기록을 **해석**한다:
- 이 항목이 "코드가 버그"(Spec)인가, "문서가 stale"(State)인가, "스타일 차이"(Convention)인가
- 어떤 액션이 필요한가 (D1 코드수정 / D2 문서수정 / D3 의도적차이기록 / D4 사용자확인)

Claude가 1차안을 제시하고 사용자는 Task 14에서 검토·확정만 하면 된다. 이 분업이 PRINCIPLES §3의 핵심.

- 관련 PRINCIPLES: §2 (문서 권위 등급), §3 (Drift 처리 규칙)
- 관련 CLAUDE_WORKFLOW: §3 (Drift 처리 워크플로우), §4 (Claude 예외 실행 4조건)

## 4-1. 의존 관계

- **선행 Task:** 10 + 11 + 12 모두 `refactor/main`에 머지 (DRIFT_INVENTORY.md 완성 후 시작)
- **후행 Task:** 14 (사용자 검토·확정), 15 (Task 매핑)
- **API surface 변경 여부:** No

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §2 (문서의 권위 등급):

| 등급 | 출처 | 본질 | 코드와 충돌 시 |
|---|---|---|---|
| **Spec(의도)** | `md/gc.md` INV-01~07, 기획서 | 동작해야 하는 방식 | **코드가 버그.** D1(코드 수정) |
| **State(상태)** | `md/handover_log.md`, `md/todo.md` | 현재 만들어진 것에 대한 주장 | **문서 stale.** D2(문서 수정) |
| **Convention(스타일)** | 코드 컨벤션, 디자인 시스템 | 권장 스타일 | 비용·가치로 D1/D2/D3 판단 |

> PRINCIPLES.md §3 (Drift 처리 규칙):

- **D1. 코드 수정** — Spec 위반. 별도 커밋, 작업지시서에 INV ID/근거 명시
- **D2. 문서 수정** — State 드리프트. Phase 6 묶음
- **D3. 의도적 차이로 기록** — 둘 다 맞음. DECISIONS.md에 사유 기록
- **D4. 사용자 확인 필요** — 모호. 별도 질문 묶음으로 일괄 확인

> CLAUDE_WORKFLOW §4 (Claude 예외 실행 4조건):

1. 정적 문서 수정 (코드 변경 아님) ✅
2. 콘텐츠가 작업지시서 본문에 완전히 정의 ✅ (§6에 판단 기준 명시)
3. Copilot의 등급 오분류 위험이 큼 ✅ (해석·판단 작업)
4. 사용자 명시 승인 (작업지시서 확정 = 승인) ✅

## 6. 작업 절차 (Claude 직접 실행)

### 6-1. DRIFT_INVENTORY.md 전체 읽기 → Pre-write 보고

`docs/refactor/DRIFT_INVENTORY.md`를 읽고 사용자에게 다음을 먼저 보고 (§7 Required):
1. 총 행 수 + 섹션별 분포 (INV N건, SYNC N건, CODE N건, none N건)
2. 즉시 명확한 항목 수 + D4 후보 수 추정
3. CRITICAL 항목 존재 여부 (CODE-01-E 하드코딩 시크릿)

사용자 확인("계속해줘") 후 §6-2부터 실행.

### 6-2. 등급 분류 기준 (판단 룰 — 순서대로 적용)

**1순위 — INV 위반 → 항상 Spec + D1**

INV-01~07은 gc.md의 불변 규칙. 위반은 예외 없이 `Spec / D1`.
단, INV-01(activity_logs)에서 해당 Service가 utility 성격이라 기록이 불필요한지 모호한 경우 → `Spec / D4` + 사유 명시.

**2순위 — SYNC 불일치**

| SYNC | 판단 기준 | 등급/결정안 |
|---|---|---|
| SYNC-01 (DB↔Entity↔Flyway) | Entity 있는데 Flyway SQL 없음 | Spec / D1 |
| SYNC-01 | 문서(database.md)에 테이블 누락 | State / D2 |
| SYNC-02 (TS↔Java DTO) | TS 타입 파일에 DTO 누락 | Convention / D1 |
| SYNC-02 | TS 타입이 더 많음(실제 미사용) | State / D2 |
| SYNC-03 (API 수 차이) | 단순 카운트 차이 — 의도 불명 | State 또는 D4 |
| SYNC-03 | handover_log API 표 stale | State / D2 |
| SYNC-04 (env 불일치) | docker-compose에 있는데 .env.example 누락 | State / D2 |
| SYNC-04 | application.yml 참조인데 docker-compose 없음 | Spec / D1 |
| SYNC-05 (기획서↔구현) 사용자 "N" | 버그로 보임 | Spec / D1 |
| SYNC-05 사용자 "N" | 의도적 기능 변경으로 보임 | Convention / D3 |
| SYNC-05 사용자 "모름" | 판단 불가 | D4 |

**3순위 — CODE 금지 패턴**

| CODE | 등급/결정안 | 비고 |
|---|---|---|
| CODE-01-C (`: any`) | Convention / D1 | Phase 3 batch fix |
| CODE-01-D (Entity 노출) | Convention / D1 | Phase 3 |
| CODE-01-E (하드코딩 시크릿) | **Spec / D1 CRITICAL** | Phase 4 즉시 |
| CODE-01-F (debug print) | Convention / D1 | Phase 3 batch fix |
| CODE-03 (Flyway 연속성 끊김) | Spec / D1 | 즉시 확인 |

### 6-3. 각 행에 등급 + 결정안 채우기

"none" 기록 행은 건너뜀. 나머지 각 행에 §6-2 룰로 두 컬럼 채움.

예시:
```
| D-INV-02-A | INV-02 | file_vault UPDATE 금지 | VaultService.java:72 update() | Spec | D1 | | |
| D-SYN-03-A | SYNC-03 | API 수 일치 | Controller 25건 vs 프론트 18건 | State | D4 | | |
| D-CODE-C-01 | CODE-01-C | `: any` 금지 | api.ts:34, kanban/Board.tsx:91 | Convention | D1 | | |
```

### 6-4. D3 항목 → DECISIONS.md 추가

D3로 분류한 항목마다 DECISIONS.md에 신규 entry 추가:

```markdown
## DEC-DRIFT-NN — <항목 설명>
- **일시:** <날짜>
- **항목:** D-<ID>
- **문서 진술:** <진술>
- **코드 실제:** <실제>
- **결정:** 의도적 차이로 기록. <사유>
- **재검토 조건:** <필요 시>
```

### 6-5. D4 항목 → Task 14용 질문 묶음 정리

DRIFT_INVENTORY.md 하단에 다음 섹션을 추가:

```markdown
## D4 — 사용자 확인 필요 항목 (Task 14에서 답변)

> 코드·문서만으로 의도를 판단하기 어려운 항목. 아래 질문에 답변 후 Task 14에서 확정.

| D4 항목 ID | 질문 | 선택지 |
|---|---|---|
| D-XXX | <질문> | A: <설명> / B: <설명> / C: 직접 확인 필요 |
```

질문은 **예/아니오 또는 A/B/C 선택** 형태로. 사용자가 컨텍스트 없이도 답할 수 있게 한 줄로.

### 6-6. 실행 로그 갱신

DRIFT_INVENTORY.md 실행 로그 표에 1행 추가:
```
| <날짜> | Claude | 분류 (Task 13) | <총 분류 행 수> |
```

## 7. Pre-write 프로토콜 적용 여부

- [x] **Required** — §6-1 보고 후 사용자 확인을 받고 §6-3 실행.
  보고 내용: 총 행 수 / 섹션별 분포 / CRITICAL 존재 여부 / D4 예상 수.

## 8. 검수 기준 (Acceptance Criteria)

- [ ] DRIFT_INVENTORY.md의 모든 비-"none" 행에 등급(Spec/State/Convention) 채워짐
- [ ] 모든 비-"none" 행에 결정안(D1~D4) 채워짐
- [ ] INV 위반 항목은 예외 없이 Spec+D1 (또는 D4 + 사유 명시)
- [ ] CODE-01-E(하드코딩 시크릿) 발견 시 CRITICAL 표시 유지
- [ ] D3 항목은 DECISIONS.md에 DEC-DRIFT-NN으로 추가됨
- [ ] D4 항목은 Task 14용 질문 묶음으로 정리됨 (선택지 포함)
- [ ] ID·출처·문서진술·코드실제·사용자결정·Task 컬럼 변경 0건
- [ ] 코드 변경 0줄
- [ ] CI(refactor-guard) 통과

## 9. PR 정보

- **Branch:** `refactor/13-drift-classify`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-13] Drift 분류: 등급 + 결정 1차안`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 13](../docs/refactor/tasks/13-drift-classify.md)
  - 의존: Task 10 + 11 + 12 머지

  ## 변경 요약
  - DRIFT_INVENTORY.md 등급·결정안 컬럼 채움
  - D3 항목 DECISIONS.md 추가 (<N>건)
  - D4 질문 묶음 섹션 추가 (<N>건)

  ## 분류 결과 요약
  - D1 (코드 수정): <N>건
  - D2 (문서 수정 → Phase 6): <N>건
  - D3 (의도적 차이): <N>건
  - D4 (사용자 확인): <N>건
  - CRITICAL (시크릿 하드코딩): <Y/N>

  ## HARD LIMIT 준수
  - 변경 파일: DRIFT_INVENTORY.md + (있으면) DECISIONS.md
  - 코드 변경 0줄

  ## 검수
  - [x] INV 위반 전부 Spec+D1
  - [x] D4 질문 묶음 정리 완료
  - [x] CI 통과
  ```
