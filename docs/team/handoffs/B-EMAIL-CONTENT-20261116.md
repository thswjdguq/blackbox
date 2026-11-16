# 이메일 알림 내용·수신 정책 기반

- 기준 main `035be3f2` / 브랜치 `codex/B-email-content` / K-41 0.1 초안 #197 검토 대기.
- 기존 API·앱 알림 발행·DB·라이브러리·실행 설정은 변경하지 않는다. 신규 클래스는 아직 실제 발송 경로에 연결하지 않는다.

## 구현

EmailNotificationPolicy는 명시적 활성 값과 단일 ASCII 주소 허용 목록을 함께 검사한다. 같은 프로젝트의 현재 팀장·팀원, 알림 수신자 id, 행위자 제외를 모두 대조한다. 주소의 도메인만 소문자화하며 local-part 대소문자/점/플러스 주소를 임의로 합치지 않는다. 탈퇴한 멤버는 현재 멤버 인자를 주지 않아 거부한다. 실제 canReview 후보 생산과 요청 대기/대상 존재 확인은 후속 worker/이벤트 생산자에서 수행해야 하며 이 클래스가 대신한다고 보지 않는다.

EmailNotificationComposer는 지원하는 두 유형의 UUID로 고정 서비스 출처의 기존 업무·검토 탭 링크를 만든다. 제목/이름의 제어문자·줄바꿈을 한 줄로 정리하고 한글·이모지를 코드 포인트 경계에서 줄인다. plain-text 본문만 만들고 메일 주소·댓글·첨부 파일을 포함하지 않는다. 공개 출처는 HTTPS, 개발은 localhost/loopback HTTP만 허용하며 사용자정보·쿼리·fragment·경로가 있는 base URL은 거절한다. Content.toString은 본문을 숨긴다.

EmailNotificationTransport는 A의 SMTP 어댑터 연결점이다. ACCEPTED와 실제 받은편지함 도착을 구분하고 일시/영구/인증/전달 불명 결과를 표현한다. deliveryId를 같은 Message-ID에 사용할 수 있도록 전달한다. SMTP 구현이나 자동 worker를 등록하지 않는다.

## 검증

Java25.0.3 / Gradle9.8.2에서 EmailNotificationContentTest8 + 기존 NotificationServiceTest8, **16/16**, 실패·오류·건너뜀0. 모두 단위 검사다. 비활성/빈 허용 목록, 현재 기여자·본인·다른 프로젝트·다른 수신자, 주소 확장/헤더 구문, 고정 원문 링크, 잘못된 base URL, 한글·이모지와 줄바꿈을 확인했다. git diff --check 통과.

실제 DB·로컬 SMTP·Gmail 수신·브라우저·운영 적용은 수행하지 않았다. 실제 비밀값을 읽거나 복사해 테스트에 사용하지 않았다. SMTP 의존성, A의 설정/SQL/이벤트, 계약 합의 후 후속 구현과 실제 통합 검증이 필요하다.
