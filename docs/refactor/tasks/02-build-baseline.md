# Task 02 — 빌드 베이스라인 캡처

## 1. 한 줄 요약
backend(Gradle)·frontend(Next.js·TypeScript) 빌드 명령을 현재 시점에서 실행하고 결과를 `docs/refactor/baseline.md`에 기록해 리팩토링 이후 회귀 비교 기준점을 만든다.

## 2. 변경 범위 (HARD LIMIT)

- **신규 파일:**
  - `docs/refactor/baseline.md` (신규 생성)
- **수정 파일:** 없음
- **메서드/함수/클래스:** N/A (코드 변경 없음)
- **예상 변경 라인 수:** 신규 ~80~150줄 (빌드 결과 + 메타정보)
- **예상 변경 파일 수:** 1개 (신규)

**중요:** 빌드 중 발견되는 에러/경고는 **이 Task에서 수정 금지**. 기록만 하고 별도 Task로 분리.

## 3. 절대 건드리지 말 것 (Out of Scope)

- 빌드 에러·경고를 발견해도 코드 수정 금지 → `baseline.md`의 "Known Issues" 섹션에 기록만
- `package.json`, `build.gradle`, `tsconfig.json` 등 빌드 설정 변경 금지
- `node_modules/`, `.next/`, `build/` 등 빌드 산출물은 git에 커밋 금지(이미 `.gitignore` 처리되어 있어야 함)
- 의존성 업그레이드 금지

## 4. 컨텍스트 / 의도

리팩토링 시작 전 "지금 빌드가 어떤 상태인가"를 객관적으로 기록한다. Phase 5(마감)에서 동일 명령을 재실행해 회귀 여부를 비교하는 데 사용한다.

- 관련 INV: 해당 없음
- 관련 Drift ID: 해당 없음
- 관련 PRINCIPLES 섹션: §0 (대전제 — "이해하는 코드만" 리팩토링하기 위한 베이스라인)

## 4-1. 의존 관계

- **선행 Task:** 없음
- **후행 Task:** 모든 후속 Task (베이스라인이 있어야 회귀 비교 가능)
- **API surface 변경 여부:** No

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §10 (문서화 원칙) 일부:

- 실제 코드를 읽고 쓴다. 옛 구조나 추측 금지.
- "지금 어떻게 동작한다"로 서술.

> PRINCIPLES.md §6 (실행자 협업 규칙):

- 추측 금지. 모르는 동작은 빈 함수로 두지 말고 `// [CONFIRM:?] ...` 주석으로 질문 남기기.

## 6. 작업 절차 (실행자 직접 실행 — DEC-WORKFLOW-010)

> **실행자(refactor-executor, Sonnet)가 빌드 명령을 직접 실행**하고 받은 실제 출력을 baseline.md에 정리한다.
> 명령을 실제로 실행하므로 출력은 환각이 아니다. **출력을 지어내지 말 것** — 실제 실행 결과만 기록.
> 환경: **WSL Ubuntu bash** 기준 (PRINCIPLES §13). 명령 실패는 정상 — 수정하지 말고 기록.

### 6-1. 메타 정보 수집
```
git rev-parse --short HEAD; node -v; java -version
```
→ baseline.md 메타 섹션 채움.

### 6-2. 백엔드 빌드 (test 제외)
```
cd backend && time ./gradlew clean build -x test 2>&1 | tail -30; echo "Exit: ${PIPESTATUS[0]}"
```
→ 종료 코드·소요 시간(real)·마지막 20줄·경고 개수를 Backend 섹션에 기록.

### 6-3. 프론트엔드 의존성 설치
```
cd frontend && time npm ci 2>&1 | tail -20; echo "Exit: ${PIPESTATUS[0]}"
```
→ install 섹션 채움. (네트워크/락파일 문제로 실패하면 그대로 기록 + 이후 단계는 가능한 만큼 진행.)

### 6-4. 프론트엔드 type-check
```
cd frontend && time npm run type-check 2>&1 | tail -30; echo "Exit: ${PIPESTATUS[0]}"
```
→ 종료 코드·에러/경고 개수를 type-check 섹션에 기록.

### 6-5. 프론트엔드 build
```
cd frontend && time npm run build 2>&1 | tail -50; echo "Exit: ${PIPESTATUS[0]}"
```
→ build 섹션 + Next.js Route별 size 표(Page / Size / First Load JS) 기록.

