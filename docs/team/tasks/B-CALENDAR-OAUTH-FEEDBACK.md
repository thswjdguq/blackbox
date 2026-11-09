# B-CALENDAR-OAUTH-FEEDBACK · 캘린더 연동 실패 뒤 설정으로 복귀

- 담당 B / 검토 A·C / 기준 main `efbfabfb` / 근거 #164 후속, #167의 B 요청과 사용자 지시
- 허용: CalendarController의 브라우저 callback, 기존 B OAuth 테스트, K-CALENDAR-SECURITY와 B 기록.
- 금지: state 검사 완화, 인증/프로젝트 API 권한 변경, 프론트 중복 편집, DB/migration/의존성/운영 변경.
- 정상 연동은 기존 `calendar=connected`. 만료·변조·다른 계정·쿠키 누락·동의 취소·토큰 교환 실패는 state 검증/일회성 소비를 유지하고 `calendar=error`로 복귀한다.
- 오류 원문·code·state·외부 토큰은 URL/화면/로그에 넣지 않는다. callback 쿠키는 성공·실패 모두 지운다. #167이 이미 고정 실패 안내를 표시한다.
- 실제 Google 승인 왕복은 별도 A 확인이며, 이번 작업은 MVC/전용 DB와 mock provider로 성공/실패 경계를 검사한다.
