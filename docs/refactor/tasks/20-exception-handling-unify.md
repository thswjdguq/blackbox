# Task 20 — 예외 처리 통일 (GlobalExceptionHandler generic fallback)

## 1. 한 줄 요약
`GlobalExceptionHandler`에 미처리 예외용 generic fallback 2개(`Exception`→500, `AccessDeniedException`→403)를 추가해, 모든 에러 응답을 일관된 `ProblemDetail`로 통일한다(상태코드 보존).

## 2. 변경 범위 (HARD LIMIT)

> 실행자는 이 섹션 밖의 파일·메서드를 절대 건드리지 않는다.

- **파일 (이외 금지):**
  - `backend/src/main/java/com/blackbox/exception/GlobalExceptionHandler.java`
- **메서드 (이외 금지) — 신규 추가만:**
  - `GlobalExceptionHandler`에 `@ExceptionHandler(AccessDeniedException.class)` 핸들러 1개 신규
  - `GlobalExceptionHandler`에 `@ExceptionHandler(Exception.class)` 핸들러 1개 신규
  - (필요한 import 2개 + Logger 필드 1개 추가)
- **기존 7개 핸들러 메서드:** 본문 0줄 변경 (그대로 둠)
- **예상 변경 라인 수:** ~25줄 추가 (삭제 없음)
- **예상 변경 파일 수:** 1개

## 3. 절대 건드리지 말 것 (Out of Scope)

- 기존 7개 `@ExceptionHandler`(DuplicateEmail/InvalidCredentials/NotFound/Forbidden/IO/MaxUploadSize/Validation) — **본문·상태코드·메시지 0줄 변경.**
- 커스텀 예외 클래스(`ForbiddenException` 등 5개) — 변경 금지.
- `SecurityConfig.java` — **변경 금지.** entrypoint/accessDeniedHandler 손대지 말 것(별도 Task 영역).
- 컨트롤러·서비스의 throw 지점 — 변경 금지(예외를 새로 던지거나 잡지 말 것).
- **상태코드 매핑 확장 금지:** `IllegalArgumentException→400` 같은 세분 매핑은 이번 Task 범위 밖(사용자 결정: 최소·상태보존). 추가하지 말 것.
- consent(INV-07)·로깅 외 부수 로직 추가 금지.

## 4. 컨텍스트 / 의도

`GlobalExceptionHandler`에 **generic fallback이 없다.** 5개 커스텀 예외 + IO/MaxUpload/Validation만 처리하고, 서비스단이 던지는 `RuntimeException`/`IllegalStateException`(예: `NotionService`·`ClaudeService`·`OpenAiService`·`GoogleCalendarService`의 "API 응답 없음" 등)은 핸들러를 만나지 못해 Spring 기본 `/error` 경로로 빠진다 → **`ProblemDetail`이 아닌 제각각 에러 바디 + 기본 500**.

이 Task는 **상태코드를 보존**하면서 에러 바디만 `ProblemDetail`로 통일한다(순수 refactor):
- 미처리 예외(현재 기본 500) → `Exception` 핸들러로 **500 `ProblemDetail`**. 상태 500→500 보존.
- `AccessDeniedException`(현재 Spring 기본 403) → `AccessDeniedException` 핸들러로 **403 `ProblemDetail`**. 상태 403→403 보존.

### ⚠ 두 핸들러는 반드시 함께 추가 (상태 보존 핵심)
`AccessDeniedException`은 `RuntimeException`(⊂`Exception`)의 하위 타입이다.
`Exception` 핸들러만 추가하면 `AccessDeniedException`이 거기로 잡혀 **403→500으로 강등**된다(동작 변경).
→ `AccessDeniedException`→403 핸들러를 **함께** 둬서 더 구체적인 핸들러가 먼저 매칭되게 한다(Spring은 가장 구체적인 `@ExceptionHandler` 선택). 이로써 403 보존.

> 참고(프레이밍): 본 repo `SecurityConfig`는 `authenticationEntryPoint=401`이고 `accessDeniedHandler` 미설정이다. PLAN의 "미처리 예외가 403으로 변환" 표현을 문자 그대로 쫓지 않고, **검증된 사실**(generic fallback 부재 → 비일관 에러 바디)만 다룬다. 권한 거부는 이미 커스텀 `ForbiddenException`→403으로 처리되며, 본 Task의 `AccessDeniedException` 핸들러는 Spring Security 자체 예외가 advice까지 도달하는 경우의 바디 통일 + 상태 보존용이다.

- 관련 INV: 해당 없음
- 관련 Drift ID: 해당 없음 (PLAN Phase 2A cross-cutting 후보. Drift 인벤토리 직접 매핑 아님)
- 관련 PRINCIPLES 섹션: §4(동작 보존), §6(실행자 협업)

## 4-1. 의존 관계

