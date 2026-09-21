# ⛔ DEPRECATED — GitHub Copilot은 본 리팩토링 실행자가 아님 (2026-06-01)

> **GitHub Copilot은 정책 변경으로 본 프로젝트 리팩토링에 더 이상 사용하지 않는다 (DEC-WORKFLOW-010).**
> 실행자는 **Claude Sonnet 4.6 서브에이전트 → `.claude/agents/refactor-executor.md`** 이다.
> 이 파일은 (1) Copilot을 일시적으로 쓰게 될 경우의 최소 가드 + (2) 실행자 무관 프로젝트 공통 규칙의
> 빠른 참조로만 남긴다. **상시 적용 규칙의 정본은 아래 "정본 위치"를 따른다.**

## 정본 위치 (충돌 시 이쪽 우선)

| 무엇 | 정본 |
|---|---|
| 실행자 동작 규칙 (HARD LIMIT·코드 패턴·검증) | `.claude/agents/refactor-executor.md` |
| 불변 규칙 INV-01~07 | `md/gc.md` |
| 리팩토링 헌법 | `docs/refactor/PRINCIPLES.md` |
| 작업지시서 양식·개별 Task | `docs/refactor/TASK_TEMPLATE.md`, `docs/refactor/tasks/NN-xxx.md` |
| 누적 의사결정 | `docs/refactor/DECISIONS.md` |
| CI 가드 | `.github/workflows/refactor-guard.yml` |

---

## 절대 위반 불가 (실행자 무관 — gc.md INV 참조)

이 5개는 어떤 실행자(Claude 서브에이전트·Copilot·사람)든 공통으로 지킨다.

1. **[CRITICAL] DB 스키마 변경**: 기존 `backend/src/main/resources/db/migration/V*.sql`는 절대 수정/삭제 금지. 새 변경은 `V18+`로 추가만 가능. (gc.md INV-02 인접)
2. **[CRITICAL] file_vault 테이블**: UPDATE/DELETE 코드 금지. INSERT만 허용 (트리거가 막음). (INV-02)
3. **[CRITICAL] 외부 managed 서비스 금지**: Supabase, Vercel, Railway, Render, AWS RDS 등 호스트명/SDK 추가 금지. 모든 인프라는 Docker Compose 내부. (INV-05)
4. **[CRITICAL] 외부 API 쓰기 권한 요청 금지**: GitHub `contents:read`만, Google Drive `readonly` scope만. (INV-03)
5. **[CRITICAL] 하드코딩 시크릿 금지**: API key, JWT secret, DB password 등은 항상 환경변수.

## 브랜치/PR 규칙 (리팩토링 기간 한정 — 실행자 무관)

- 모든 작업은 `refactor/main`에서 분기한 `refactor/<task-id>-<name>` 브랜치에서 진행.
- PR은 항상 **base: `refactor/main`**. `main`에 직접 PR/push/merge 금지 (DEC-WORKFLOW-002).
- 한 커밋에는 한 종류 변경만: `rename:` / `move:` / `refactor:` / `fix:` / `feat:` / `docs:` / `chore:`. 섞지 말 것.
- PR 제목에 task ID 포함: `[refactor-NN] 짧은 설명`
- `--no-verify`, `--no-gpg-sign` 등 훅 우회 금지.

## 코드 컨벤션 (프로젝트 공통 — 실행자 무관)

### 백엔드 (Spring Boot 3.3.5 / Java 17)
- 레이어: Controller(얇음) → Service(`@Transactional`, `activityLogService.log()` 호출) → Repository.
- DTO는 `dto/` 패키지 Java `record`. Entity는 Controller에서 직접 반환 금지.
- 권한 검증은 `ProjectAccessChecker` 사용.
- 외부 HTTP는 `WebClient` + 에러 핸들러(`onErrorReturn` 또는 try-catch). 미처리 예외가 Security 필터를 거쳐 403으로 변환되는 버그 주의.
- 예외 처리는 `GlobalExceptionHandler`로 통일.
- AI 호출은 Claude → OpenAI 폴백 패턴 유지.

### 프론트 (Next.js 16 App Router / TypeScript)
- 모든 API 호출은 `src/lib/api.ts`의 axios 인스턴스 경유.
- 상태는 Zustand, store는 `src/lib/store/`.
- 디자인 토큰은 `md/frontend-design.md`의 `bb-*` CSS 변수 + Tailwind 사용.
- **`: any` 사용 금지**. 모르면 코드에 `// [CONFIRM:?] ...` 주석.
- 토큰 저장은 HttpOnly 쿠키 기반 (Task 71 이후).

## 실행 규율 (요약 — 정본은 refactor-executor.md)

- 작업지시서(`docs/refactor/tasks/NN-xxx.md`)의 **§2 변경 범위(HARD LIMIT)에 명시된 파일·메서드만** 수정. 그 외 절대 금지.
- 같은 파일 안에서도 작업지시서가 명시한 메서드/함수만 수정. 다른 메서드는 import 정리·네이밍·주석 정리도 금지.
- **§3 Out of Scope**는 절대 건드리지 말 것.
- 작업지시서가 없는 변경은 만들지 말 것. 추측 금지 — 모르면 `// [CONFIRM:?] ...` 주석.
- 복잡 Task(2파일↑ 또는 30줄↑)는 §7 Pre-write 프로토콜: 변경 계획 3~5불릿 먼저 제시 후 승인받고 구현.
- `docs/_archive/` 문서는 인용·참조 금지 (deprecated 보존본).

## 코드 패턴 (반드시 이 형태로 — 정본은 refactor-executor.md / PRINCIPLES §8·§9)

1. **Service 메서드**: 사용자 행동 유발 시 `@Transactional` + `activityLogService.log(...)` (INV-01). 읽기 전용은 `@Transactional(readOnly = true)`. Entity는 Controller로 새지 않게.
2. **WebClient 외부 호출**: `.onStatus(...)` + `.onErrorReturn(...)`(또는 호출부 try-catch) 필수. AI 호출은 `IllegalStateException` 전에 `isConfigured()` 체크.
3. **Controller 응답**: `ResponseEntity<XxxResponse>` 또는 `XxxResponse`. **`ResponseEntity<XxxEntity>` 절대 금지.** 권한은 `ProjectAccessChecker`. 입력은 `@Valid`.
4. **Frontend API 호출**: 항상 `lib/api.ts` 경유. 응답 타입은 `src/types/`. `: any` 금지.

## DROP COLUMN은 2단계

1. PR 1: 코드에서 해당 컬럼 사용 모두 제거.
2. PR 2: 다음 Flyway 마이그레이션에서 `DROP COLUMN`.

## 참고 문서

- `.claude/agents/refactor-executor.md` — **실행자(Claude Sonnet) 정본 가드**
- `docs/refactor/PRINCIPLES.md` — 전체 리팩토링 원칙 (정본)
- `docs/refactor/PLAN.md` — Phase 로드맵
- `docs/refactor/DECISIONS.md` — 누적 의사결정 (DEC-WORKFLOW-010: 실행자 교체)
- `md/gc.md` — 불변 규칙 INV-01~07, 일관성 SYNC-01~05, 금지 패턴 CODE-01~03
- `md/claude.md` — 프로젝트 컨텍스트
