# Task 27 — AiService 폴백 오케스트레이터 도입 (MeetingController 정리)

## 1. 한 줄 요약
`MeetingController`에 인라인으로 복붙된 "Claude→OpenAI" 폴백 분기를 `AiService` 한 곳으로 옮겨, 컨트롤러가 AI provider 선택 로직을 모르게 만든다.

## 2. 변경 범위 (HARD LIMIT)

> Copilot은 이 섹션 밖의 파일·메서드를 절대 건드리지 않는다.

- **파일 (이외 금지):**
  - `backend/src/main/java/com/blackbox/service/AiService.java` (신규)
  - `backend/src/main/java/com/blackbox/service/ClaudeService.java` (`@Order` 추가만)
  - `backend/src/main/java/com/blackbox/service/OpenAiService.java` (`@Order` 추가만)
  - `backend/src/main/java/com/blackbox/controller/MeetingController.java`
- **메서드/함수/클래스 (이외 금지):**
  - 신규 `AiService` (전체)
  - `ClaudeService` / `OpenAiService`: **클래스 어노테이션에 `@Order`만 추가** (본문 로직 변경 금지)
  - `MeetingController`: 생성자, 필드 선언, `aiSummarize()`, `aiExtractStructured()`, 그리고 이 두 메서드를 호출하는 `summarizeMeeting()`·`extractActions()` 의 호출부
- **예상 변경 라인 수:** ~70줄 (신규 AiService ~45 + 컨트롤러 순삭감)
- **예상 변경 파일 수:** 4개

> **§13 파일 수 예외 사유:** Claude/OpenAiService 변경은 **`@Order` 한 줄 추가뿐**(폴백 순서 보장용).
> 실질 로직 변경은 AiService(신규) + MeetingController 2파일.

## 3. 절대 건드리지 말 것 (Out of Scope)

- `ClaudeService`/`OpenAiService`의 **본문 로직·메서드 변경 금지** — `@Order` 클래스 어노테이션 1줄 외 손대지 말 것.
- `GoogleCalendarService` — Task 28에서 별도 처리. 이 Task에서 건드리지 말 것.
- `MeetingController`의 AI 외 다른 엔드포인트(생성/조회/체크인/Notion 등) 변경 금지.
- AI 호출 시 consent(INV-07) 체크 **신규 추가 금지** — 현재 없는 동작을 리팩토링에서 끼워넣지 않는다(동작 보존).
- 예외 메시지 문구 변경 금지 — 키 부재 시 던지는 `IllegalStateException` 메시지 보존.

## 4. 컨텍스트 / 의도

현재 `MeetingController`는 `ClaudeService`·`OpenAiService`를 **둘 다 주입**받아 다음 분기를 2개 메서드(`aiSummarize`, `aiExtractStructured`)에 복붙한다:

```java
if (claudeService.isConfigured())      return claudeService.xxx(...);
else if (openAiService.isConfigured()) return openAiService.xxx(...);
throw new IllegalStateException("AI API 키가 설정되지 않았습니다 ...");
```

이 폴백은 `GoogleCalendarService`에도 또 복붙되어 있다(총 3곳). AI 메서드를 하나 추가할 때마다
양쪽 서비스 + 3곳 분기를 동시에 고쳐야 한다. `AiService` 하나가 `List<LlmClient>`를 받아
"설정된 첫 번째 클라이언트"를 고르게 하면 폴백이 한 곳으로 모인다.

- 관련 INV: 해당 없음 (INV-07 consent는 현행대로 미적용 — 추가하지 않음)
- 관련 Drift ID: 해당 없음 (fast-track)
- 관련 PRINCIPLES 섹션: §1(중복 제거), §4(동작 보존), §8(폴백 패턴 유지)

## 4-1. 의존 관계

- **선행 Task (이게 먼저 머지되어야 함):** **26-llm-client-abstraction** (`LlmClient` 타입이 있어야 `List<LlmClient>` 주입 가능)
- **후행 Task (이 Task 머지 후 진행):** 28-calendar-use-ai-service
- **API surface 변경 여부:** No
  - `/meetings/{id}/ai/summarize`·`/ai/extract-actions` 의 요청·응답 형태 불변 → 문서 갱신 면제.

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §4 (동작 보존 원칙):

리팩토링 커밋은 외부에서 본 동작이 동일해야 한다.
같은 PR 안에서 `refactor:` 커밋과 `fix:` 커밋이 섞이는 것은 OK. 한 커밋에 두 종류가 섞이는 것은 금지.

> PRINCIPLES.md §8 (AI 서비스 / 외부 HTTP):

AI 서비스: Claude → OpenAI 폴백 패턴 유지.
AI 호출 등 키 부재가 가능한 경우 `IllegalStateException` 던지기 전에 `isConfigured()` 체크.

