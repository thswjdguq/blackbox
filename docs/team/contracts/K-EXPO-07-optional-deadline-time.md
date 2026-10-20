# K-EXPO-07 / v0.1 / 로컬 시연 환경 승인

- 기존 제출물 생성·수정·조회 API에 dueTime: string|null 추가. 기존 dueDate: YYYY-MM-DD는 유지.
- dueTime은 HH:mm(00:00~23:59), 한국 시간 Asia/Seoul. 생략/null은 시간 미지정. 빈 문자열·초·잘못된 시간은 400. PUT은 전체 교체이므로 생략/null은 기존 시간 해제.
- DB: deliverables.due_time TIME(0) NULL. 기존 행은 NULL 그대로이며 23:59로 보정하지 않는다. 로컬 V31_3 예약; 공유 DB는 A가 전체 번호와 적용 순서를 재조정한다.
- UI 기본 날짜만 입력. ‘마감 시간 추가’로 선택 입력, 해제 가능. 저장된 시간이 있으면 수정 시 펼친 상태. 미지정 표시 유지.
- 기한 표시: 지정 시간까지는 날짜 기준 D-day 표시, 지정 시각 이상이면 기한 지남. 미지정은 기존 날짜 기준 유지. 확정·제출 상태 표시 우선.
- AI DELIVERABLE_PLAN deliverable.dueTime에도 같은 필드·검증 적용. 안내문에 명시된 시간만 제안, 불명확하면 null. 읽기 도구에서도 시간과 기준 시간대를 설명한다.
- 권한·프로젝트 경계·확정 후 수정 제한·기존 상태 전환은 유지. 신규 전용 API 없음.
- 예: {"title":"발표 자료","dueDate":"2026-11-05","dueTime":"18:00","description":"","submissionMethod":"LMS","ownerId":null}.
- 호환: 이전 요청은 시간 미지정으로 저장. 프론트는 이전 응답의 필드 생략을 미지정으로 처리. 오래된 클라이언트의 전체 PUT은 시간을 해제하므로 배포는 서버·화면을 함께 갱신.

