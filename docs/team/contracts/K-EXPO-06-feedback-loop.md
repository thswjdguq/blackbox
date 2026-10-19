# K-EXPO-06 피드백·알림 연결

2026-10-19 사용자 승인: 업무와 최종 제출물 양쪽 연결, DB·API까지 엑스포 임시 담당 범위.
K-54의 기존 업무 알림은 유지한다. 기존 미커밋 작업과 기록을 보존한다.

## 수신과 반영
- 일반 업무 댓글: 현재 팀장·팀원에게 알림, 작성자·관찰자 제외. 지정 의견 요청: 지정 수신자에게만 알림.
- 업무 원문(COMMENT/REQUEST)에 답글: 원 작성자에게 알림. 지정 요청은 지정 수신자의 답변만 ANSWERED. 일반 댓글은 타인의 답글이 있으면 RESPONDED. 답글·업로드를 실제 반영 완료로 자동 판정하지 않는다.
- 업무 파일: 기존 TASK_FILE_UPLOADED 알림으로 다른 팀원(피드백 작성자 포함)에게 알린다.
- 최종 제출물 피드백: 제출 담당자와 검토 파일 업로더에게 알림. 둘 다 없으면 검토 회차 개설자. 본인·관찰자·탈퇴자는 제외.
- 해결 기록: 원 작성자에게 알림. 중복 해결 요청은 추가 알림 없음. 다시 열기 후 재해결은 새 이벤트.
- 새 검토 회차: 해당 제출물의 피드백 작성자들에게 알림. 아직 검토 회차에 연결하지 않은 일반 자료실 파일 업로드는 제출물 수정으로 추정하지 않는다.

## 기존 API 확장
- GET /projects/{id}/feedback: received/sent/revisions 유지. kind에 COMMENT/REVIEW 추가. 답변된 요청도 유지.
- InboxItem에 status, response, deliverableId, reviewId 추가. REVIEW의 taskId=null, messageId는 검토 댓글 ID, taskTitle은 목록 제목. 원문 링크는 최종 제출물의 검토 탭이다.
- status: OPEN / RESPONDED / ANSWERED / RESOLVED / REFLECTION_PENDING. response는 최신 타인 답글 또는 해결 사유. 해결 사유는 기존 API의 선택 입력 규칙 유지.
- 개인 알림에 deliverableId/deliverableTitle/reviewId/commentId 추가. 검토 알림은 taskId=null. 기존 taskTitle 저장 열은 제목 스냅샷으로 재사용한다.
- 새 type: TASK_COMMENT_POSTED, REVIEW_FEEDBACK_RECEIVED, REVIEW_FEEDBACK_RESOLVED, REVIEW_FEEDBACK_REOPENED, REVIEW_VERSION_POSTED.
- 모든 발행은 원 명령과 같은 트랜잭션. 사용자별 읽음·중복 방지·프로젝트 권한 유지. 기존 댓글은 목록에 보이지만 과거 알림을 소급 발행하지 않는다.

## DB 번호
V32는 #133 예약. 사용자 DB/API 담당 승인 후 로컬/인계 문서에서 미사용인 V31_1(31.1)을 사용하여 V31→31.1→32 순서를 유지한다. V30/V31은 수정하지 않는다.
공용 main 병합 전 A가 번호를 다시 확인해야 한다. 이미 V32 이상 적용한 다른 DB에 뒤늦게 이 분기를 적용하지 않는다(별도 순차 migration 조정 필요).

## 화면
피드백함에 업무·최종 제출물 원문과 답변/반영 기록을 표시한다. 종 아이콘은 개인 알림, 삼각형은 기존 팀 위험 알림. 개인 알림은 화면 활성 시 10초 간격 갱신한다. 웹 푸시·이메일 알림은 포함하지 않는다.
