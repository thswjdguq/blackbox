# EXPO-09 — 완료 기준 선택 처리

- 사용자 요청: 완료 기준 없이 결과 확인 요청 및 알림·피드백 흐름 사용.
- 기준: shinguexpo / e8f41aad. 기존 next-env.d.ts 및 PR-137 검토 기록 보존.
- 범위: TaskWorkflowService와 해당 테스트, TaskModal, TaskCompletionPanel, 인수인계.
- 완료 기준 텍스트는 보존하되 요청·승인의 필수 검사에서 제외한다.
- 담당자/관찰자 권한, 독립 확인자, 파일 필수 설정, 선행 업무, 확정 잠금은 유지한다.
- DB migration 및 응답 필드 변경 없음. checks에서 필수 CRITERIA 항목을 제거한다.
- 로컬 검증본 반영까지 수행한다. 원격 push·배포는 포함하지 않는다.
