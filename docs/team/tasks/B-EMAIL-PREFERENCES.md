# B-EMAIL-PREFERENCES · 회원 이메일 수신 선택과 비차단 주소 확인

- 담당 B / 검토 A(인증·SQL·SMTP), C(가입·설정 화면) / 기준 main `035be3f2`
- 계약: K-41 0.4 7~12장 후속 DRAFT. #200의 초안을 #197에 통합한다. 학기 구현 범위와 초기 SMTP를 구별해 합의 뒤 구현한다.
- B 작성 제안: EmailNotificationPreference 엔티티/저장소·EmailNotificationSettingsService/Controller·DTO·대응 검사, 발송 직전 enabled/현재 주소 확인 시각/마지막 활성 시각과 대기 시각 대조.
- A 작성 제안: SignupRequest/AuthService의 선택값 연동, 현재 주소 확인 상태·인증 API/토큰/재전송 제한, SQL 번호·DDL, 테스트 메일 프로필. 명시적 합의 없이 A 파일을 변경하지 않는다.
- C 작성 제안: 가입 선택 항목 기본 해제, 프로필의 수신 설정/주소 확인, `/email/verify#token=...`의 토큰 제거와 안전한 로그인 복귀.
- 수용: 기존 가입 payload 호환, 기존 회원 기본 off, 주소 확인 없이 가입·로그인·PMS 사용, 미확인 주소 업무 메일 0건, 자기 설정만 변경, 설정 off/주소 변경 뒤 미발송 이벤트 제외, 일반 테스트에 SMTP 불필요.
- 검증: 단위·실제 DB/HTTP·Mailpit 수신·실제 Gmail을 구별한다. 운영 API에 인증 생략용 옵션이나 공용 토큰을 추가하지 않는다. 실제 비밀값을 테스트 fixture에 복사하지 않는다.