- **선행 Task (먼저 머지):** 없음.
- **후행 Task:** `21-webclient-error-pattern`(WebClient 호출부 에러 패턴 정리)이 본 Task가 세운 "에러는 ProblemDetail로 통일" 베이스라인 위에서 진행됨.
- **API surface 변경 여부:** No (엔드포인트·DTO 시그니처 불변). 에러 응답 바디 포맷만 통일, 상태코드 보존 → 문서 갱신 면제.

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §4 (동작 보존 원칙):

리팩토링 커밋은 외부에서 본 동작이 동일해야 한다. 본 Task는 에러 응답의 **HTTP 상태코드를 보존**하고 바디 포맷만 `ProblemDetail`로 통일한다. 상태코드를 바꾸는 변경(예: 400/404 재매핑)은 이 Task 범위 밖이다.

> PRINCIPLES.md §6 (실행자 협업 규칙):

- 추측 금지. 변경 범위 작업지시서 외 확장 금지.
- 기존 핸들러는 손대지 않는다. 신규 핸들러만 추가한다.
- 임포트 자동 정리 결과는 수동 확인.

## 6. 작업 절차

1. **import 추가** (`GlobalExceptionHandler.java` 상단):
   - `import org.springframework.security.access.AccessDeniedException;`
   - `import org.slf4j.Logger;`
   - `import org.slf4j.LoggerFactory;`

2. **Logger 필드 추가** (클래스 본문 맨 위):
   ```java
   private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
   ```

3. **`AccessDeniedException` 핸들러 추가** (기존 핸들러들과 같은 스타일, 클래스 내부):
   ```java
   @ExceptionHandler(AccessDeniedException.class)
   public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
       ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
       pd.setTitle("Forbidden");
       pd.setDetail("접근 권한이 없습니다");
       return pd;
   }
   ```

4. **generic `Exception` fallback 핸들러 추가** (가장 마지막, least-specific):
   ```java
   @ExceptionHandler(Exception.class)
   public ProblemDetail handleUnhandled(Exception ex) {
       log.error("Unhandled exception", ex);
       ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
       pd.setTitle("Internal Server Error");
       pd.setDetail("서버 내부 오류가 발생했습니다");
       return pd;
   }
   ```
   - **detail 메시지에 `ex.getMessage()`를 그대로 노출하지 말 것**(내부 정보 누출 방지). 고정 메시지 사용. 원문은 `log.error`로만.

5. **컴파일 확인:** `cd backend && ./gradlew compileJava` 통과.

6. **회귀 눈 검증:** 기존 7개 핸들러가 그대로인지(상태코드·title·detail 문자열 무변경) diff로 확인.

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — 신규 핸들러 2개가 §6에 코드 단위로 박혀 있고 1 파일·~25줄. 기존 핸들러 무변경. 상태 보존 근거(§4) 명시.
- [ ] **Required**

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `@ExceptionHandler(Exception.class)`(500 ProblemDetail) + `@ExceptionHandler(AccessDeniedException.class)`(403 ProblemDetail) 2개 신규 추가.
- [ ] 기존 7개 핸들러 본문·상태코드·문자열 **0줄 변경**.
- [ ] generic 핸들러 detail에 `ex.getMessage()` 미노출(고정 메시지). 예외 원문은 `log.error`로만.
- [ ] `AccessDeniedException` 핸들러가 있어 권한 거부가 403으로 보존됨(500 강등 아님).
- [ ] `SecurityConfig`·컨트롤러·서비스·커스텀 예외 클래스 diff 0.
- [ ] `./gradlew compileJava` 통과. HARD LIMIT 외 변경 없음. CI(refactor-guard) 통과.

## 9. PR 정보

- **Branch:** `refactor/20-exception-handling-unify`
- **PR base:** `refactor/main` (절대 `main` 아님)
- **PR title:** `[refactor-20] 예외 처리 통일 — GlobalExceptionHandler generic fallback (상태 보존)`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 20](../docs/refactor/tasks/20-exception-handling-unify.md)

  ## 변경 요약
  - GlobalExceptionHandler에 generic fallback 2개 추가: Exception→500, AccessDeniedException→403 (모두 ProblemDetail)
  - 미처리 예외(서비스단 RuntimeException 등)가 일관된 ProblemDetail로 응답
  - 상태코드 보존(500→500, 403→403). 바디 포맷만 통일 = 순수 refactor
  - 기존 7개 핸들러 무변경

  ## HARD LIMIT 준수
  - 파일: GlobalExceptionHandler.java (1개)
  - 라인: ~25 추가 (기존 핸들러 0줄 변경)

  ## 검수 체크리스트 결과
  - [x] 상태코드 보존 (AccessDeniedException→403 핸들러로 500 강등 차단)
  - [x] generic 핸들러 detail에 내부 예외 메시지 미노출 (log.error만)
  ```