### 6-6. 테스트 파일 카운트
```
find backend/src/test -type f -name '*.java' 2>/dev/null | wc -l
find frontend -path '*/node_modules' -prune -o -type f \( -name '*.test.*' -o -name '*.spec.*' \) -print 2>/dev/null | wc -l
```
→ 테스트 파일 개수 섹션 채움.

### 6-7. baseline.md 최종본 작성

실제 실행 결과로만 `docs/refactor/baseline.md` 작성. **실행 못 한 항목(명령 부재·환경 미비 등)은 `<실행 불가: 사유>` 표기. 출력 날조 금지.**

### baseline.md 골격

다음 골격으로 작성:

```markdown
# Build Baseline

> 리팩토링 시작 시점의 빌드 상태 스냅샷. Phase 5에서 동일 명령으로 회귀 비교.

## 메타
- 측정 일시: <YYYY-MM-DD HH:MM TZ>
- Git: branch `refactor/main`, commit `<sha>`
- OS/Shell: <환경>
- Node 버전: `node -v` 결과
- Java 버전: `java -version` 결과 (백엔드)

## Backend (Gradle)

명령: `./gradlew clean build -x test`

- 종료 코드: <0 또는 N>
- 소요 시간: <Xs>
- 경고 개수: <N>
- 결과 마지막 20줄:
  ```
  <붙여넣기>
  ```

## Frontend (Next.js)

### npm ci
- 종료 코드: <>
- 소요 시간: <>

### npm run type-check
- 종료 코드: <>
- 에러 개수: <>
- 경고 개수: <>

### npm run build
- 종료 코드: <>
- 소요 시간: <>
- 번들 크기 요약(Next.js 출력):
  ```
  <붙여넣기 — Route별 size·First Load JS>
  ```

## 테스트 파일 개수
- 백엔드 (`backend/src/test/**/*.java`): <N>개
- 프론트엔드 (`*.test.*`, `*.spec.*`): <N>개

## Known Issues (이번 빌드에서 발견됨)

> 발견된 에러/경고를 기록만. 본 Task에서 수정 금지.

- [ ] <issue 1: 파일 경로 + 한 줄 요약>
- [ ] <issue 2>

각 issue는 Phase 1 Drift Inventory 또는 Phase 4 버그 Task로 후속 처리.
```

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — 절차가 6-1~6-7로 명확. 코드 변경 없음(문서 1개 생성).
- **실행자가 명령을 직접 실행**하고 받은 실제 출력만 기록 (날조 금지 — PRINCIPLES §13).
- 빌드 실패 시 **수정 시도 금지**. 결과를 그대로 기록하고 Known Issues 섹션에 모음.

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `docs/refactor/baseline.md` 신규 생성됨
- [ ] **모든 출력은 실행자가 실제 실행한 결과 그대로** (날조한 출력 0건)
- [ ] 실행 불가 항목은 `<실행 불가: 사유>` 표기
- [ ] backend·frontend 빌드 결과 기록 (성공·실패 무관)
- [ ] 종료 코드·소요 시간·경고 개수 명시 (사용자 응답에 있는 한)
- [ ] 테스트 파일 개수 기록
- [ ] Known Issues 섹션에 발견된 모든 에러/경고 기록 (수정 시도 X)
- [ ] 코드 변경 0줄 (HARD LIMIT 준수)
- [ ] CI(refactor-guard) 통과

## 9. PR 정보

- **Branch:** `refactor/02-build-baseline`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-02] 빌드 베이스라인 캡처`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 02](../docs/refactor/tasks/02-build-baseline.md)

  ## 변경 요약
  - `docs/refactor/baseline.md` 신규 작성
  - backend gradle build, frontend type-check + build 결과 기록
  - 테스트 파일 개수 스냅샷

  ## 빌드 상태 요약
  - Backend: <성공/실패>
  - Frontend type-check: <성공/실패>
  - Frontend build: <성공/실패>

  ## Known Issues (수정하지 않고 기록만)
  - <list>

  ## HARD LIMIT 준수
  - 파일: `docs/refactor/baseline.md` 신규 1개만
  - 코드 변경 0줄

  ## 검수 체크리스트 결과
  - [x] CI 통과
  ```
