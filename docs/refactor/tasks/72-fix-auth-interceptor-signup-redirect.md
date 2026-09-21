# Task 72 — fix: 회원가입 페이지에서 인증 인터셉터가 /login으로 튕기는 버그

## 1. 한 줄 요약
`lib/api.ts` 응답 인터셉터가 401→refresh 실패 시 `/signup`을 예외에 넣지 않아 회원가입 페이지에서 사용자를 `/login`으로 강제 이동시키는 버그를 고친다(회원가입 자체가 막힘).

## 2. 변경 범위 (HARD LIMIT)

> 실행자는 이 섹션 밖의 파일·메서드를 절대 건드리지 않는다.

- **파일 (이외 금지):**
  - `frontend/src/lib/api.ts`
- **메서드/함수 (이외 금지):**
  - `api.interceptors.response.use(...)`의 **에러 핸들러 내 두 곳의 리다이렉트 가드만** 수정
- **예상 변경 라인 수:** ~6줄
- **예상 변경 파일 수:** 1개

## 3. 절대 건드리지 말 것 (Out of Scope)

- refresh 로직 자체(`api.post("/auth/refresh")`, `_retry`, 재시도 흐름) 변경 금지 — **리다이렉트 예외 경로만** 손댄다.
- `AuthProvider.tsx`, `authStore.ts`, 로그인/회원가입 페이지, 다른 어떤 파일도 수정 금지.
- `withCredentials`·쿠키 인증 방식(#71) 변경 금지.
- 백엔드 변경 금지.

## 4. 컨텍스트 / 의도 (버그 재현)

`/signup` 진입 시 흐름:
1. `AuthProvider`가 `GET /auth/profile` 호출 → **401**(로그아웃 상태, 정상).
2. `api.ts` 인터셉터가 401을 잡고 `POST /auth/refresh` 시도 → **401**(리프레시 토큰 없음, 정상).
3. 인터셉터가 `if (window.location.pathname !== "/login") window.location.href = "/login"` 실행 → 현재 경로가 `/signup`이라 **`/login`으로 강제 이동**.

→ 회원가입 폼에 머물 수 없어 가입 불가. `AuthProvider`는 이미 `/login`·`/signup`을 `isPublicPath`로 예외 처리하지만, **인터셉터(별도 레이어)에는 `/signup` 예외가 빠져** 있는 게 원인.

- 관련 INV: 해당 없음
- 발견 경위: Task 06 SMOKE 베이스라인 시도 중 S1(회원가입)에서 발견
- 관련 PRINCIPLES: §4(동작 보존 — 단 본 Task는 의도적 동작 수정 `fix:`)

## 4-1. 의존 관계

- **선행 Task:** 없음 (단독 fix)
- **후행:** Task 06 SMOKE 베이스라인 재실행(이 fix 머지 후 스택 재기동하면 S1~S7 진행 가능)
- **API surface 변경 여부:** No (프론트 라우팅 동작만 교정)

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §4 (동작 보존 원칙):

동작 변경이 의도적이라면 별도 커밋(`fix:`)으로 분리한다. (본 Task는 `fix:`)

> PRINCIPLES.md §6 (실행자 협업 규칙):

- 추측 금지. 변경 범위 작업지시서 외 확장 금지. 임포트 자동 정리 결과는 수동 확인.

## 6. 작업 절차

1. `frontend/src/lib/api.ts` 상단(또는 인터셉터 직전)에 예외 경로 상수 추가:
   ```ts
   const AUTH_REDIRECT_EXEMPT = ["/login", "/signup"];
   ```
2. 인터셉터 에러 핸들러의 **두 리다이렉트 가드**를 모두 교체:
   - 기존: `if (window.location.pathname !== "/login") { window.location.href = "/login"; }`
   - 변경: `if (!AUTH_REDIRECT_EXEMPT.includes(window.location.pathname)) { window.location.href = "/login"; }`
   - 첫 번째 가드(refresh 실패 catch 블록)와 두 번째 가드(`url === "/auth/refresh"` 분기) **둘 다** 적용.
3. **검증:** `cd frontend && npm run type-check` (그리고 가능하면 `npm run build`) 통과. WSL Ubuntu에서 실행.
   - 셸이 Git Bash라 실패하면 보고만 (계획자가 WSL에서 재검증).

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — 변경이 라인 단위로 명확(예외 목록에 `/signup` 추가).
- [ ] **Required**

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `frontend/src/lib/api.ts`만 변경 (다른 파일 0).
- [ ] 예외 경로 상수에 `/login`·`/signup` 포함, 두 리다이렉트 가드 모두 이를 사용.
- [ ] refresh 시도 로직(요청·`_retry`·재시도) 자체는 불변.
- [ ] 로그아웃 상태로 `/signup` 접근 시 더 이상 `/login`으로 강제 이동되지 않음(빌드/타입체크로 1차, 스모크 재실행으로 최종 — Task 06).
- [ ] 보호된 경로(예: `/dashboard`)에서 인증 실패 시에는 **기존대로 `/login`으로 이동**(동작 보존).
- [ ] `npm run type-check` 통과. HARD LIMIT 외 변경 없음. CI 통과.

## 9. PR 정보

- **Branch:** `refactor/72-fix-auth-interceptor-signup-redirect`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-72] fix: 회원가입 페이지 인증 인터셉터 /login 강제이동 버그`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 72](../docs/refactor/tasks/72-fix-auth-interceptor-signup-redirect.md)

  ## 변경 요약 (fix)
  - api.ts 인터셉터의 401→refresh 실패 리다이렉트가 /signup을 예외 처리하지 않아
    회원가입 페이지에서 /login으로 튕기던 버그 수정
  - AUTH_REDIRECT_EXEMPT = ["/login", "/signup"] 로 두 가드 통일

  ## 동작 변경 (refactor 외)
  - [x] 의도적 동작 변경 포함 (fix) — /signup 공개 경로 예외 추가

  ## HARD LIMIT 준수
  - 파일: frontend/src/lib/api.ts 1개. ~6줄

  ## 검수
  - [x] /dashboard 등 보호 경로의 /login 이동은 보존
  - [x] type-check 통과
  - 발견 경위: Task 06 SMOKE S1
  ```
