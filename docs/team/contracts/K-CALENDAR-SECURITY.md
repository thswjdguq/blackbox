# K-CALENDAR-SECURITY · 기존 캘린더 API의 프로젝트 경계와 연동 콜백

- 버전 0.2 / DRAFT / 생산자 B, 검토 A·소비자 C (#164의 0.1 서버/소비자 승인은 PR 기록에 보존. #167에 맞춘 오류 복귀 추가)
- 작업 B-CALENDAR-SECURITY / 기준 main `9435eb27`
- 새 URL·JSON 필드·DB migration·외부 라이브러리는 추가하지 않는다.

## 프로젝트 경계

- GET `/api/projects/{projectId}/calendar/status`, `/calendar/availability`, `/schedule/team-status`: 해당 프로젝트 멤버만 가능. 비멤버 403, 없는 프로젝트 404.
- POST `/api/calendar/recommend`: 요청의 `projectId`에 대한 멤버 권한을 먼저 확인하고 참석자 전원을 같은 프로젝트의 멤버로 검증한다. 비멤버 또는 다른 프로젝트/없는 참석자는 403. 빈 참석자 목록은 기존처럼 전체 멤버이다.
- POST `/api/projects/{projectId}/calendar/events`: 경로의 프로젝트에 대한 팀장·팀원 권한을 확인한다. 관찰자 403. 참석자 전원 검증을 마친 뒤 외부 API를 호출하며 하나라도 부적격이면 아무 일정도 등록하지 않는다. 빈 목록은 기존처럼 본인이고, 중복 ID는 한 번만 처리한다.
- 추천 및 등록의 요청/성공 응답 모양은 유지한다. 추천은 외부 일정을 읽는 조회이므로 프로젝트 멤버(관찰자 포함)에게 허용하고, 외부 일정 쓰기는 관찰자에게 허용하지 않는다.

## OAuth

- GET `/api/calendar/authorize`는 기존 `authUrl` 응답과 함께 `calendarOAuthState` 쿠키를 설정한다. HttpOnly, SameSite=Lax, callback 경로, 600초. HTTPS 요청(기존 인증과 같은 전달 헤더 판단)은 Secure.
- state는 사용자 ID가 아닌 256비트 난수다. 서버에 사용자·만료 시각을 임시 저장한다. 사용자마다 최신 요청 하나만 남기며, 재발급하면 이전 요청은 무효다. 한 계정의 반복 발급으로 다른 계정의 연동을 막는 공용 한도는 두지 않는다. 만료 기록은 다음 발급 때 정리한다.
- GET `/api/auth/google/callback`은 state와 해당 브라우저 쿠키, 서버 발급 기록을 검사한다. 로그인 계정이 있으면 발급 계정과 같아야 한다. 성공한 state는 외부 코드 교환 전에 한 번만 소비한다.
- state 검사 자체는 누락·변조·만료·재사용·다른 계정/브라우저를 거절하며 Google 코드 교환/토큰 저장을 호출하지 않는다. 브라우저 callback은 JSON 오류 대신 `/profile/settings?calendar=error`로 302 이동한다. 동의 취소(`error`), code 누락/공백, 코드 교환/저장 실패도 같은 고정 안내로 복귀한다. 유효한 state는 동의 취소/교환 실패 때도 일회성으로 소비한다.
- 성공은 기존 `/profile/settings?calendar=connected`로 302 이동한다. 성공·실패 모두 callback 쿠키를 지운다. 오류 원문·code·state·토큰을 리다이렉트 URL/화면에 넣지 않고, 예기치 않은 provider 실패는 예외 종류만 로그에 남긴다.
- 로그인 토큰이 만료돼도 해당 브라우저가 시작한 유효한 연동은 가능하다. 서버 재시작/다중 서버에서 발급 기록이 없으면 다시 시작하도록 거절한다. 영속 state 저장이나 다중 서버 공유는 이번 범위가 아니다.
- 실제 Google 승인 왕복은 클라이언트 키/외부 계정이 있는 환경에서 A·C가 검증해야 한다. 단위·MVC 검사와 실제 외부 연동 완료는 구별한다.

## 화면 확인 요청

C는 기존 버튼이 받은 URL로 같은 브라우저에서 이동하며 쿠키가 callback까지 전달되는지 확인한다. 실패 때 #167의 `calendar=error` 고정 안내가 보이고 설정에서 연동을 다시 시작한다. 다른 캘린더 API의 401/403/404 및 성공 응답은 바꾸지 않는다.
