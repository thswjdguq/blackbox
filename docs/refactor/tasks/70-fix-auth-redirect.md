# Task 70 — 로그인 페이지 auth 리다이렉트 수정

## 1. 한 줄 요약
로그인된 상태에서 뒤로가기로 로그인 페이지 진입 시 즉시 dashboard로 리다이렉트하고, 앱 루트에서 토큰 재수화 + 서버 검증을 수행해 브라우저 히스토리 이동에서도 인증 상태가 일관되게 한다.

## 2. 변경 범위 (HARD LIMIT)

- **파일 (이외 금지):**
  - `frontend/src/app/layout.tsx` (또는 루트 클라이언트 컴포넌트)
  - `frontend/src/app/(auth)/login/page.tsx` (또는 실제 로그인 페이지 경로)
  - `frontend/src/lib/store/authStore.ts`
  - `frontend/src/lib/api.ts` (프로필 호출 함수 소량 보완 필요 시만)
- **메서드/함수 (이외 금지):**
  - `authStore.ts`: `initFromStorage()` 개선 또는 `isAuthenticated()` 헬퍼 추가
  - `layout.tsx`: 루트 마운트 시 재수화 + 프로필 검증 로직 추가
  - `login/page.tsx`: 마운트 시 auth 체크 + `router.replace` 리다이렉트
- **예상 변경 라인 수:** ~40~70줄
- **예상 변경 파일 수:** 2~4개

## 3. 절대 건드리지 말 것 (Out of Scope)

- 백엔드 코드 변경 금지 (`/api/auth/profile` 엔드포인트는 이미 존재)
- `api.ts` 인터셉터 로직 변경 금지 (토큰 첨부·401 처리는 그대로 유지)
- 로그인 폼 UI·유효성 검사 로직 변경 금지
- 다른 페이지 컴포넌트 변경 금지
- `localStorage` 키 이름 변경 금지 (기존 토큰 저장 방식 유지)

## 4. 컨텍스트 / 의도

**현재 문제:**
1. 로그인 성공 후 `router.push('/dashboard')` 사용 → 히스토리에 `/login`이 남음
2. 로그인 페이지에 mount 시 auth 체크 없음 → 뒤로가기로 진입해도 로그인 페이지 그대로 표시
3. 앱 루트에서 `initFromStorage()` 미호출 → 새로고침·히스토리 이동 시 Zustand store가 비어있음

**수정 후 동작:**
- 로그인 성공 → `router.replace('/dashboard')` (히스토리에 `/login` 미기록)
- 로그인 페이지 mount → 토큰 있으면 즉시 `router.replace('/dashboard')`
- 앱 루트 mount → `initFromStorage()` 호출 + 토큰 있으면 `/api/auth/profile` 검증 → 실패 시 토큰 삭제

- 관련 INV: 해당 없음
- 관련 Drift ID: 해당 없음
- 관련 PRINCIPLES 섹션: §4 (동작 보존 원칙 — 본 Task는 의도적 동작 변경이므로 `fix:` 커밋)

## 4-1. 의존 관계

- **선행 Task:** 없음 (Phase 0 진행 중 선행 처리)
- **후행 Task:** 없음
- **API surface 변경 여부:** No (백엔드 변경 없음)

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

### 6-1. 실제 파일 경로 확인 (Pre-write)

변경 전 다음 경로가 실제로 존재하는지 확인:
```bash
find frontend/src -name "layout.tsx" | head -5
find frontend/src -name "page.tsx" -path "*/login/*"
find frontend/src/lib/store -name "authStore.ts"
find frontend/src/lib -name "api.ts"
```
실제 경로를 HARD LIMIT §2에 반영 후 구현.

### 6-2. authStore.ts 수정

`initFromStorage()` 함수에 다음 보완:
- localStorage에서 토큰 읽어 store에 복원 (기존 로직 유지)
- 토큰 존재 여부를 boolean으로 반환하도록 개선 (또는 `hasToken()` 헬퍼 추가)

