# B-EMAIL-SMTP · Gmail SMTP 알림 연결

- 담당 B / 검토 A(서버·설정·DB), C(화면·링크) / 기준 main `035be3f2d1ad46ea807ce776cfd2e181de7d5cdc`
- 공급자: Gmail SMTP. 발신 인증은 서버 환경변수 MAIL_USERNAME/MAIL_PASSWORD로 주입한다.
- 범위: K-41 초안 제안 → 독립적인 B 메일 내용/허용 수신자 정책 → A SMTP 기반·SQL·검토 요청 이벤트 → 앱 알림과 원자적인 발송 대기 기록 → 재시도/발송 검증.
- 허용: 신규 B EmailNotification* 클래스·대응 테스트·본 작업서/개인 인수인계/K-41 초안. 새 HTTP API·DB·A/C 파일·라이브러리 변경은 계약 합의/담당자 PR 전 적용하지 않는다.
- A 요청 파일: backend/build.gradle, application*.yml, .env.example, Compose 및 로컬 실행 스크립트, 새 migration, ReviewRoundService. A가 작성하거나 임시 작성 범위를 명시적으로 합의한다.
- 금지: 기존 migration 변경·번호 자의 배정, 실제 사용자 메일 발송, 실제 DB 변경/삭제, 비밀값 출력, 운영 배포, 개인 알림 응답 변경.
- 수용: 기능 기본 꺼짐/수신 허용 목록 없음이면 발송0, 본인·관찰자·탈퇴/다른 프로젝트 제외, 업무 롤백 시 발송 대기 없음, 커밋 후 SMTP 수행, 중복 예약 방지, 실패해도 업무·앱 알림 유지, 재시작 후 대기 복구. SMTP의 전달 불명 상태에서 정확히 한 번 발송을 보장한다고 표현하지 않는다.
- 검사: B 정책/본문 단위 검사부터 독립 진행. SMTP 수신은 로컬 테스트 서버로, DB는 A 새 migration 뒤 새 전용 DB로 검증한다. 실제 Gmail은 별도 수신 주소와 실제 발송 요청을 받은 뒤 확인한다.

현 로컬 실행 스크립트는 AI 변수만 읽으므로, SMTP 기반 작업에서 MAIL_*의 백엔드 프로세스 전달도 추가한다.
