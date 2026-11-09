# B · 캘린더 연동 실패·취소 화면 복귀

- 기준 main `efbfabfb`, 작업 `codex/b-calendar-oauth-feedback`, #164·#167 후속
- K-CALENDAR-SECURITY 0.2 DRAFT: 브라우저 callback의 거절 응답만 설정 화면으로 돌린다. 일반 캘린더 API의 401/403/404는 유지한다.

## 결과

정상 코드 교환/저장이 끝나면 기존 `calendar=connected`, state 누락·만료·변조·재사용·다른 계정/쿠키 누락 또는 동의 취소/code 누락/교환 실패면 `calendar=error`로 302 복귀한다. #167의 고정 안내가 이를 받는다.
state 검사와 일회성 소비를 생략하지 않으며, 유효한 동의 취소/교환 실패도 다시 사용할 수 없다. 성공·실패 모두 callback 쿠키를 지운다. 오류 원문·code·state·토큰은 URL/화면에 넣지 않는다. provider 예외는 종류만 로그에 남긴다.

## 검사

- Java25.0.3 / Gradle9.8.2 / PostgreSQL16 전용 DB `bb_b_oauth_feedback_20261109`.
- OAuth MVC 6 + 프로젝트 DB/인증 필터 MVC 3 + 일회성 state 6 + 서비스 접근 경계 6 = **21/21 통과** (55초).
- 정상 이동, 누락/재사용/다른 계정 거절 복귀, 동의 취소, 빈 요청/code, provider 실패의 고정 안내·쿠키 삭제·재사용 거절을 확인했다. 권한 거절 API는 계속 403이며 외부 요청은 호출하지 않는다.
- mock provider만 사용했다. 실제 Google 승인/토큰 교환·실제 브라우저 확인은 하지 않았다. 오류 JSON을 없애는 것이 실제 연동 성공을 증명하지 않는다.
- SQL/migration/스키마/의존성/프론트/운영·시연 자료 변경 없음. 로컬 전체 검사는 반복하지 않고 PR CI의 전체 검사를 별도 확인한다.

## 다음

A의 서버 검토·작성자 외 병합, C의 #167 안내 동작 확인. 실제 Google 연동은 키가 있는 A 환경에서 별도 확인한다. #160 이후 합쳐지면 신규/기존 B HTTP 테스트의 Boot4 패키지도 최신 main에 맞춘다.

## #160·#161 병합 뒤 재검증

- main `4b0834ba`(Java 25 / Spring Boot 4.1)를 일반 merge했다. 기존 CalendarProjectAccessHttpTest의 Boot 4 MockMvc import와 이 PR의 실패 302 검사를 함께 보존했다. callback/state 정책은 추가 변경하지 않았다.
- Java 25.0.3 / Gradle 9.8.2 / PostgreSQL 16 새 전용 DB `bb_b_oauth_boot4_20261109`: OAuth MVC 6 + DB/인증 필터 MVC 3 + state 6 + 서비스 접근 6 = **21/21 통과**, 실패·오류·건너뜀 0. 1분 35초.
- 이어 main `e6a8b6e4`의 #161(프론트 보안·Node 26)을 일반 merge했다. 이 추가분은 backend 소스/설정을 바꾸지 않으므로 관련 서버 검사를 반복하지 않았다. 최종 head의 전체 Java 25·Node 26·DB 검사는 PR CI로 확인한다.
- 새 DB만 사용했고 합성 자료는 롤백했다. 실제 Google 승인·토큰 교환·브라우저·운영 적용은 여전히 미검증이다. A 서버 재검토·C 소비자 확인과 작성자 외 병합이 남아 있다.
