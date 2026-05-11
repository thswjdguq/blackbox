# Task 14 — Drift 사용자 검토·확정

## 1. 한 줄 요약
Task 13에서 Claude가 제시한 등급·결정 1차안을 사용자가 검토하고 D4 항목에 답변해 DRIFT_INVENTORY.md의 "사용자 결정" 컬럼을 확정한다.

## 2. 변경 범위 (HARD LIMIT)

- **수정 파일:**
  - `docs/refactor/DRIFT_INVENTORY.md` — "사용자 결정" 컬럼만 채움
  - `docs/refactor/DECISIONS.md` — D4 → D3 전환 항목 추가 (해당 시), 사용자가 1차안을 뒤집는 경우 기록
- **수정 대상 컬럼:** `사용자 결정` (이외 컬럼 수정 금지)
- **예상 변경 라인 수:** 비-"none" 행 수 × 1컬럼
- **예상 변경 파일 수:** 1~2개

## 3. 절대 건드리지 말 것 (Out of Scope)

- 등급·결정안 컬럼 — Claude 1차안이 그대로 유지 (사용자가 변경하는 경우 §6-4 참조)
- ID·출처·문서진술·코드실제·Task 컬럼 수정 금지
- "Task" 컬럼 — Task 15에서 채움. 비워둠
- 코드·테스트·다른 문서 일체 수정 금지

## 4. 컨텍스트 / 의도

Claude 1차안은 판단을 구조화하지만 틀릴 수 있다. 사용자(캡스톤 본인 코드 담당)가 최종 확정해야 다음 Phase 작업의 근거가 된다. 특히 D4 항목은 사용자 응답 없이는 분류 자체가 불가능하다.

PRINCIPLES §3 워크플로우:
> Claude가 등급·결정 1차안 제시 → **사용자가 검토·확정** → Copilot 작업지시서에 반영

- 관련 PRINCIPLES: §3 (Drift 처리 규칙)
- 관련 CLAUDE_WORKFLOW: §3 (Drift 처리 워크플로우)

## 4-1. 의존 관계

- **선행 Task:** 13 (`refactor/main`에 머지)
- **후행 Task:** 15 (확정된 결정 기반으로 Task 매핑)
- **API surface 변경 여부:** No

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §3 (Drift 처리 규칙):

결정 타입:
- **D1** → Phase 2~4 Task로 매핑
- **D2** → Phase 6 묶음
- **D3** → DECISIONS.md
- **D4** → 사용자 확인 후 D1/D2/D3 중 하나로 확정

사용자 확인 필요(D4) 항목은 별도 질문 묶음으로 일괄 확인.

> PRINCIPLES.md §7 (의도가 모호할 때):

그래도 모호 → **리팩토링 유예**. 작업지시서에서 해당 부분 제외하고 `docs/refactor/DEFERRED.md`에 기록.

## 6. 작업 절차 (사용자 + Copilot 협력)

> **실행 주체: 사용자 답변 + Copilot 기록.**
> 사용자가 판단·답변하고 Copilot이 DRIFT_INVENTORY.md에 기록.

### 6-1. Task 13 PR 머지 확인

`refactor/main`에 Task 13이 머지되었는지 확인:
```bash
git checkout refactor/main
git pull origin refactor/main
cat docs/refactor/DRIFT_INVENTORY.md | grep "D4 —"
```
D4 질문 묶음 섹션이 보이면 준비 완료.

### 6-2. 사용자 D1/D2/D3 항목 검토 (1차안 확인)

Copilot 발화:
> "DRIFT_INVENTORY.md의 D1/D2/D3 항목을 아래와 같이 정리했습니다:
> [섹션별 요약 표 출력]
>
> 1차안에 동의하지 않는 항목이 있으면 알려주세요 (항목 ID + 변경 원하는 결정).
> 없으면 '1차안 전부 확정'이라고 알려주세요."

사용자가 응답 → Copilot은 이견 없는 항목의 "사용자 결정" 컬럼에 `1차안 확정` 기재.

### 6-3. D4 항목 일괄 질문

