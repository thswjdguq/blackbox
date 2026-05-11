# Task 04 — PR 템플릿 추가

## 1. 한 줄 요약
`.github/PULL_REQUEST_TEMPLATE.md`를 신규 추가해 모든 PR이 작업지시서 링크·HARD LIMIT 준수·DRIFT_INVENTORY ID·검수 체크리스트를 명시하도록 강제한다.

## 2. 변경 범위 (HARD LIMIT)

- **신규 파일:**
  - `.github/PULL_REQUEST_TEMPLATE.md` (신규 생성)
- **수정 파일:** 없음
- **메서드/함수/클래스:** N/A
- **예상 변경 라인 수:** ~50~70줄 (신규 템플릿)
- **예상 변경 파일 수:** 1개 (신규)

## 3. 절대 건드리지 말 것 (Out of Scope)

- 기존 `.github/copilot-instructions.md`, `.github/workflows/refactor-guard.yml` 수정 금지
- 다른 어떤 파일도 수정 금지

## 4. 컨텍스트 / 의도

GitHub은 `.github/PULL_REQUEST_TEMPLATE.md`가 있으면 새 PR 본문을 자동으로 이 양식으로 채운다. 리팩토링 기간 동안 모든 PR이 동일 양식을 따르게 만들어:
- 작업지시서 추적이 누락되지 않게
- HARD LIMIT 위반 검수가 강제되게
- DRIFT_INVENTORY 결정사항과 PR을 연결되게

- 관련 INV: 해당 없음
- 관련 Drift ID: 해당 없음
- 관련 PRINCIPLES 섹션: §5 (형상관리 — PR 규칙), §11 (검수)

## 4-1. 의존 관계

- **선행 Task:** 없음
- **후행 Task:** 모든 후속 Task (PR 작성 양식이 됨)
- **API surface 변경 여부:** No

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §5 (형상관리) 일부:

- PR은 항상 **base: `refactor/main`**. `main`에 직접 PR/push/merge 금지.
- 한 커밋에는 한 종류 변경만: `rename:` / `move:` / `refactor:` / `fix:` / `feat:` / `docs:` / `chore:`
- PR 제목에 task ID 포함: `[refactor-NN] 짧은 설명`

> PRINCIPLES.md §11 (검수):

각 PR 머지 전 사용자 또는 Claude가 `docs/refactor/REVIEW_CHECKLIST.md`로 검수한다. 통과 못 하면 머지 금지.

## 6. 작업 절차

### 6-1. `.github/PULL_REQUEST_TEMPLATE.md` 신규 작성

다음 내용으로 정확히 작성:

```markdown
## 작업지시서
<!-- 이 PR이 어떤 Task를 구현하는가 -->
- Task: [refactor-NN](../docs/refactor/tasks/NN-xxx.md)
- 의존: <선행 Task ID 또는 "없음">

## 변경 요약
<!-- 무엇이 왜 바뀌었는가. 3~5개 불릿 -->
-
-

## HARD LIMIT 준수
<!-- 작업지시서의 §2 HARD LIMIT 범위 안에서 작업했음을 확인 -->
- 변경 파일 (작업지시서와 일치):
  - `<path>`
- 변경 라인 수: 약 N줄 (작업지시서 예상치 ±30% 이내)
- HARD LIMIT 외 파일 수정: ❌ 없음 / ⚠️ 있음 (사유: <description>)

## DRIFT_INVENTORY 참조
<!-- DRIFT_INVENTORY에 등록된 항목을 처리한 PR이라면 ID 명시 -->
- 처리한 Drift ID: <D-XX> 또는 "해당 없음"
- 결정 타입(D1~D4): <type> 또는 "해당 없음"

## 동작 변경 (refactor 외)
<!-- 외부에서 본 동작이 의도적으로 변경되었는가 -->
- [ ] 이 PR은 동작 보존만 (refactor 전용)
- [ ] 이 PR은 의도적 동작 변경 포함 (별도 fix/feat 커밋으로 분리됨)

## 검수 체크리스트
<!-- REVIEW_CHECKLIST.md 또는 작업지시서 §8 acceptance와 동기화 -->
- [ ] 작업지시서 §8 acceptance 모든 항목 충족
- [ ] HARD LIMIT 외 파일·메서드 수정 없음 (diff 검토)
- [ ] CI(refactor-guard) 통과
- [ ] PR diff 크기 가드 통과 (작업지시서 예상치 안)
- [ ] `// [CONFIRM:?]` 잔존 시 본 PR description에 명시
- [ ] 동작 변경 시 SMOKE_TESTS 해당 시나리오 수동 통과 확인

## CONFIRM 사항 (있으면)
<!-- Copilot이 남긴 [CONFIRM:?] 주석 또는 결정 보류 항목 -->
- 없음 / <list>

## Pre-write 프로토콜
- [ ] Skip (작업지시서 §7에 따름)
- [ ] Required (계획 먼저 제시 → 사용자 승인 후 구현 — 본 PR description 또는 별도 issue에 계획 링크)

## 참고
- PRINCIPLES: docs/refactor/PRINCIPLES.md
- 본 PR base는 반드시 `refactor/main` (절대 `main` 아님)
```

### 6-2. 파일 위치 확인
```bash
ls -la .github/PULL_REQUEST_TEMPLATE.md
```
존재하면 OK. 부적절한 위치에 들어갔다면 이동.

### 6-3. (선택적) 동작 검증
이 Task의 PR을 만들 때 자동으로 위 템플릿이 본문에 채워지는지 확인 가능 (단, 이 Task PR 자체는 템플릿이 적용되지 않을 수 있음 — 머지 후부터 적용).

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — 단일 신규 파일. 내용 명확. 즉시 작성 가능.

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `.github/PULL_REQUEST_TEMPLATE.md` 신규 생성
- [ ] 위 §6-1의 섹션 모두 포함 (작업지시서·변경 요약·HARD LIMIT·DRIFT·동작 변경·검수·CONFIRM·Pre-write·참고)
- [ ] base 브랜치가 `refactor/main`이라는 점 명시 포함
- [ ] 코드 변경 0줄
- [ ] 다른 파일 수정 없음 (HARD LIMIT 준수)
- [ ] CI(refactor-guard) 통과

## 9. PR 정보

- **Branch:** `refactor/04-add-pr-template`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-04] PR 템플릿 추가`
- **PR body:** (이 PR은 템플릿 적용 전이므로 자유 양식)
  ```markdown
  ## 작업지시서
  - [Task 04](../docs/refactor/tasks/04-add-pr-template.md)

  ## 변경 요약
  - `.github/PULL_REQUEST_TEMPLATE.md` 신규 — 모든 후속 PR이 동일 양식 사용

  ## 효과
  - 머지 후 새 PR 작성 시 자동으로 본 템플릿이 본문에 채워짐
  - 작업지시서 링크·HARD LIMIT·DRIFT 참조·검수 항목이 누락되지 않음

  ## HARD LIMIT 준수
  - 변경 파일: 신규 1개
  - 코드 변경 0줄
  ```
