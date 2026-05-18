# Task 03 — 테스트 커버리지·존재 여부 스냅샷

## 1. 한 줄 요약
backend·frontend의 현재 테스트 자산을 조사하고 도메인별 커버리지 유무를 `docs/refactor/TEST_COVERAGE.md`로 기록해 리팩토링 시 회귀 위험도를 가시화한다.

## 2. 변경 범위 (HARD LIMIT)

- **신규 파일:**
  - `docs/refactor/TEST_COVERAGE.md` (신규 생성)
- **수정 파일:** 없음
- **메서드/함수/클래스:** N/A (코드 변경 없음)
- **예상 변경 라인 수:** ~100~150줄 (신규 문서 작성만)
- **예상 변경 파일 수:** 1개 (신규)

**중요:** 테스트를 새로 작성·수정·실행 결과 수정 절대 금지. **현황 조사·기록만.**

## 3. 절대 건드리지 말 것 (Out of Scope)

- 새 테스트 파일 추가 금지
- 기존 테스트 파일 내용 수정 금지
- 테스트 프레임워크 설정 변경 금지 (`build.gradle`, `package.json`, `jest.config.js` 등)
- 테스트 실행 중 발견되는 에러 수정 금지 → `TEST_COVERAGE.md`의 "Failing/Skipped" 섹션에 기록만
- 코드·다른 문서 변경 금지

## 4. 컨텍스트 / 의도

리팩토링 시 가장 큰 위험은 **"동작하던 기능이 죽는"** 회귀 버그. 자동 테스트가 없으면 회귀 탐지가 어려움. 본 Task는 다음을 명확히 한다:
- 자동 테스트가 어디까지 있는가 (도메인별)
- 테스트 없는 영역은 어디인가 (회귀 위험 高)
- Phase 2~3 작업 시 어느 도메인을 더 신중히 다뤄야 하는가

자동 테스트 추가는 본 리팩토링 범위 외 (`PLAN.md` Phase 0 정의).
대신 `Task 05 SMOKE_TESTS`가 수동 회귀 검증을 담당.

- 관련 INV: 해당 없음
- 관련 Drift ID: 해당 없음
- 관련 PRINCIPLES 섹션: §0 (대전제 — "이해하는 코드만 리팩토링")

## 4-1. 의존 관계

- **선행 Task:** 02 (build baseline에서 test 파일 개수 1차 파악) — 병렬 가능, 단 02 결과를 참조하면 더 정확
- **후행 Task:** Phase 2 도메인 Task들 (도메인별 위험도 인지에 사용)
- **API surface 변경 여부:** No

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §10 (문서화 원칙):

- 실제 코드를 읽고 쓴다. 옛 구조나 추측 금지.
- "지금 어떻게 동작한다"로 서술.
- 예시 코드는 실제 파일에서 가져온다.

> PRINCIPLES.md §6 (Copilot 협업 규칙):

- 추측 금지. 모르는 동작은 빈 함수로 두지 말고 `// [CONFIRM:?] ...` 주석으로 질문 남기기.

## 6. 작업 절차 (오케스트레이터 + 사용자 실행 패턴)

> **Copilot은 명령을 직접 실행하지 않는다.** 사용자에게 다음 순서로 명령 실행을 요청하고, 받은 출력으로 TEST_COVERAGE.md 작성.
> 환경: **WSL Ubuntu bash** 기준 (PRINCIPLES §13).

### 6-1. 사용자에게 백엔드 테스트 파일 목록 요청

Copilot 발화 예시:
> "다음 명령 실행 후 결과 부탁드려요:
> ```
> find backend/src/test -type f -name '*.java' 2>/dev/null | sort
> ```
> 출력이 없으면 'none'이라고 알려주세요."

추가 요청:
> "각 파일의 @Test 메서드 개수도:
> ```
> find backend/src/test -name '*.java' -print0 2>/dev/null | xargs -0 -I {} sh -c 'echo \"{}: $(grep -c @Test {})\"'
> ```"

