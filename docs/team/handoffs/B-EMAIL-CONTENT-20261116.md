# 이메일 알림 내용·수신 정책 기반

- 기준 main `035be3f2` / 브랜치 `codex/B-email-content` / K-41 0.3 초안 #197 검토 대기.
- 기존 API·앱 알림 발행·DB·라이브러리·실행 설정은 변경하지 않는다. 신규 클래스는 아직 실제 발송 경로에 연결하지 않는다.

## 구현

EmailNotificationPolicy는 명시적 활성 값과 단일 ASCII 주소 허용 목록을 함께 검사한다. 같은 프로젝트의 현재 팀장·팀원, 알림 수신자 id, 행위자 제외를 모두 대조한다. 주소의 도메인만 소문자화하며 local-part 대소문자/점/플러스 주소를 임의로 합치지 않는다. 탈퇴한 멤버는 현재 멤버 인자를 주지 않아 거부한다. 실제 canReview 후보 생산과 요청 대기/대상 존재 확인은 후속 worker/이벤트 생산자에서 수행해야 하며 이 클래스가 대신한다고 보지 않는다.

NotificationPresentationFactory는 현재 알림 12종의 이름·문장·대상·상대 경로를 한곳에서 만든다. 업무 메시지/파일 위치 id와 검토 댓글 anchor를 보존한다. 모르는 종류도 유효한 대상 경로는 유지하고, 잘못된 혼합/누락 대상은 UNKNOWN/path=null로 이동을 만들지 않는다. 조회 시 저장된 스냅숏을 읽기만 하며 자체 DB 조회는 하지 않는다. 대상의 실제 존재/현재 프로젝트 권한 검사는 후속 앱 응답·발송 경계에서 수행해야 한다.

EmailNotificationComposer는 공통 생성기의 message/label/title/path를 그대로 사용해 두 업무 메일을 만든다. 제목/이름 제어문자 처리·Unicode 자르기도 공통 생성기에서만 수행한다. 종류 이름에 조사를 붙이는 방식 대신 종류별 완성 문장을 사용한다. 메일 주소·댓글 원문·첨부 파일을 포함하지 않는다. 공개 출처는 HTTPS, 개발은 localhost/loopback HTTP만 허용하며 사용자정보·쿼리·fragment·경로가 있는 base URL은 거절한다. Content와 NotificationPresentation의 toString은 내용을 숨긴다.

EmailNotificationTransport는 A의 SMTP 어댑터 연결점이다. ACCEPTED와 실제 받은편지함 도착을 구분하고 일시/영구/인증/전달 불명 결과를 표현한다. deliveryId를 같은 Message-ID에 사용할 수 있도록 전달한다. SMTP 구현이나 자동 worker를 등록하지 않는다.

## 검증

Java25.0.3 / Gradle9.8.2에서 NotificationPresentationFactoryTest8 + EmailNotificationContentTest8 + 기존 NotificationServiceTest8, **24/24**, 실패·오류·건너뜀0. 모두 단위 검사다. 공통 문장/메일 본문·경로의 정확한 일치, 12종 대상, 업무 메시지·파일 위치, 검토 댓글 anchor, 모르는 코드/잘못된 대상, 원본 불변성, 비활성/허용 목록·수신자 경계, 헤더/URL/Unicode를 확인했다. git diff --check 통과.

실제 DB·로컬 SMTP·Gmail 수신·브라우저·운영 적용은 수행하지 않았다. 실제 비밀값을 읽거나 복사해 테스트에 사용하지 않았다. NotificationDtos.Item의 추가 필드와 NotificationService 연결은 아직 구현하지 않았으며 C 소비자 확인 뒤 후속 PR에서 수행한다. SMTP 의존성 뒤 Jakarta Mail 주소 검사 전환, A V39/이벤트, 대기 worker와 통합 검증이 남아 있다.
