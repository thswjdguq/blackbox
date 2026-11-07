# K-54 · 개인 업무 알림

v0.1 / DRAFT / 2026-10-14. Codex 소비자 초안, Claude 서버 검토·구현 예정. 현행 API가 아니며 완료 기능으로 노출하지 않는다. 엑스포 임시 서버 담당은 Claude, 프론트 담당은 Codex. 공통 인증 및 ProblemDetail은 기존 구현 유지.

## 최소 범위

기존 위험 `/alerts` 및 처리 대기 `/feedback`를 대체하지 않는 개인 이벤트 알림. 읽음은 확인 요청 처리 완료가 아니다. 피드백함은 처리할 일, 알림함은 새 소식이며 두 숫자를 합산하지 않는다.

| type | 발생 | 수신자 |
| --- | --- | --- |
| TASK_FILE_UPLOADED | 업무 파일 버전 저장 성공 | 등록자를 제외한 현재 팀장·팀원 |
| TASK_COMPLETION_REQUESTED | 업무 완료 확인 요청 성공 | 현재 canReview 조건을 충족하는 팀장·팀원 |
| TASK_REVISION_REQUESTED | 보완 결정 성공 | 결정자를 제외한 현재 담당자 및 요청자, 중복 제거 |
| TASK_OPINION_REQUESTED | 명시적 의견 요청 성공 | 지정 수신자 |
| TASK_OPINION_REPLIED | 요청에 답글 저장 성공 | 답글 작성자를 제외한 원 요청자 |

일반 댓글 전체 알림·일반 자료실 업로드·최종본 알림은 후속 범위. 첫 구현은 자동 묶음/시간 지연 없이 이벤트당 1건으로 단순화한다. 파일 업로드와 이후 확인 요청은 서로 다른 이벤트다. 추후 화면에서 같은 업무별로 묶을 수 있으나 서버가 의미를 소실시키지 않는다.

## API 초안

실제 HTTP는 `/api` 접두사. 프론트 api 인스턴스에는 접두사를 중복하지 않는다. 인증된 현재 사용자만 대상으로 하며 recipientId를 클라이언트가 지정하지 않는다.

- GET `/projects/{projectId}/notifications?limit=20&cursor=...&unreadOnly=false`
- GET `/projects/{projectId}/notifications/unread-count` → 200 `{ "unreadCount": 3 }`
- PUT `/projects/{projectId}/notifications/{notificationId}/read` → 200 `{ "id": "notification-uuid", "readAt": "2026-10-14T10:00:00Z" }`
- 전체 읽음/삭제/읽지 않음으로 변경은 이번 범위 제외.

목록 200 예시(식별자는 설명용이며 실제 UUID 사용):

```json
{
  "items": [{
    "id": "notification-uuid",
    "type": "TASK_FILE_UPLOADED",
    "projectId": "project-uuid",
    "actorName": "현우",
    "taskId": "task-uuid",
    "taskTitle": "현장 조사 결과 정리",
    "fileId": "immutable-file-version-uuid",
    "fileName": "현장조사.pdf",
    "fileVersion": 2,
    "messageId": null,
    "createdAt": "2026-10-14T09:00:00Z",
    "readAt": null,
    "targetAvailable": true
  }],
  "nextCursor": null,
  "unreadCount": 3
}
```