> PRINCIPLES.md §6 (Copilot 협업 규칙):

- 추측 금지 — 모르는 동작은 `// [CONFIRM:?]` 주석.
- Lombok 자동 생성 의존 시 동작 확인.
- 변경 범위 작업지시서 외 확장 금지.

## 6. 작업 절차

1. **폴백 순서 보장:** `ClaudeService` 클래스에 `@org.springframework.core.annotation.Order(1)`, `OpenAiService`에 `@Order(2)` 추가.
   - 이유: Spring이 `List<LlmClient>`를 주입할 때 순서는 `@Order` 기준. 이게 없으면 "Claude 우선" 보장 불가.
2. **신규 `AiService` 작성** (`@Service`):
   - 생성자에서 `List<LlmClient> clients` 주입 (Spring이 `@Order` 순으로 채움).
   - private 헬퍼 `private LlmClient active()`:
     - `clients` 중 `isConfigured()` 가 true인 **첫 번째** 반환.
     - 없으면 `throw new IllegalStateException("AI API 키가 설정되지 않았습니다 (CLAUDE_API_KEY 또는 OPENAI_API_KEY 필요)");`
       (기존 MeetingController 문구를 **그대로** 사용.)
   - 위임 메서드:
     - `String summarizeMeeting(String title, String purpose, String notes, String decisions)` → `active().summarizeMeeting(...)`
     - `List<ActionItemDto> extractStructuredActionItems(String notes, String decisions)` → `active().extractStructuredActionItems(...)`
     - `String rawCall(String prompt, int maxTokens)` → `active().rawCall(prompt, maxTokens)` (Task 28이 사용)
3. **`MeetingController` 수정:**
   - 필드/생성자에서 `ClaudeService claudeService`, `OpenAiService openAiService` 제거하고 `AiService aiService` 주입으로 교체.
   - private `aiSummarize(...)` / `aiExtractStructured(...)` **삭제**.
   - `summarizeMeeting()` 엔드포인트의 `aiSummarize(...)` 호출 → `aiService.summarizeMeeting(...)`.
   - `extractActions()` 엔드포인트의 `aiExtractStructured(...)` 호출 → `aiService.extractStructuredActionItems(...)`.
   - 사용 안 하게 된 `ClaudeService`/`OpenAiService` import 제거 (수동 확인).
4. **컴파일 확인:** `cd backend && ./gradlew compileJava` 통과.

## 7. Pre-write 프로토콜 적용 여부

- [ ] **Skip**
- [x] **Required** — 신규 빈 + DI 순서 의존성. Copilot은 구현 전 변경 계획 3~5불릿 제시 후 승인받고 진행. 특히 `@Order` 기반 폴백 순서가 보존되는지 계획에 명시.

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `AiService`가 신규 생성되고 `List<LlmClient>` 기반으로 "설정된 첫 클라이언트"를 선택.
- [ ] `ClaudeService(@Order(1))` → `OpenAiService(@Order(2))` 순서가 명시되어 Claude 우선 폴백이 보존됨.
- [ ] 두 클라이언트 모두 미설정 시 기존과 **동일한 메시지**의 `IllegalStateException` 발생.
- [ ] `MeetingController`가 더 이상 `ClaudeService`/`OpenAiService`를 직접 주입받지 않음 (오직 `AiService`).
- [ ] `MeetingController`의 인라인 폴백 메서드 2개(`aiSummarize`/`aiExtractStructured`)가 사라짐.
- [ ] `/ai/summarize`·`/ai/extract-actions` 응답 형태·동작 불변 (수동 또는 빌드 확인).
- [ ] `ClaudeService`/`OpenAiService` 본문 로직 변경 0줄 (`@Order` 외).
- [ ] `./gradlew compileJava` 통과. HARD LIMIT 외 변경 없음. CI 통과.

## 9. PR 정보

- **Branch:** `refactor/27-ai-service-orchestrator`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-27] AiService 폴백 오케스트레이터 도입 (MeetingController 정리)`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 27](../docs/refactor/tasks/27-ai-service-orchestrator.md)

  ## 변경 요약
  - AiService 신규: List<LlmClient> 기반 Claude→OpenAI 폴백 중앙화
  - @Order(1/2)로 폴백 우선순위 보존
  - MeetingController 인라인 폴백 2메서드 제거 → aiService 위임

  ## HARD LIMIT 준수
  - 파일: AiService(신규)/ClaudeService(@Order)/OpenAiService(@Order)/MeetingController
  - 라인: ~70 (예상 ±30% 내)

  ## 검수 체크리스트 결과
  - [x] Claude 우선 폴백 순서 보존(@Order)
  - [x] 키 부재 시 동일 예외 메시지
  - [x] 엔드포인트 동작 불변
  ```