### 6-2. 사용자에게 백엔드 테스트 실행 요청

Copilot 발화 예시:
> "다음 명령:
> ```
> cd backend && time ./gradlew test 2>&1 | tail -50; echo \"Exit: $?\"
> ```
> 마지막 50줄과 Exit, time real 값 부탁드려요. 'X tests completed, Y failed' 같은 요약 라인이 있으면 강조해주세요."

받은 출력에서 통과/실패/스킵 개수만 추출. 환각 금지.

### 6-3. 사용자에게 프론트엔드 테스트 자산 확인 요청

Copilot 발화 예시:
> "두 명령 결과 부탁드려요:
> ```
> grep -E '\"jest\"|\"vitest\"|\"@testing-library\"|\"playwright\"|\"cypress\"' frontend/package.json
> echo \"---\"
> find frontend -path '*/node_modules' -prune -o -type f \\( -name '*.test.*' -o -name '*.spec.*' \\) -print 2>/dev/null
> ```
> grep 결과가 비어있으면 '프레임워크 미설정', find 결과가 비어있으면 '테스트 파일 없음'으로 알려주세요."

### 6-4. 도메인별 커버리지 매트릭스 작성 (Copilot 단독 작업)

받은 데이터로 `md/handover_log.md` §6 도메인 목록(Auth, Project, Task, Meeting, FileVault, Score, Alert, Calendar, Notion, Discord, Report, Risk) 기준 매트릭스 작성. 룰은 **객관적**:

- **백엔드 테스트 파일 카운트** (도메인명 매칭, 모호하면 `?`)
- **프론트엔드 테스트 파일 카운트** (없을 가능성 ↑)
- **위험도 자동 룰**: 테스트 0개 = HIGH / 1개 이상 = MEDIUM / (LOW는 본 시점 미사용 — 충분 판정은 추후)

### 6-5. 사용자에게 도메인 매핑 검증 요청

Copilot 발화 예시:
> "도메인별 매트릭스 1차안입니다:
> [표 출력]
> 도메인 매핑이 맞는지 확인 부탁드려요. 잘못 매핑된 항목 있으면 알려주세요."

### 6-6. `docs/refactor/TEST_COVERAGE.md` 최종본 작성

수집·검증한 데이터로만 작성. **사용자 미제공 항목은 `<사용자 미제공>`. 추측·환각 금지.**

### TEST_COVERAGE.md 골격

다음 골격으로:

```markdown
# Test Coverage Snapshot

> 리팩토링 시작 시점의 테스트 자산 스냅샷. 회귀 위험도 매트릭스로 활용.

## 메타
- 측정 일시: <YYYY-MM-DD HH:MM TZ>
- Git: branch `refactor/main`, commit `<sha>`
- 관련 baseline: `docs/refactor/baseline.md` (Task 02)

## Backend (Java / JUnit 5 + Spring Boot Test)

### 테스트 프레임워크
- 의존성: `spring-boot-starter-test`, `spring-security-test`, `junit-platform-launcher` (build.gradle 확인)
- 테스트 디렉터리: `backend/src/test/`

### 테스트 파일 목록
| 경로 | 대상 production 클래스 | @Test 개수 |
|---|---|---|
| `<path>` | `<ClassName>` | <N> |

(파일이 없으면 "없음"으로 1행 기록)

### 테스트 실행 결과
명령: `./gradlew test`
- 종료 코드: <>
- Pass / Fail / Skip: <P> / <F> / <S>
- 소요 시간: <Xs>
- 실패한 테스트:
  ```
  <목록 또는 "없음">
  ```

## Frontend (Next.js / TypeScript)

### 테스트 프레임워크
- 의존성: <확인 결과 — 예: "없음", "jest 설정 발견" 등>
- 테스트 디렉터리: <확인 결과>

### 테스트 파일 목록
| 경로 | 비고 |
|---|---|
| <path> | <설명> |

(없으면 "없음"으로 1행 기록)

## 도메인별 커버리지 매트릭스

| 도메인 | Backend 테스트 | Frontend 테스트 | 회귀 위험도 |
|---|---|---|---|
| Auth | Y/N (file) | Y/N (file) | HIGH/MEDIUM/LOW |
| Project | | | |
| Task | | | |
| Meeting | | | |
| FileVault | | | |
| Score | | | |
| Alert | | | |
| Calendar | | | |
| Notion | | | |
| Discord | | | |
| Report | | | |
| Risk | | | |

## 위험도 판정 기준
- **HIGH**: 자동 테스트 0개. 리팩토링 시 SMOKE_TESTS 수동 실행 필수
- **MEDIUM**: 일부 테스트 있음. happy path가 명확히 커버되지 않음
- **LOW**: happy path 자동화 충분. 단순 리팩토링은 테스트로 회귀 탐지 가능

## 리팩토링 시 활용
- HIGH 위험 도메인 작업지시서: Phase 5 진입 전 SMOKE_TESTS 해당 시나리오 의무 실행
- MEDIUM/LOW 도메인: SMOKE_TESTS 권장
- 본 리팩토링 기간 내 새 테스트 추가는 범위 외 (`PLAN.md` Phase 0)

## Failing / Skipped 테스트 상세

> 실행 중 발견된 모든 실패·스킵을 기록. 본 Task에서는 수정 금지.

| 테스트 | 상태 | 이유(추정) | 후속 처리 |
|---|---|---|---|
| <name> | FAIL/SKIP | <message 1줄 요약> | Phase 4 또는 DEFERRED.md |
```

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — 절차가 6-1~6-6으로 명확. 사용자 응답 의존 구조.
- **명령은 Copilot이 절대 직접 실행하지 않음** (PRINCIPLES §13)

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `docs/refactor/TEST_COVERAGE.md` 신규 생성
- [ ] **모든 출력은 사용자가 실제 실행해서 붙인 내용 그대로** (Copilot이 만들어낸 출력 0건)
- [ ] 사용자 미제공 항목은 `<사용자 미제공>` 표기
- [ ] 백엔드 테스트 파일 목록 + @Test 개수 기록
- [ ] 백엔드 `./gradlew test` 실행 결과 기록 (성공·실패 무관)
- [ ] 프론트엔드 테스트 자산 확인 결과 기록
- [ ] 12개 도메인 × 매트릭스 채움 + 사용자 도메인 매핑 검증 완료
- [ ] HIGH(0개)/MEDIUM(1개+) 룰로 위험도 자동 분류
- [ ] 코드·기존 테스트·빌드 설정 변경 0줄 (HARD LIMIT 준수)
- [ ] CI(refactor-guard) 통과

## 9. PR 정보

- **Branch:** `refactor/03-test-coverage-snapshot`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-03] 테스트 커버리지·존재 여부 스냅샷`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 03](../docs/refactor/tasks/03-test-coverage-snapshot.md)

  ## 변경 요약
  - `docs/refactor/TEST_COVERAGE.md` 신규
  - 백엔드 테스트 자산 + 실행 결과 기록
  - 프론트엔드 테스트 자산 확인 (예상: 미설정)
  - 도메인별 회귀 위험도 매트릭스 (12개 도메인)

  ## 주요 발견
  - 백엔드 테스트 파일 수: <N>
  - 백엔드 테스트 통과/실패: <P>/<F>
  - 프론트엔드 테스트 프레임워크: <설정/미설정>
  - HIGH 위험 도메인: <list>

  ## HARD LIMIT 준수
  - 변경 파일: 신규 1개만
  - 코드·기존 테스트 변경 0줄

  ## 검수 체크리스트 결과
  - [x] CI 통과
  ```
