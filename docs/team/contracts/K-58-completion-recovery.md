# K-58 완료 확인 요청 복구

2026-10-16 · EXPO-59 로컬 통합 계약. 기존 K-54 알림 계약 유지.

- `GET /api/projects/{projectId}/tasks/{taskId}/completion`에 `canCancel: boolean` 추가.
- `checks`에 `REVIEWER` 추가. 실제 요청자와 수행자(무담당이면 생성자)를 제외한 팀장/팀원이 있어야 통과한다. 대기 요청은 저장된 요청자를 기준으로 계산한다.
- 독립 검토자가 없으면 `canRequest=false`, 신규 완료 요청은 409: `담당자와 요청자를 제외한 확인 가능한 팀원이 없습니다. 담당자를 조정해 주세요.`
- `POST /api/projects/{projectId}/tasks/{taskId}/completion-cancel`, JSON `{ "reason": "담당자 조정" }`, 성공 200 기존 TaskResponse.
- 현재 요청자 또는 팀장만 취소 가능. 관찰자/비구성원 403, 다른 프로젝트 업무 404. 사유는 공백 제외 1~2000자, 오류 400.
- 유효한 IN_PROGRESS 대기 요청이 없거나 승인 완료/연결된 제출물 최종 확정이면 409. 프로젝트 잠금 및 제출물 잠금으로 승인/취소/확정과 직렬화한다.
- 취소는 IN_PROGRESS와 파일·대화·알림·이력을 보존하고 요청 시각/요청자만 해제한다. TASK_COMPLETION_CANCEL 활동 기록에 기존 요청자·요청 시각·취소자(활동 actor)·취소 시각(활동 시각)·사유를 남긴다.
- 취소 후 담당 조정 및 재요청 가능. 재요청 알림은 새 요청 시각 키를 사용한다. 취소는 새 알림 종류나 migration을 추가하지 않는다.
