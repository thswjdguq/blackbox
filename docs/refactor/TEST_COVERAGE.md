# Test Coverage Snapshot

> 리팩토링 시작 시점의 테스트 자산 스냅샷. 회귀 위험도 매트릭스로 활용.

## 메타
- 측정 일시: 2026-06-08 20:01 +09:00 (KST)
- Git: branch `refactor/03-test-coverage-snapshot`, commit `e69296b7449ed8a1347936b71339b92d7e1d8c37`
- 관련 baseline: `docs/refactor/baseline.md` (Task 02) — BE-002 항목과 일치

## Backend (Java / JUnit 5 + Spring Boot Test)

### 테스트 프레임워크
- 의존성 (`backend/build.gradle` 확인):
  - `org.springframework.boot:spring-boot-starter-test`
  - `org.springframework.security:spring-security-test`
  - `org.junit.platform:junit-platform-launcher` (testRuntimeOnly)
- 테스트 디렉터리: `backend/src/test/`

### 테스트 파일 목록

명령: `find backend/src/test -type f -name '*.java' | sort`
출력 (실제):
```
backend/src/test/java/com/blackbox/BlackboxApplicationTests.java
```

`@Test` 개수 명령: `find backend/src/test -name '*.java' -print0 | xargs -0 -I {} sh -c 'echo "{}: $(grep -c @Test {})"'`
출력 (실제):
```
backend/src/test/java/com/blackbox/BlackboxApplicationTests.java: 1
```

| 경로 | 대상 production 클래스 | @Test 개수 | 비고 |
|---|---|---|---|
| `backend/src/test/java/com/blackbox/BlackboxApplicationTests.java` | `BlackboxApplicationTests` (컨텍스트 로드 검증) | 1 | `contextLoads()` — Spring ApplicationContext 기동 테스트. 특정 도메인 클래스 미대상. 도메인 분류: 없음(Application-level) |

### 테스트 실행 결과

명령 (WSL Ubuntu 내부 실행): `cd backend && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew test --no-daemon`

결과 — **BUILD FAILED** (Exit: 1, 40s)

```
> Task :compileTestJava
> Task :testClasses
> Task :test

BlackboxApplicationTests > contextLoads() FAILED
    java.lang.IllegalStateException at DefaultCacheAwareContextLoaderDelegate.java:180
        Caused by: org.springframework.beans.factory.BeanCreationException ...
            Caused by: org.flywaydb.core.internal.exception.FlywaySqlException ...
                Caused by: org.postgresql.util.PSQLException ...
                    Caused by: java.net.ConnectException at Net.java:-2

1 test completed, 1 failed
BUILD FAILED in 40s
```

**Pass / Fail / Skip:** 0 / 1 / 0
**소요 시간:** 40s

**실패 원인:**
- 유일한 백엔드 테스트 `contextLoads()`는 **Spring 전체 컨텍스트를 기동**한다 → 기동 중 Flyway가 **실제 PostgreSQL**에 마이그레이션을 실행 시도 → DB 미기동 시 `java.net.ConnectException`(연결 거부)으로 컨텍스트 로드 실패.
- 즉 **테스트 프로파일/인메모리 DB(H2)/Testcontainers가 없어** 이 테스트는 **단독 실행 불가** — `docker compose up`으로 Postgres가 떠 있어야만 통과 가능(추정).
- 실질 의미: 존재하는 1개 테스트조차 독립 회귀 검증에 쓸 수 없음 → **사용 가능한 자동 회귀 커버리지 사실상 0**.

> 참고(환경): 본 Task의 실행자 1차 시도는 Windows Git Bash에서 UNC 경로(`\\wsl.localhost\...`)로 gradle을 돌려 파일 잠금 오류(`잘못된 기능입니다`)로 빌드 시작 자체가 실패했음. 위 결과는 **WSL Ubuntu 내부**에서 재실행해 얻은 실측이다. gradle은 WSL 내부에서 정상 동작하며(Task 02 baseline도 동일), UNC 경로 잠금 문제는 프로젝트가 아니라 셸 환경 아티팩트다.

## Frontend (Next.js / TypeScript)

### 테스트 프레임워크

명령: `grep -E '"jest"|"vitest"|"@testing-library"|"playwright"|"cypress"' frontend/package.json`
출력: (없음) — grep exit code: 1