```typescript
// 예시 패턴 (실제 코드에 맞게 적용)
initFromStorage: () => {
  const accessToken = localStorage.getItem('accessToken')
  const refreshToken = localStorage.getItem('refreshToken')
  if (accessToken && refreshToken) {
    set({ accessToken, refreshToken, isAuthenticated: true })
    return true
  }
  return false
}
```

### 6-3. layout.tsx 수정

루트 레이아웃 클라이언트 컴포넌트에 마운트 시 재수화 + 서버 검증 추가:

```typescript
// 예시 패턴
useEffect(() => {
  const hasToken = useAuthStore.getState().initFromStorage()
  if (hasToken) {
    // 서버 검증
    api.get('/auth/profile')
      .catch(() => {
        // 토큰 만료 또는 무효 → store 초기화
        useAuthStore.getState().logout()
      })
  }
}, [])
```

**주의:** layout.tsx가 서버 컴포넌트이면 별도 `AuthProvider` 클라이언트 컴포넌트로 분리. `'use client'` 없이 useEffect 사용 금지.

### 6-4. login/page.tsx 수정

마운트 시 auth 체크 추가:

```typescript
// 예시 패턴
useEffect(() => {
  const { isAuthenticated, initFromStorage } = useAuthStore.getState()
  initFromStorage()
  if (isAuthenticated) {
    router.replace('/dashboard') // push 아닌 replace
  }
}, [])
```

로그인 성공 핸들러에서 `router.push` → `router.replace` 변경.

### 6-5. 동작 검증

다음 3개 시나리오 수동 확인:
1. 로그인 → 뒤로가기 → 로그인 페이지 진입 즉시 `/dashboard` 리다이렉트
2. 토큰 없는 상태 → 로그인 페이지 정상 표시
3. 새 탭에서 `/login` 직접 접근 → 로그인 상태면 `/dashboard` 리다이렉트

## 7. Pre-write 프로토콜 적용 여부

- [x] **Required** — 구현 전 다음을 먼저 제시:
  1. 실제 파일 경로 (§6-1 find 결과 기반)
  2. `authStore.ts`의 현재 `initFromStorage()` 구현 요약
  3. `layout.tsx`가 서버/클라이언트 컴포넌트 중 어느 것인지
  사용자 확인 후 구현.

## 8. 검수 기준 (Acceptance Criteria)

- [ ] 로그인 후 뒤로가기 → 로그인 페이지 진입 시 즉시 `/dashboard` 리다이렉트
- [ ] 로그인 성공 시 `router.replace` 사용 (히스토리에 `/login` 미기록)
- [ ] 토큰 없는 상태에서 `/login` 접근 시 정상 로그인 페이지 표시
- [ ] 새로고침 후에도 로그인 상태 유지 (재수화 동작)
- [ ] 토큰 만료 시 자동 로그아웃 (프로필 검증 실패 → store 초기화)
- [ ] 백엔드 코드 변경 0줄
- [ ] `api.ts` 인터셉터 로직 변경 0줄
- [ ] HARD LIMIT 외 파일 변경 없음
- [ ] CI(refactor-guard) 통과
- [ ] SMOKE_TESTS S1 시나리오 수동 통과

## 9. PR 정보

- **Branch:** `refactor/70-fix-auth-redirect`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-70] 로그인 페이지 auth 리다이렉트 수정`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 70](../docs/refactor/tasks/70-fix-auth-redirect.md)

  ## 변경 요약
  - 로그인 페이지 mount 시 auth 체크 + router.replace 리다이렉트
  - 앱 루트에서 토큰 재수화 + 서버 검증
  - 로그인 성공 시 router.push → router.replace 변경

  ## Before / After
  - Before: 뒤로가기로 /login 진입 시 로그인 페이지 그대로 표시
  - After: 토큰 있으면 즉시 /dashboard 리다이렉트

  ## 동작 변경
  - [x] 의도적 동작 변경 포함 (`fix:` 커밋으로 분리됨)

  ## HARD LIMIT 준수
  - 변경 파일: 프론트 2~4개
  - 백엔드 변경 0줄

  ## 검수
  - [x] 시나리오 3개 수동 통과
  - [x] SMOKE_TESTS S1 통과
  - [x] CI 통과
  ```
