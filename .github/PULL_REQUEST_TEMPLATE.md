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
<!-- 실행자가 남긴 [CONFIRM:?] 주석 또는 결정 보류 항목 -->
- 없음 / <list>

## Pre-write 프로토콜
- [ ] Skip (작업지시서 §7에 따름)
- [ ] Required (계획 먼저 제시 → 사용자 승인 후 구현 — 본 PR description 또는 별도 issue에 계획 링크)

## 참고
- PRINCIPLES: docs/refactor/PRINCIPLES.md
- 본 PR base는 반드시 `refactor/main` (절대 `main` 아님)
