# B · 프로필 이메일 수신 설정 API 경계

## 최신 체크포인트 (11/23 실제 인증 연결)

- 아래 초기54건 기록 이후 A #228이 수정 검토·HTTP9/9·마이그레이션4/4·CI4를 거쳐 main `dd19913c`로 병합됐다. 현재 B 브랜치도 일반 merge로 반영했다. 기존 migration/번호는 수정하지 않았다.
- B 설정 저장 인터페이스는 JDBC 클래스 하나로 합쳤다. 계정 행 잠금으로 최초 동시 저장을 직렬화하며, 행 없음+off는 INSERT하지 않고 같은 값은 UPDATE하지 않는다. 실제 변경은 RETURNING의 DB 시각을 사용한다.
- A의 User 인증 시각과 availability를 소비하는 실제 Reader를 추가하고 서비스를 필수 주입으로 바꿨다. 같은 영속성 문맥의 오래된 User를 돌려주지 않게 refresh한다.
- 전용 `bb_b_prefs_real_20261123`에서 변경 후 관련 **37/37** 통과(실제 저장8·전이7·서비스10·MVC8·JWT HTTP4). JWT HTTP의 저장/인증 읽기는 대역이다. 초기54/worker145와 중복 실행을 합산하지 않는다.
- 새 전용 DB `bb_b_settings_link_20261123`에서 **50/50** 통과: 기존37+실제JWT/저장/Reader/A 인증 API 결합4+A 인증 HTTP9. 새 결합4는 Store/Reader를 대역으로 바꾸지 않고 SMTP만 대역이다. 미인증 동의→확인 요청→실제 confirm→GET 인증됨→해제, 인증만으로 자동 동의하지 않음/대상 위조 방지, 같은 영속성 문맥의 JDBC 인증 갱신 재조회, 혼합 대소문자 주소와 A availability 문장을 확인했다.
- A #229 코드 검토·C #227 실제 프로필 연결을 요청한다. 실제 Gmail·프로필 브라우저·Mailpit 수신·worker 전체 흐름·운영 적용은 아직 미검증이다. 이전54/37/145와 합산하지 않는다.

## 초기 경계 구현 이력

- 기준 main `3e206b14` / K-41 0.5(#221 승인·병합) / 2026-11-23.
- B-EMAIL-SETTINGS, Draft. A의 V40·실제 인증 읽기와 B 저장 어댑터 연결 전이며 일반 회원 기능 완료가 아니다.

## 구현

GET/PUT `/api/notifications/email-settings`는 인증된 본인 UUID만 사용한다. JSON의 임의 userId/email/emailVerified는 읽지 않고, enabled만 실제 JSON Boolean일 때 허용한다. 문자열·숫자·배열/객체·누락/null은400이다. 가입 API/화면은 변경하지 않는다.

응답은 K-41의 enabled/emailVerified/verificationRequired/verificationAvailable/verificationUnavailableReason/updatedAt이다. 인증·확인 요청 가용 상태는 A의 실제 조회를 B 소비자 포트로 받아 사용하며, GET이 인증 메일을 요청하거나 SMTP에 연결하지 않는다. 동의 on은 인증이 아니므로 미인증이면 enabled=true와 verificationRequired=true를 즉시 반환한다. 인증된 사용자도 기본off이며 인증 자체가 수신 동의를 만들지 않는다.

수신 의향의 전이 모델은 동일PUT에 기존 시각/동의 버전을 보존한다. off→on은 새로운 enabledAt을 기록하며 지난 대기를 되살리지 않는다. DB 저장 구현은 이 전이를 현재 값에 원자적으로 적용해야 한다. 이 작업의 테스트로 실제 DB 동시 저장을 검증했다고 주장하지 않는다.

실제 저장/인증 읽기 구현이 없으면 `CodedException`의503 사용자 문장으로 거절하며 성공/인증 대역을 운영 bean으로 등록하지 않는다. 기존 ResponseStatusException의5xx는 전역 처리기가 상세 문장을 숨기므로, 통제된 준비 중 응답만 기존 CodedException 경계를 사용한다. DB·인증 장애는 기본off/저장 성공으로 숨기지 않는다.

## 검증

Java25, 관련 **54/54 통과**, 실패·오류·건너뜀0.

| 검사 | 건수 | 범위 |
| --- | --- | --- |
| 수신 의향 전이 | 7 | 기본off, 동일PUT, UTC 마이크로초, 동의 버전, 해제·재활성화 |
| 서비스 단위 | 10 | 미인증/현재 인증 재조회, 가용 상태·서버 문장, 없는 구현503, 장애 전파 |
| 실제 MVC 바인딩/응답 | 8 | principal401, 엄격 Boolean, 대상/인증 입력 위조 무시, JSON shape·503 detail |
| 실제 JWT 필터·PostgreSQL 계정 조회 HTTP | 4 | 무토큰/잘못된/refresh 토큰401, 본인만, 인증된 계정도 기본off, 서버 문장 |
| 기존 알림 HTTP·메일 내용/설정·SMTP 단위 | 25 |6+8+3+8 회귀 |

실제 DB 검사는 전용 `bb_b_email_settings_20261123`에서 실행했다. 계정 조회와 JWT 필터는 실제지만 설정 저장/주소 인증은 테스트 대역이다. V40, 인증 토큰 API·메일함·브라우저·실제 Gmail은 검증하지 않았다. 이전 worker/SMTP 검사와 겹치는 항목을 합산하지 않는다.

첫 단위25건 중1건은 Mockito stubbing 안에서 mock Clock을 호출해 UnfinishedStubbingException으로 실패했다. 시간을 미리 계산한 값으로 바꾼 뒤 JWT 검사/기존 회귀를 포함한54건을 한 번에 통과했다. 제품 코드에서 인증 상태를 완화하거나 검사 실패를 숨기지 않았다.

## A·C 요청과 후속

- A: 예약 V40의 실제 설정 컬럼/type/null 조건과 현재 계정 주소/인증 시각·요청 가용 상태/사용자 문장 읽기 인터페이스 제공. B가 User/Auth/SQL을 임의 변경하거나 마이그레이션 번호를 추가하지 않는다.
- B: 실제 설정 Store와 A 읽기 어댑터를 연결한 뒤 실제 저장·동시 PUT·JWT/HTTP를 검사한다. 이후 #223 Authorization이 이 실제 조회를 소비하도록 별도 연결한다. 이 API는 #223 인터페이스에 의존하지 않아 순환 선행을 피한다.
- C: #227의 화면은 이 응답을 소비하되 실제 API/인증 준비 전 Draft 유지. 리뷰로 남긴 오래된 GET/저장 응답, 잘못된 새 fragment/이전 토큰, sessionStorage 접근 실패3건을 반영하고 실제 백엔드·격리 메일함으로 재검증한다.
- 실제 환경 파일/비밀값·운영 DB/서버는 사용하지 않았고 실제 발송을 켜지 않았다.
