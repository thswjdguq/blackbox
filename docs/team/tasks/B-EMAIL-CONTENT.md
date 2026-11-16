# B-EMAIL-CONTENT · SMTP와 분리한 메일 내용·수신 정책

- 담당 B / 검토 A / 기준 main `035be3f2` / Gmail SMTP 연결 준비(2026-11-16).
- K-41 초안 #197의 독립 준비. A SMTP 기반/설정/SQL·계약 합의 전 실제 알림 경로에는 연결하지 않는다.
- 허용: 신규 EmailNotificationComposer/Policy/Transport, 대응 단위 테스트·개인 기록. 기존 A/C 파일·설정·의존성·DB·API 변경 금지.
- 목표: 기본 꺼짐과 명시적인 수신 허용 목록, 현재 프로젝트 기여자/행위자 제외, 고정 출처 원문 링크, 제목 줄바꿈/한글·이모지 처리. 주소·본문을 로그 문자열에 노출하지 않는다.
- 검사: 관련 JUnit 단위 및 기존 NotificationServiceTest. 실제 DB/SMTP/Gmail 발송 검사로 보고하지 않는다.
