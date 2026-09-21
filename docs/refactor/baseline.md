# Build Baseline

> 리팩토링 시작 시점의 빌드 상태 스냅샷. Phase 5에서 동일 명령으로 회귀 비교.

## 메타

- 측정 일시: 2026-06-08 19:10 UTC+9 (KST)
- Git: branch `refactor/02-build-baseline`, commit `1476924`
- OS/Shell: WSL Ubuntu 22.04 (Ubuntu-22.04 on Windows 11 Pro 10.0.26200)
- Node 버전: `v20.20.0`
- Java 버전: `openjdk version "25.0.3" 2026-04-21` (시스템 기본)
  - 빌드 사용 JVM: `openjdk version "17"` (`/usr/lib/jvm/java-17-openjdk-amd64`) — 아래 Known Issues 참고

## Backend (Gradle)

명령: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew clean build -x test --warning-mode=all`

- 종료 코드: 0 (BUILD SUCCESSFUL)
- 소요 시간: 4s (두 번째 실행 캐시 포함, 최초 clean 실행 시 ~9s)
- 경고 개수: 0
- 결과 마지막 20줄:
  ```
  > Task :clean
  > Task :compileJava
  > Task :processResources
  > Task :classes
  > Task :resolveMainClassName
  > Task :bootJar
  > Task :jar
  > Task :assemble
  > Task :check
  > Task :build

  BUILD SUCCESSFUL in 4s
  6 actionable tasks: 6 executed
  ```

> 주의: `java -version` (시스템 기본) = Java 25를 사용하면 Gradle 8.14가 "Unsupported class file major version 69" 오류로 BUILD FAILED (exit 1). JAVA_HOME을 Java 17로 명시해야 빌드 성공함. Known Issues 참고.

## Frontend (Next.js)

### npm ci

- 종료 코드: 0 (성공)
- 소요 시간: real 0m32.609s
- 출력 마지막 20줄:
  ```
  npm warn deprecated inflight@1.0.6: This module is not supported, and leaks memory. Do not use it. Check out lru-cache if you want a good and tested way to coalesce async requests by a key value, which is much more comprehensive and powerful.
  npm warn deprecated @humanwhocodes/config-array@0.13.0: Use @eslint/config-array instead
  npm warn deprecated rimraf@3.0.2: Rimraf versions prior to v4 are no longer supported
  npm warn deprecated @humanwhocodes/object-schema@2.0.3: Use @eslint/object-schema instead
  npm warn deprecated glob@7.2.3: Old versions of glob are not supported, and contain widely publicized security vulnerabilities, which have been fixed in the current version. Please update.
  npm warn deprecated glob@10.3.10: Old versions of glob are not supported, and contain widely publicized security vulnerabilities, which have been fixed in the current version. Please update.
  npm warn deprecated eslint@8.57.1: This version is no longer supported. Please see https://eslint.org/version-support for other options.

  9 vulnerabilities (3 moderate, 6 high)

  To address issues that do not require attention, run:
    npm audit fix

  npm notice New major version of npm available! 10.8.2 -> 11.16.0
  ```
- 보안 취약점: 9개 (moderate 3, high 6)
- deprecation 경고: 7개

### npm run type-check

스크립트: `tsc --noEmit` (package.json 확인)

- 종료 코드: 0 (성공)
- 소요 시간: real 0m6.007s
- 에러 개수: 0
- 경고 개수: 0
- 출력: (빈 출력 — TypeScript 오류 없음)

### npm run build

스크립트: `next build` (package.json 확인)

- 종료 코드: 0 (성공)
- 소요 시간: real 0m16.078s
- Next.js 버전: 16.2.1 (Turbopack 사용)
- 번들 크기 요약(Next.js 출력):
  ```
  ⚠ Warning: Next.js inferred your workspace root, but it may not be correct.
   We detected multiple lockfiles and selected the directory of
   /home/user/project/team-blackbox/package-lock.json as the root directory.
   Detected additional lockfiles:
     * /home/user/project/team-blackbox/blackbox/frontend/package-lock.json
     * /home/user/project/team-blackbox/blackbox/package-lock.json

  ▲ Next.js 16.2.1 (Turbopack)

    Creating an optimized production build ...
  ✓ Compiled successfully in 6.7s
    Running TypeScript ...
    Finished TypeScript in 6.5s ...
    Collecting page data using 7 workers ...
  ✓ Generating static pages using 7 workers (12/12) in 393ms
    Finalizing page optimization ...

  Route (app)
  ┌ ○ /
  ├ ○ /_not-found
  ├ ○ /analytics
  ├ ○ /board
  ├ ○ /dashboard
  ├ ○ /login
  ├ ○ /meetings
  ├ ○ /profile/notion
  ├ ○ /profile/settings
  ├ ƒ /projects/[projectId]
  ├ ƒ /projects/[projectId]/analytics
  ├ ƒ /projects/[projectId]/board
  ├ ƒ /projects/[projectId]/meetings
  ├ ƒ /projects/[projectId]/meetings/[meetingId]
  ├ ƒ /projects/[projectId]/schedule
  ├ ƒ /projects/[projectId]/settings
  ├ ƒ /projects/[projectId]/vault
  ├ ○ /signup
  └ ○ /vault

  ○  (Static)   prerendered as static content
  ƒ  (Dynamic)  server-rendered on demand
  ```
- 참고: Next.js 16 Turbopack 출력에는 Route별 byte 크기 컬럼이 포함되지 않음 (webpack 출력 포맷과 상이).

## 테스트 파일 개수

- 백엔드 (`backend/src/test/**/*.java`): 1개
- 프론트엔드 (`*.test.*`, `*.spec.*`, node_modules 제외): 0개

## Known Issues (이번 빌드에서 발견됨)

> 발견된 에러/경고를 기록만. 본 Task에서 수정 금지.

- [ ] **[BE-001] Java 25 + Gradle 8.14 호환성 문제**: 시스템 기본 `java` = OpenJDK 25.0.3. Gradle 8.14의 embedded Groovy가 class file major version 69 (Java 25)를 지원하지 않아 `JAVA_HOME` 미설정 시 BUILD FAILED ("Unsupported class file major version 69"). `backend/gradle/wrapper/gradle-wrapper.properties`에 `org.gradle.java.home` 설정 또는 CI에서 JAVA_HOME 명시 필요.
- [ ] **[FE-001] 복수 lockfile 경고**: repo 루트에 `package-lock.json`이 3곳에 존재 (`/home/user/project/team-blackbox/package-lock.json`, `blackbox/frontend/package-lock.json`, `blackbox/package-lock.json`). Next.js Turbopack이 workspace root 추론에 실패하여 경고 출력. `turbopack.root` 설정 또는 불필요한 lockfile 제거 필요.
- [ ] **[FE-002] npm 보안 취약점 9개**: `npm ci` 실행 시 보고됨 — moderate 3건, high 6건. `npm audit` 결과 상세 확인 필요.
- [ ] **[FE-003] deprecated 패키지 경고 7개**: `inflight@1.0.6`, `@humanwhocodes/config-array@0.13.0`, `rimraf@3.0.2`, `@humanwhocodes/object-schema@2.0.3`, `glob@7.2.3`, `glob@10.3.10`, `eslint@8.57.1`. 주로 ESLint 관련 간접 의존성.
- [ ] **[FE-004] ESLint 버전 미지원**: `eslint@8.57.1` deprecated. ESLint 9.x 이상으로 업그레이드 검토 필요.
- [ ] **[BE-002] 테스트 커버리지 미비**: 백엔드 테스트 파일 1개, 프론트엔드 테스트 파일 0개. 리팩토링 안전망 부재.

각 issue는 Phase 1 Drift Inventory 또는 Phase 4 버그 Task로 후속 처리.