- 의존성: **없음** (jest, vitest, @testing-library, playwright, cypress 모두 미발견)
- 테스트 디렉터리: 미설정

### 테스트 파일 목록

명령: `find frontend -path '*/node_modules' -prune -o -type f \( -name '*.test.*' -o -name '*.spec.*' \) -print`
출력: (없음) — exit code: 1

| 경로 | 비고 |
|---|---|
| 없음 | 테스트 파일 0개. 프레임워크 미설정. |

## 도메인별 커버리지 매트릭스

> 도메인 목록 출처: `md/handover_log.md` §6 (Auth, Project, Task, Meeting, FileVault, Score, Alert, Calendar, Notion, Discord, Report, Risk)
> 매핑 근거: `BlackboxApplicationTests.contextLoads()` = Spring 전체 컨텍스트 기동 테스트. 특정 도메인 클래스 미대상. 도메인별 테스트 파일 없음.

| 도메인 | Backend 테스트 | Frontend 테스트 | 회귀 위험도 |
|---|---|---|---|
| Auth | N | N | HIGH |
| Project | N | N | HIGH |
| Task | N | N | HIGH |
| Meeting | N | N | HIGH |
| FileVault | N | N | HIGH |
| Score | N | N | HIGH |
| Alert | N | N | HIGH |
| Calendar | N | N | HIGH |
| Notion | N | N | HIGH |
| Discord | N | N | HIGH |
| Report | N | N | HIGH |
| Risk | N | N | HIGH |

**참고:** `BlackboxApplicationTests` (1개 @Test)는 `contextLoads()` — Spring ApplicationContext 초기화 검증. 특정 도메인 비즈니스 로직을 커버하지 않으므로 도메인별 매트릭스에서 Y로 계상하지 않음. (모든 12개 도메인 = HIGH)

## 위험도 판정 기준
- **HIGH**: 자동 테스트 0개. 리팩토링 시 SMOKE_TESTS 수동 실행 필수
- **MEDIUM**: 일부 테스트 있음. happy path가 명확히 커버되지 않음
- **LOW**: happy path 자동화 충분. 단순 리팩토링은 테스트로 회귀 탐지 가능

## 리팩토링 시 활용
- **모든 12개 도메인 HIGH** — Phase 5 진입 전 SMOKE_TESTS(`docs/refactor/tasks/05-smoke-test-scenarios.md`) 해당 시나리오 의무 실행
- 본 리팩토링 기간 내 새 테스트 추가는 범위 외 (`PLAN.md` Phase 0)
- baseline.md `BE-002` 항목과 일치: "백엔드 테스트 파일 1개, 프론트엔드 테스트 파일 0개. 리팩토링 안전망 부재."

## Failing / Skipped 테스트 상세

> 실행 중 발견된 모든 실패·스킵을 기록. 본 Task에서는 수정 금지.

| 테스트 | 상태 | 이유 | 후속 처리 |
|---|---|---|---|
| `BlackboxApplicationTests.contextLoads` | FAILED | Spring 컨텍스트 기동 중 Flyway가 PostgreSQL 연결 시도 → DB 미기동 시 `java.net.ConnectException`. 테스트용 인메모리 DB/프로파일 부재로 단독 실행 불가(Postgres 필요). | Phase 4 또는 별도 테스트 인프라 Task 후보: 테스트 프로파일(H2/Testcontainers) 도입 검토. 단 자동 테스트 보강은 본 리팩토링 범위 외(PLAN Phase 0). |

## 환경 제약 메모

- **측정 환경:** WSL Ubuntu 22.04 내부 bash (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`). gradle은 WSL 내부에서 정상 동작.
- **테스트 실패 = DB 의존:** 유일한 테스트 `contextLoads()`가 Postgres에 의존 → `docker compose up`으로 DB 기동 후에만 통과 가능(추정). 회귀 안전망으로는 사실상 무용.
- **셸 환경 주의(별개 이슈):** Windows Git Bash에서 UNC 경로(`\\wsl.localhost\...`)로 gradle 실행 시 Gradle 8.14가 파일 잠금 실패(`잘못된 기능입니다`). 실행자 Bash 셸이 WSL/Git Bash 중 무엇인지 불확정할 수 있음 → **gradle 류는 WSL 내부에서 실행** 권장.
- 관련 baseline: `BE-002`(테스트 안전망 부재), `BE-001`(Java 버전).
