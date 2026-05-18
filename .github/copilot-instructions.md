# GitHub Copilot — Project Instructions

이 저장소는 Team Blackbox 프로젝트입니다. 모든 자동 생성 코드와 변경 제안은 아래 규칙을 따릅니다.

**권장 사용 모드:** Copilot Edits 또는 Workspace (VSCode). Surgical 다중 파일 편집 모드 우선. Chat free-text는 단순 질의·설명에만.

## 절대 위반 불가 (gc.md INV 참조)

1. **[CRITICAL] DB 스키마 변경**: 기존 `backend/src/main/resources/db/migration/V*.sql`는 절대 수정/삭제 금지. 새 변경은 `V18+`로 추가만 가능.
2. **[CRITICAL] file_vault 테이블**: UPDATE/DELETE 코드 금지. INSERT만 허용 (트리거가 막음).
3. **[CRITICAL] 외부 managed 서비스 금지**: Supabase, Vercel, Railway, Render, AWS RDS 등 호스트명/SDK 추가 금지. 모든 인프라는 Docker Compose 내부.
4. **[CRITICAL] 외부 API 쓰기 권한 요청 금지**: GitHub `contents:read`만, Google Drive `readonly` scope만.
5. **[CRITICAL] 하드코딩 시크릿 금지**: API key, JWT secret, DB password 등은 항상 환경변수.

## 브랜치/PR 규칙 (현재 리팩토링 기간 한정)

- 모든 작업은 `refactor/main`에서 분기한 `refactor/<task-id>-<name>` 브랜치에서 진행.
- PR은 항상 **base: `refactor/main`**. `main`에 직접 PR/push/merge 금지.
- 한 커밋에는 한 종류 변경만: `rename:` / `move:` / `refactor:` / `fix:` / `feat:` / `docs:` / `chore:`. 섞지 말 것.
- PR 제목에 task ID 포함: `[refactor-NN] 짧은 설명`
- `--no-verify`, `--no-gpg-sign` 등 훅 우회 금지.

## 코드 컨벤션

### 백엔드 (Spring Boot 3.3.5 / Java 17)
- 레이어: Controller(얇음) → Service(`@Transactional`, `activityLogService.log()` 호출) → Repository.
- DTO는 `dto/` 패키지 Java `record`. Entity는 Controller에서 직접 반환 금지.
- 권한 검증은 `ProjectAccessChecker` 사용.
- 외부 HTTP는 `WebClient` + 에러 핸들러(`onErrorReturn` 또는 try-catch). 미처리 예외가 Security 필터를 거쳐 403으로 변환되는 버그 주의.
- 예외 처리는 `GlobalExceptionHandler`로 통일.
- AI 호출은 Claude → OpenAI 폴백 패턴 유지.

### 프론트 (Next.js 16 App Router / TypeScript)
- 모든 API 호출은 `src/lib/api.ts`의 axios 인스턴스 경유 (토큰·401 인터셉터 자동).
- 상태는 Zustand, store는 `src/lib/store/`.
- 디자인 토큰은 `md/frontend-design.md`의 `bb-*` CSS 변수 + Tailwind 사용.
- **`: any` 사용 금지**. 모르면 코드에 `// [CONFIRM:?] ...` 주석으로 질문 남기고 PR에 표시.
- 토큰 저장은 현재 `localStorage` (변경 결정 전까지 유지).

## 작업 범위 제한 (HARD LIMIT)

- 작업지시서(`docs/refactor/tasks/NN-xxx.md`)의 **변경 범위 (HARD LIMIT)** 섹션에 명시된 파일·메서드만 수정. 그 외 절대 금지.
- 같은 파일 안에서도 작업지시서가 명시한 메서드/함수만 수정. 다른 메서드는 import 정리·네이밍·주석 정리도 금지.
- 작업지시서의 **Out of Scope** 섹션은 절대 건드리지 말 것 (별도 Task로 분리됨).
- 작업지시서가 없는 변경은 만들지 말 것.
- 추측 금지. 모르는 동작은 빈 함수로 두지 말고 `// [CONFIRM:?] ...` 주석으로 질문 남기기.
- 임포트 자동 정리 결과는 수동 확인.
- 테스트가 깨지면 코드를 다시 보라(테스트가 옳을 가능성).
- `docs/_archive/` 안의 모든 문서는 인용·참조 금지 (deprecated 보존본). 컨텍스트로 사용하지 말 것.

## 출력 형식 (Diff-first)

**파일 전체 재출력 절대 금지.** 변경된 부분만 다음 형식으로 출력:

```
파일: <relative/path>
위치: <function name 또는 line range>
변경 사유: <한 줄>

--- before
<변경 전 라인 + 컨텍스트 3줄>
--- after
<변경 후 라인 + 컨텍스트 3줄>
```

