# Task 71 — JWT HttpOnly 쿠키 전환 및 로컬 토큰 제거

## 1. 한 줄 요약
브라우저 localStorage에 저장하던 JWT를 HttpOnly 쿠키로 전환하고, 로그인·리프레시·로그아웃 흐름을 서버 검증 기반으로 바꿔 XSS로 인한 토큰 탈취 위험을 줄인다.

## 2. 변경 범위 (HARD LIMIT)

> Copilot은 이 섹션 밖의 파일·메서드를 절대 건드리지 않는다.

- **파일 (이외 금지):**
  - `backend/src/main/java/com/blackbox/controller/AuthController.java`
  - `backend/src/main/java/com/blackbox/service/AuthService.java`
  - `backend/src/main/java/com/blackbox/security/JwtAuthenticationFilter.java`
  - `frontend/src/lib/api.ts`
  - `frontend/src/lib/store/authStore.ts`
  - `frontend/src/components/AuthProvider.tsx`
  - `frontend/src/app/login/page.tsx`
  - `frontend/src/components/Sidebar.tsx`
  - `frontend/src/types/auth.ts`
- **메서드/함수/클래스 (이외 금지):**
  - `AuthController.login()` / `signup()` / `refresh()` / `logout()`
  - `AuthService.login()` / `signup()` / `refresh()` / `logout()` / `issueTokenPair()`
  - `JwtAuthenticationFilter.doFilterInternal()`
  - `useAuthStore.initFromStorage()` / `clearTokens()` / auth state shape
  - `AuthProvider`의 bootstrap `useEffect`
  - `login/page.tsx`의 mount auth 체크와 submit 핸들러
  - `Sidebar`의 logout 핸들러
  - `api.ts`의 request/response interceptor
- **예상 변경 라인 수:** ~160~220줄
- **예상 변경 파일 수:** 8~9개

## 3. 절대 건드리지 말 것 (Out of Scope)

- 로그인 폼 UI/디자인 수정
- 비밀번호 검증 규칙 변경
- 다른 페이지 컴포넌트나 도메인 API 수정
- DB 스키마/마이그레이션 추가
- `layout.tsx`에 auth 로직을 다시 분산시키는 변경
- 토큰 저장을 다른 브라우저 저장소로 옮기는 우회안

## 4. 컨텍스트 / 의도

현재 인증 흐름은 JWT를 localStorage에 저장하고 `api.ts`가 이를 직접 읽는다. 이 구조는 XSS가 한 번만 생겨도 access/refresh token이 그대로 노출되므로, Task 70의 로그인 리다이렉트 수정과는 분리된 보안 하드닝이 필요하다.

이 Task는 **별도 브랜치/별도 PR**에서 처리한다. redirect UX 수정(Task 70)과 token-storage 보안 강화가 같은 diff에 섞이면 검토와 회귀 추적이 어려워진다.

- 관련 INV: 해당 없음
- 관련 Drift ID: 해당 없음
- 관련 PRINCIPLES 섹션: §4, §6, §9

## 4-1. 의존 관계

> 의존하는 Task가 `refactor/main`에 머지된 후에만 이 Task 시작.
> 의존성 무시하고 시작하면 베이스가 stale해서 충돌·재작업 발생.

- **선행 Task (이게 먼저 머지되어야 함):** 70-fix-auth-redirect
- **후행 Task (이 Task 머지 후 진행):** 없음
- **API surface 변경 여부:** Yes
  - 로그인/리프레시/로그아웃 응답과 쿠키 처리 방식이 바뀌므로 `md/handover_log.md` 갱신이 필요하다.
  - 프론트 auth 타입이 필요하면 `frontend/src/types/auth.ts`를 정본으로 추가한다.

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §4 (동작 보존 원칙):

동작 변경이 의도적이라면 별도 커밋(`fix:` 또는 `feat:`)으로 분리한다.
같은 PR 안에서 `refactor:` 커밋과 `fix:` 커밋이 섞이는 것은 OK.
한 커밋에 두 종류가 섞이는 것은 금지.

> PRINCIPLES.md §6 (Copilot 협업 규칙):

- 추측 금지 — 모르는 동작은 `// [CONFIRM:?]` 주석으로 질문
- 변경 범위 작업지시서 외 확장 금지
- 테스트가 깨지면 코드를 다시 보라

> PRINCIPLES.md §9 (프론트엔드 코드 컨벤션):

- API 호출: 항상 `lib/api.ts`의 axios 인스턴스 경유
- 상태: Zustand. store는 `lib/store/`
- 토큰 저장: 현재 `localStorage` 사용 (변경 금지)

## 6. 작업 절차

1. 현재 auth 경로를 확인하고, 로그인/리프레시/프로필/로그아웃이 어디서 토큰을 읽고 쓰는지 먼저 고정한다.
2. 백엔드에서 JWT를 HttpOnly 쿠키로 발급·갱신·폐기하도록 바꾸고, `JwtAuthenticationFilter`는 쿠키를 우선 읽도록 수정한다.
3. 프론트에서는 `authStore`의 토큰 영속화를 제거하고, `api.ts`/`AuthProvider`/`login`/`Sidebar`를 쿠키 기반 흐름으로 전환한 뒤 수동 시나리오로 검증한다.

## 7. Pre-write 프로토콜 적용 여부

- [ ] **Skip** — HARD LIMIT이 명확하고 변경 < 30줄 / 1 파일. 즉시 구현 가능.
- [x] **Required** — 복잡도 ↑. Copilot에게 "구현 전 변경 계획을 3~5개 불릿으로 먼저 제시" 요청. 사용자 승인 후 구현.

## 8. 검수 기준 (Acceptance Criteria)

- [ ] login/refresh/logout 흐름에서 accessToken / refreshToken이 localStorage에 저장되지 않는다.
- [ ] `api.ts`는 쿠키 기반 인증으로 protected request를 처리한다.
- [ ] 로그인 성공 후 `/login` 히스토리 잔존 없이 `/dashboard`로 이동한다.
- [ ] 로그아웃 시 서버 쿠키가 무효화되고 프론트 auth state가 초기화된다.
- [ ] 만료/무효 쿠키에서 `/auth/profile` 실패 시 로그인 화면으로 복귀한다.
- [ ] `md/handover_log.md` 또는 auth 타입 파일 갱신이 필요한 경우 반영된다.
- [ ] HARD LIMIT 외 파일 변경 없음.
- [ ] CI(refactor-guard) 통과.

## 9. PR 정보

- **Branch:** `refactor/71-auth-cookie-hardening`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-71] JWT HttpOnly 쿠키 전환 및 로컬 토큰 제거`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 71](../docs/refactor/tasks/71-auth-cookie-hardening.md)

  ## 변경 요약
  - localStorage 기반 JWT 저장 제거
  - HttpOnly 쿠키 기반 auth / refresh / logout 전환
  - auth bootstrap 및 logout 흐름을 서버 검증 기반으로 통일

  ## HARD LIMIT 준수
  - 파일: backend AuthController/AuthService/JwtAuthenticationFilter, frontend api/authStore/AuthProvider/login/Sidebar/types
  - 라인: ~160~220 (예상 N±30% 범위 내)

  ## 검수 체크리스트 결과
  - [x] 로그인/리프레시/로그아웃에서 토큰이 localStorage에 남지 않음
  - [x] `/login` 히스토리 잔존 없이 dashboard 리다이렉트
  - [x] 로그아웃 후 서버 쿠키 무효화
  ```