- 모든 예시 키를 반환. fileId/fileName/fileVersion은 파일 비관련이면 null, messageId는 대화 비관련이면 null. 파일 식별자는 불변 버전 레코드 ID이며 논리 파일 ID로 바꾸지 않는다.
- actorName/taskTitle은 이벤트 당시 표시용 스냅샷. 알림 본문에 코멘트 전체나 이메일은 저장하지 않는다.
- taskId는 업무 알림의 필수 필드. 삭제 후에도 식별자는 유지하고 targetAvailable=false로 제공한다. 이동 시 실제 리소스 API 권한 재검증은 필수.
- timestamp는 UTC ISO-8601, 읽지 않음 readAt=null. 읽음 재요청은 최초 readAt 유지(멱등).
- 최신순 createdAt DESC, id DESC의 안정적 cursor 페이지. limit 기본20, 1..50. 빈 목록 items=[], nextCursor=null. unreadCount는 현재 사용자/프로젝트 전체 미읽음 수이며 페이지/필터와 무관.
- unreadOnly 생략=false. cursor 생략=첫 페이지. 빈/변조 cursor 및 limit 범위 위반은 400. cursor는 정렬 위치만 표현하고 권한 증명으로 사용하지 않는다.
- 401 인증 없음, 403 프로젝트 멤버 아님, 404 다른 사용자의 알림/다른 프로젝트 알림/없는 알림. 응답은 기존 ProblemDetail. 프로젝트 관찰자는 자신의 알림 조회·읽음만 가능하고 새 업무 알림 수신에서는 제외한다.
- 탈퇴한 사용자는 기존 알림도 조회 불가. 이후 가입한 사용자의 과거 알림 소급 생성 없음.

## 일관성과 서버 검증

도메인 변경과 알림 저장은 동일 DB 트랜잭션으로 원자적으로 처리하는 방식을 우선 검토한다. 단순 afterCommit 메모리 콜백만으로 유실을 허용하지 않는다. 트랜잭션 커밋 전 알림이 다른 요청에 노출되지 않아야 한다. 외부 이메일·푸시·메시지 브로커 도입 없음.

고유 제약은 (이벤트 발생 식별자, 수신자) 기준. 업무 ID+종류만으로 중복 제거하면 재요청 알림이 사라지므로 금지. 파일은 fileId, 의견은 messageId, 완료/보완은 해당 요청·결정 발생을 구분하는 안정적 키 필요. 기존 서버에 발생 ID가 없으면 구현 전에 구체 키를 이 계약에 추가한다. 새 실제 파일 버전 생성과 동일 이벤트 재처리를 구별한다.

서비스의 최종 기존 권한 판정을 재사용한다. 순환 서비스 참조를 만들지 않는다. 파일 저장의 외부 저장소 실패/DB 롤백 시 알림이 남지 않는지 검사한다. 기존 위험 알림 읽음 상태를 재활용하지 않는다.

## 프론트 연결

기본 이동은 `/projects/{projectId}/board?task={taskId}`이며 실제 보드 query 이름을 연결 전에 재확인한다. 의견은 messageId로 원문 위치, 파일은 fileId로 특정 버전을 강조하도록 후속 프론트 작업에서 연결한다. 서버가 임의 URL/외부 URL을 제공하지 않는다. 읽음 실패 때문에 업무 열기를 막지 않고 실패 상태와 재시도를 제공한다.

서버 준비 전 호출·가짜 성공 UI는 추가하지 않는다. 준비 후 타입/fixture를 이 예시에 맞추고 실제 응답으로 검증한다. 30초 활성 화면 polling 및 창 focus 갱신을 기본으로 검토한다. 미구현 endpoint의 404를 알림 0건으로 숨기지 않는다.

## 서버 담당이 먼저 확정할 항목

1. DTO·경로 충돌, 실제 업무 딥링크 query 확인.
2. 완료 요청/보완의 안정적 발생 키와 동시 요청 중복 정책.
3. 엔티티·인덱스·FK 삭제 정책 및 최신 migration 번호. 번호는 아직 예약하지 않음.
4. 실제 계약 fixture 및 GET/읽음 응답, 오류 예제.

권한·본인 제외·수신자 중복·롤백·동시 재처리·읽음 독립·재요청·cursor 동률·탈퇴·삭제 대상 테스트 후 IMPLEMENTED로 표시한다. DB migration 및 4인 실제 수신 검증은 단위 테스트와 따로 기록한다.
