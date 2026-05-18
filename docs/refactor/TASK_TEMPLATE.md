# Task 작업지시서 템플릿

> Claude가 `docs/refactor/tasks/NN-xxx.md`를 만들 때 사용하는 표준 양식.
> 모든 섹션은 필수. "해당 없음"이면 그렇게 적기.

---

# Task NN — <한 줄 제목>

## 1. 한 줄 요약
<무엇을, 왜, 어디서. 한 문장 이내.>

## 2. 변경 범위 (HARD LIMIT)

> Copilot은 이 섹션 밖의 파일·메서드를 절대 건드리지 않는다.

- **파일 (이외 금지):**
  - `backend/src/main/java/com/blackbox/<path>.java`
  - `frontend/src/<path>.tsx`
- **메서드/함수/클래스 (이외 금지):**
  - `XxxService.create()`
  - `YyyController.getList()`
- **예상 변경 라인 수:** ~N줄 (±30%)
- **예상 변경 파일 수:** N개

## 3. 절대 건드리지 말 것 (Out of Scope)

- 같은 파일의 다른 메서드 (별도 Task에서 처리)
- import 정렬·주석 정리·네이밍 통일 (별도 Task)
- DB 스키마 (해당 시 별도 V18+ Task)
- 테스트 외 다른 디렉터리

## 4. 컨텍스트 / 의도

<왜 이 변경이 필요한가. 어떤 INV / Drift ID와 연결되는가. 변경 후 기대 효과.>

- 관련 INV: <INV-XX (gc.md)> 또는 "해당 없음"
- 관련 Drift ID: <D-XX (DRIFT_INVENTORY.md)> 또는 "해당 없음"
- 관련 PRINCIPLES 섹션: §X.Y

## 4-1. 의존 관계

> 의존하는 Task가 `refactor/main`에 머지된 후에만 이 Task 시작.
> 의존성 무시하고 시작하면 베이스가 stale해서 충돌·재작업 발생.

- **선행 Task (이게 먼저 머지되어야 함):** <Task ID 나열, 또는 "없음">
- **후행 Task (이 Task 머지 후 진행):** <Task ID 나열 — 참고용>
- **API surface 변경 여부:** Yes / No
  - Yes면 acceptance에 "API 표·types 갱신" 포함 (PLAN §Phase 2 추가 규칙)

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md의 관련 섹션을 anchor 참조가 아니라 **본문을 발췌**하여 박는다.
> Copilot이 PRINCIPLES.md를 자동으로 안 읽기 때문.

```
<PRINCIPLES.md §X.Y의 본문을 그대로 복사>
```

## 6. 작업 절차

1. <단계 1: 먼저 무엇을 확인>
2. <단계 2: 어떤 변경>
3. <단계 3: 어떻게 검증>

## 7. Pre-write 프로토콜 적용 여부

- [ ] **Skip** — HARD LIMIT이 명확하고 변경 < 30줄 / 1 파일. 즉시 구현 가능.
- [ ] **Required** — 복잡도 ↑. Copilot에게 "구현 전 변경 계획을 3~5개 불릿으로 먼저 제시" 요청. 사용자 승인 후 구현.

## 8. 검수 기준 (Acceptance Criteria)

- [ ] HARD LIMIT을 벗어난 변경이 없음 (diff 검토)
- [ ] CI(refactor-guard) 통과
- [ ] `// [CONFIRM:?]` 잔류가 있다면 PR description에 명시
- [ ] <Task별 기능적 검증 항목>
- [ ] 동작 변경이 의도되지 않았다면 외부 동작 동일 확인 (수동 또는 테스트)

## 9. PR 정보

- **Branch:** `refactor/NN-<short-name>`
- **PR base:** `refactor/main` (절대 `main` 아님)
- **PR title:** `[refactor-NN] <한 줄 제목>`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task NN](../docs/refactor/tasks/NN-xxx.md)

  ## 변경 요약
  - <bullet>

  ## HARD LIMIT 준수
  - 파일: <list>
  - 라인: ~N (예상 N±30% 범위 내)

  ## 검수 체크리스트 결과
  - [x] ...
  ```