Copilot 발화:
> "D4(사용자 확인 필요) 항목입니다. 각 항목에 A/B/C 중 선택해주세요:
>
> [DRIFT_INVENTORY.md D4 섹션의 질문 표 그대로 출력]
>
> 전체를 한 번에 답변하셔도 됩니다. 예: 'D-SYN-03: A, D-SYN-05-X: B, D-INV-01-B: C'"

사용자 응답 → Copilot은 각 D4 항목에 대해:
- A/B 선택 → 해당 결정안(D1/D2/D3)으로 확정, "사용자 결정" 컬럼에 기재
- C(직접 확인 필요) → `유예 — DEFERRED.md` 기재

### 6-4. 1차안 변경 항목 처리

사용자가 Claude 1차안과 다른 결정을 내리면:
1. DRIFT_INVENTORY.md "사용자 결정" 컬럼에 변경된 결정 기재
2. DECISIONS.md에 변경 사유 추가:
   ```markdown
   ## DEC-DRIFT-NN — Claude 1차안 변경
   - **항목:** D-<ID>
   - **Claude 1차안:** <등급> / <결정안>
   - **사용자 결정:** <등급> / <결정안>
   - **사유:** <사용자 설명>
   ```

### 6-5. 유예 항목 → DEFERRED.md

"C(직접 확인 필요)"로 답한 항목:
```markdown
## DEFERRED-NN — <항목 설명>
- **원 ID:** D-<ID>
- **사유:** 사용자가 코드 직접 확인 필요 (의도 불명)
- **후속 처리:** Phase 2/3 해당 도메인 작업 시 재검토
```

DEFERRED.md가 없으면 이 시점에 신규 생성.

### 6-6. 전체 확정 상태 검증

모든 비-"none" 행에 "사용자 결정" 컬럼이 채워졌는지 확인:
```bash
grep -c "| |" docs/refactor/DRIFT_INVENTORY.md
```
결과가 0이면 완료. 아니면 미완료 행 찾아서 처리.

### 6-7. 실행 로그 갱신

실행 로그 표에 1행 추가:
```
| <날짜> | <사용자 이름> | 사용자 확정 (Task 14) | <확정 행 수> |
```

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — 절차가 6-1~6-7로 명확. 사용자 응답 수집 → Copilot 기록 구조.

## 8. 검수 기준 (Acceptance Criteria)

- [ ] DRIFT_INVENTORY.md 모든 비-"none" 행에 "사용자 결정" 컬럼 채워짐
- [ ] "사용자 결정" 컬럼 값은 반드시 `D1 확정` / `D2 확정` / `D3 확정` / `유예 — DEFERRED.md` 중 하나
- [ ] D4 질문 항목 전부 처리됨 (A/B 선택 또는 유예)
- [ ] 1차안 변경 항목은 DECISIONS.md에 기록됨
- [ ] 유예 항목은 DEFERRED.md에 기록됨
- [ ] 등급·결정안·ID·출처·문서진술·코드실제·Task 컬럼 변경 0건
- [ ] 코드 변경 0줄
- [ ] CI(refactor-guard) 통과

## 9. PR 정보

- **Branch:** `refactor/14-drift-user-review`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-14] Drift 사용자 검토·확정`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 14](../docs/refactor/tasks/14-drift-user-review.md)
  - 의존: Task 13 머지

  ## 변경 요약
  - DRIFT_INVENTORY.md "사용자 결정" 컬럼 전체 확정
  - 1차안 변경 항목 DECISIONS.md 추가 (<N>건)
  - 유예 항목 DEFERRED.md 추가 (<N>건)

  ## 확정 결과 요약
  - D1 확정: <N>건
  - D2 확정: <N>건
  - D3 확정: <N>건
  - 유예: <N>건
  - 1차안 변경: <N>건

  ## HARD LIMIT 준수
  - 변경 파일: DRIFT_INVENTORY.md + (있으면) DECISIONS.md + DEFERRED.md
  - 코드 변경 0줄

  ## 검수
  - [x] 모든 비-"none" 행에 "사용자 결정" 채워짐
  - [x] D4 항목 전부 처리
  - [x] CI 통과
  ```
