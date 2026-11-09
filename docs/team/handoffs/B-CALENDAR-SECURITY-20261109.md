# B · 캘린더 프로젝트 경계와 OAuth 연동 보호

- 기준: main `9435eb27` / 작업: `codex/b-calendar-security`
- 관련: #158, #160 / 작업서 B-CALENDAR-SECURITY / 계약 K-CALENDAR-SECURITY 0.1 DRAFT(A·C 검토 요청)

## 결과

연동 상태·가용 시간·추천은 프로젝트 멤버 검사를 먼저 한다. 일정 등록은 경로 프로젝트의 팀장·팀원만 가능하며, 지정 참석자 전원을 같은 프로젝트에서 검증한 뒤 토큰/외부 API에 접근한다. 중복 참석자는 한 번 처리하고 기존 빈 목록 동작은 유지한다.

OAuth 사용자 ID state를 10분 만료 256비트 난수로 대체했다. 서버 발급 기록·브라우저 HttpOnly/Lax 쿠키를 대조하고, 로그인 계정이 있으면 같은 계정인지도 확인한다. 검증과 일회성 소비는 원자적이며 외부 코드 교환 전에 끝낸다. 누락·만료·변조·재사용·계정 변경을 거절한다. URL·JSON 성공 응답 형태와 callback 이동 경로는 유지한다.

DB/기존 migration/의존성/프론트/공통 인증 파일은 변경하지 않았다. 운영·시연 DB와 업로드는 수정하지 않았다.

## 검증

- Java 17, PostgreSQL 16 전용 DB `bb_b_calendar_20261109`: 신규 17/17 통과(상태 5, 서비스 권한 6, OAuth MVC 3, 실제 DB+인증 필터 MVC 3).
- 전체 `./gradlew build`: 전용 DB `bb_b_calendar_full_20261109`, 총 390건 / 통과 388 / 실패·오류 0 / 실제 AI 키 테스트 2건 건너뜀. 2분 6초. 검사 결과는 `backend/build/calendar-full-20261109/`(미커밋 산출물).
- 수정 전 main 소스에 같은 HTTP 권한 검사 2건을 넣어 전용 DB `bb_b_calendar_baseline_20261109`에서 실행: 두 건 모두 `expected 403, was 200`으로 실패. 수정본에서는 통과한다.
- baseline 첫 시도는 다른 검증과 Gradle 캐시를 공유해 잠금 시간 초과로 테스트가 시작되지 않았다. 전체 검사 종료 뒤 순차 재실행해 위 결함을 재현했다. 첫 환경 실패를 코드 실패로 세지 않는다.
- `verify-main-migration-history.cjs`: 기존 main SQL 24개 그대로, 전체 34개 버전 중복 없음. `git diff --check` 통과.

테스트 DB는 모두 새로 만들었고 기존 개발·시연 DB를 쓰지 않았다. Google API·실제 계정·유료 AI 호출은 하지 않았다. MVC 검사는 실제 네트워크 Google 승인 왕복과 다르다.

## 미검증과 다음 단계

- A·C: Google 키가 설정된 환경에서 같은 브라우저의 동의→callback 쿠키 전달→계정 연동 왕복 확인. 이번 테스트는 실제 Google 토큰 교환 성공을 증명하지 않는다.
- state는 단일 서버 메모리에만 있다. 재시작/다중 서버로 발급 기록을 찾지 못하면 거절하고 다시 연동한다. 영속 공유 저장소·추가 migration은 이번 범위가 아니다.
- A: #160도 GoogleCalendarService의 URL builder를 변경하므로 보안 PR 병합 뒤 main을 일반 merge해 양쪽 변경을 보존한다. 새 DB HTTP 테스트의 Boot 3 MockMvc 어노테이션도 Boot 4에서 패키지 변경이 필요하다.
- 작성자 외 A 승인과 C 호환 확인 후 일반 병합. 기존 원본 worktree의 next-env.d.ts 변경은 건드리지 않았다.