여러 파일이면 위 블록을 반복. **변경 라인이 30줄 미만이라면 파일 전체 출력은 명백한 위반.** Copilot Edits 모드를 사용 중이라면 이 규칙은 Edits 자체가 강제하므로 자연스럽게 충족됨.

## Pre-write 프로토콜 (복잡한 Task)

**복잡도 임계값: 변경 파일 2개 이상 OR 예상 변경 30줄 이상.**

이 임계값을 넘는 Task는 즉시 코드 출력 금지. 다음 순서로 진행:

1. **변경 계획을 먼저 출력** — 3~5개 불릿:
   - "파일 X의 메서드 Y를 Z로 변경"
   - "DTO XxxResponse를 새로 만듦"
   - 등
2. 사용자 승인 대기
3. 승인 후에만 실제 코드 출력 (Diff-first 형식)

작업지시서의 HARD LIMIT 안에 이미 충분히 상세한 변경 명세가 있다면 1단계 생략 가능. 모호함이 1%라도 있으면 1단계 필수.

## DROP COLUMN은 2단계

1. PR 1: 코드에서 해당 컬럼 사용 모두 제거.
2. PR 2: 다음 Flyway 마이그레이션에서 `DROP COLUMN`.

## 코드 패턴 (반드시 이 형태로 작성)

서술 규칙보다 패턴 일치가 우선이다. 새 코드는 아래 4개 패턴을 따른다.

### 1. Service 메서드 (INV-01: activity_logs 기록 강제)

```java
@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final ActivityLogService activityLogService;

    @Transactional
    public TaskResponse create(UUID projectId, UUID userId, CreateTaskRequest req) {
        Task task = taskRepository.save(Task.of(projectId, userId, req));
        activityLogService.log(projectId, userId, "TASK_CREATE", Map.of("taskId", task.getId()));
        return TaskResponse.from(task);
    }
}
```
- 사용자 행동을 일으키는 Service 메서드는 **항상** `activityLogService.log()` 호출.
- 쓰기 메서드는 `@Transactional`. 읽기 전용은 `@Transactional(readOnly = true)`.
- DTO 변환은 Service 또는 DTO의 정적 팩토리에서. Controller로 Entity 새지 않게.

### 2. WebClient 외부 호출 (handover_log §9.1: 403 버그 재발 방지)

```java
public Mono<Map<String, Object>> callExternal(...) {
    return webClient.post()
        .uri(...)
        .retrieve()
        .onStatus(HttpStatusCode::isError, resp ->
            resp.bodyToMono(String.class).flatMap(body ->
                Mono.error(new ExternalApiException("외부 API 실패: " + body))))
        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
        .onErrorReturn(Map.of());  // 또는 적절한 fallback
}
```
- 미처리 예외가 Spring Security `ExceptionTranslationFilter`를 거쳐 403으로 변환되는 버그가 알려져 있음.
- 모든 `WebClient` 호출은 `.onStatus()` + `.onErrorReturn()`(또는 try-catch in caller) 필수.
- AI 호출(Claude/OpenAI) 등 키 부재가 가능한 경우 `IllegalStateException` 던지기 전에 `isConfigured()` 체크.

### 3. Controller 응답 (Entity 직접 노출 금지)

```java
@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;
    private final ProjectAccessChecker accessChecker;

    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateTaskRequest req) {
        accessChecker.assertMember(projectId, userId);
        return ResponseEntity.ok(taskService.create(projectId, userId, req));
    }
}
```
- 반환 타입은 `ResponseEntity<XxxResponse>` 또는 `XxxResponse`. **`ResponseEntity<XxxEntity>` 절대 금지.**
- 권한 검증은 `ProjectAccessChecker`의 `assertMember`/`assertLeader` 등 사용.
- 입력 검증은 `@Valid` + DTO에 어노테이션.

### 4. Frontend API 호출 (lib/api.ts 경유)

```typescript
import { api } from "@/lib/api";
import type { TaskResponse, CreateTaskRequest } from "@/types/task";

export async function createTask(
  projectId: string,
  req: CreateTaskRequest
): Promise<TaskResponse> {
  const { data } = await api.post<TaskResponse>(
    `/projects/${projectId}/tasks`,
    req
  );
  return data;
}
```
- `fetch`/`axios` 직접 사용 금지. 항상 `lib/api.ts`의 `api` 인스턴스 경유 (토큰·401 인터셉터 자동).
- 응답 타입은 `src/types/`에 정의된 타입 사용. 인라인 타입 정의 금지.
- `: any` 절대 금지. 모르면 `// [CONFIRM:?] response shape unknown` 주석 + Task 검수에서 질문.

## 참고 문서

- `docs/refactor/PRINCIPLES.md` — 전체 리팩토링 원칙 (정본)
- `docs/refactor/PLAN.md` — Phase 로드맵
- `md/gc.md` — 불변 규칙 INV-01~07, 일관성 SYNC-01~05, 금지 패턴 CODE-01~03
- `md/claude.md` — 프로젝트 컨텍스트
