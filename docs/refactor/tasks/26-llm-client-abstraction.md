# Task 26 — LLM 클라이언트 추상화 (Claude/OpenAI 공통 로직 추출)

## 1. 한 줄 요약
`ClaudeService`와 `OpenAiService`에 byte 단위로 중복된 프롬프트 빌드·응답 파싱 로직을 공통 상위 타입(`LlmClient` + `AbstractLlmClient`)으로 추출하여, provider별 차이(URI·헤더·바디·응답파싱)만 각 서비스에 남긴다.

## 2. 변경 범위 (HARD LIMIT)

> Copilot은 이 섹션 밖의 파일·메서드를 절대 건드리지 않는다.

- **파일 (이외 금지):**
  - `backend/src/main/java/com/blackbox/service/LlmClient.java` (신규 — interface)
  - `backend/src/main/java/com/blackbox/service/AbstractLlmClient.java` (신규 — 추상 클래스)
  - `backend/src/main/java/com/blackbox/service/ClaudeService.java`
  - `backend/src/main/java/com/blackbox/service/OpenAiService.java`
- **메서드/함수/클래스 (이외 금지):**
  - `ClaudeService` 전체 (시그니처 유지, 내부 재배치)
  - `OpenAiService` 전체 (시그니처 유지, 내부 재배치)
  - 신규 `LlmClient`, `AbstractLlmClient`
- **예상 변경 라인 수:** ~250줄 (신규 추출 포함, 순삭감 ~200줄)
- **예상 변경 파일 수:** 4개

> **§13 "단일 Task ≤ 3 파일" 예외 사유:** 4파일 중 2개(`LlmClient`, `AbstractLlmClient`)는
> 기존 두 서비스에서 **동일 코드를 그대로 끌어올리는 순수 추출**이며, 나머지 2개는 그 코드를
> 삭제하고 상속으로 연결할 뿐이다. 한 덩어리로 다루지 않으면 추상화가 반쪽이 되어 컴파일 불가.

## 3. 절대 건드리지 말 것 (Out of Scope)

- `MeetingController`, `GoogleCalendarService` — 이 두 소비자는 **이 Task에서 절대 수정 금지** (각각 Task 27·28).
- 공개 메서드 **시그니처 변경 금지**: `isConfigured()`, `summarizeMeeting(...)`, `extractStructuredActionItems(...)`, `extractActionItems(...)`, `rawCall(...)` 의 이름·인자·반환타입 모두 현행 유지.
- `@Service` 어노테이션·빈 이름·클래스명(`ClaudeService`/`OpenAiService`) 유지 — DI 주입부가 깨지면 안 됨.
- 패키지 이동 금지 (`service/` 평면 유지. `service/ai/` 등으로 옮기지 말 것).
- 죽은 코드인 `extractActionItems()` 삭제 금지 (호출자 없음은 Phase 3 `64-dead-code-remove`에서 별도 처리).
- 프롬프트 **문구 자체 변경 금지** — 글자 하나라도 바꾸면 AI 출력이 달라져 동작 보존 위반.
- WebClient 설정(헤더·base-url·model 주입) 변경 금지.

## 4. 컨텍스트 / 의도

`ClaudeService`(217줄)와 `OpenAiService`(214줄)는 약 95%가 동일하다. 다음이 **byte 단위로 중복**:
`buildSummaryPrompt`, `buildExtractPrompt`, `parseStructured`, `extractJsonArray`, `parseLines`, `nvl`,
그리고 공개 메서드 시그니처(`summarizeMeeting`/`extractStructuredActionItems`/`extractActionItems`/`rawCall`/`isConfigured`).

provider 간 **실제 차이는 4가지뿐**:
1. 호출 URI (`/v1/messages` vs `/v1/chat/completions`)
2. 인증 헤더 (`x-api-key` + `anthropic-version` vs `Authorization: Bearer`)
3. 요청 바디의 system 위치 (top-level `system` 필드 vs `system` role 메시지)
4. 응답 파싱 경로 (`content[0].text` vs `choices[0].message.content`)

이 차이만 추상 메서드(template method)로 남기고 나머지를 상위로 끌어올린다.

- 관련 INV: 해당 없음
- 관련 Drift ID: 해당 없음 (Phase 1 인벤토리 전 fast-track — 사용자 결정)
- 관련 PRINCIPLES 섹션: §1(중복 제거), §4(동작 보존), §8(AI 폴백 패턴 유지)

## 4-1. 의존 관계

- **선행 Task (이게 먼저 머지되어야 함):** 없음
- **후행 Task (이 Task 머지 후 진행):** 27-ai-service-orchestrator, 28-calendar-use-ai-service
- **API surface 변경 여부:** No
  - 공개 메서드 시그니처 불변, REST 엔드포인트·DTO 불변 → **문서 갱신 면제** (PLAN §2B).

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §4 (동작 보존 원칙):

리팩토링 커밋은 외부에서 본 동작이 동일해야 한다.
동작 변경이 의도적이라면 별도 커밋(`fix:` 또는 `feat:`)으로 분리한다.
행동 보존이 의심되면 유예(§7)한다.

> PRINCIPLES.md §8 (백엔드 코드 컨벤션 — AI 서비스):

AI 서비스: Claude → OpenAI 폴백 패턴 유지(`md/handover_log.md` §9.1).
외부 HTTP: `WebClient`(Spring WebFlux). 에러 핸들러 필수.
AI 호출 등 키 부재가 가능한 경우 `IllegalStateException` 던지기 전에 `isConfigured()` 체크.

> PRINCIPLES.md §6 (Copilot 협업 규칙):

- 추측 금지 — 모르는 동작은 빈 함수로 두지 말고 `// [CONFIRM:?]` 주석으로 질문.
- 임포트 자동 정리 결과는 수동 확인.
- 변경 범위 작업지시서 외 확장 금지 — 옆 파일이 더러워도 건드리지 말 것.

## 6. 작업 절차

1. **신규 `LlmClient` interface 작성** — 다음 5개 공개 메서드만 선언:
   - `boolean isConfigured();`
   - `String summarizeMeeting(String title, String purpose, String notes, String decisions);`
   - `java.util.List<ActionItemDto> extractStructuredActionItems(String notes, String decisions);`
   - `java.util.List<String> extractActionItems(String notes, String decisions);`
   - `String rawCall(String userPrompt, int maxTokens);`
2. **신규 `AbstractLlmClient` 추상 클래스 작성** (`implements LlmClient`):
   - 공통 코드를 **그대로 이동**: `buildSummaryPrompt`, `buildExtractPrompt`, `parseStructured`, `extractJsonArray`, `parseLines`, `nvl`.
   - 공개 메서드 구현을 상위로 이동:
     - `summarizeMeeting(...)` → `return callModel(buildSummaryPrompt(...), 1500);`
     - `rawCall(p, n)` → `return callModel(p, n);`
     - `extractActionItems(...)` → `return parseLines(callModel(buildExtractPrompt(...), 800));`
     - `extractStructuredActionItems(...)` → systemPrompt/userPrompt 구성 후 `callModelWithSystem(...)` → `parseStructured(...)` (재시도도 `callModelWithSystem` 사용).
   - **두 개의 protected abstract 메서드(template method)** 선언:
     - `protected abstract String callModel(String userPrompt, int maxTokens);`
     - `protected abstract String callModelWithSystem(String systemPrompt, String userPrompt, int maxTokens);`
   - `isConfigured()`는 추상 유지(`protected abstract boolean isConfigured();` 는 interface가 이미 선언 → 각 구현체가 채움) 하거나, 공통화하려면 `protected abstract String apiKey();` 도입. **모호하면 각 서비스에 isConfigured 그대로 두는 쪽 선택** (최소 변경).
3. **`ClaudeService` 수정** — `extends AbstractLlmClient`. 남기는 것은:
   - 생성자(WebClient/model/apiKey 주입) 그대로.
   - `isConfigured()` (apiKey 기반) 그대로.
   - 기존 `callClaude` → `callModel`로 이름 맞춰 `@Override` 구현 (Claude의 URI/헤더/바디/`content[0].text` 파싱).
   - 기존 `callClaudeWithSystem` → `callModelWithSystem`로 `@Override` 구현.
   - 위에서 추출된 프롬프트/파싱 메서드는 **삭제**(상위로 이동했으므로).
4. **`OpenAiService` 수정** — 동일 패턴. `call` → `callModel`, `callWithSystem` → `callModelWithSystem` `@Override`. OpenAI의 URI/헤더/바디/`choices[0].message.content` 파싱만 남김.
5. **컴파일 확인:** `cd backend && ./gradlew compileJava` 통과. (실행 결과 캡처는 사용자.)

## 7. Pre-write 프로토콜 적용 여부

- [ ] **Skip**
- [x] **Required** — 4파일·추상화 도입으로 복잡도 ↑. Copilot은 **구현 전 변경 계획을 3~5개 불릿으로 먼저 제시**하고 사용자 승인 후 구현. 특히 isConfigured 공통화 여부는 계획 단계에서 명시.

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `LlmClient` interface와 `AbstractLlmClient` 추상 클래스가 신규 생성됨.
- [ ] `ClaudeService`/`OpenAiService`가 `AbstractLlmClient`를 상속하고, 클래스명·`@Service`·공개 메서드 시그니처가 모두 현행과 동일.
- [ ] `buildSummaryPrompt`/`buildExtractPrompt`/`parseStructured`/`extractJsonArray`/`parseLines`/`nvl`가 **두 서비스에서 사라지고 상위에 단 1벌**만 존재.
- [ ] 프롬프트 문자열이 기존과 **글자 단위로 동일** (diff에서 프롬프트 본문 변경 없음).
- [ ] Claude의 `content[0].text`, OpenAI의 `choices[0].message.content` 파싱 경로가 각 서비스에 그대로 보존.
- [ ] `extractStructuredActionItems`의 "1회 재시도" 로직이 보존됨.
- [ ] `MeetingController`·`GoogleCalendarService` diff **0줄** (이 Task에서 건드리지 않음).
- [ ] `./gradlew compileJava` 통과.
- [ ] HARD LIMIT 외 파일 변경 없음. CI(refactor-guard) 통과.

## 9. PR 정보

- **Branch:** `refactor/26-llm-client-abstraction`
- **PR base:** `refactor/main` (절대 `main` 아님)
- **PR title:** `[refactor-26] LLM 클라이언트 추상화 (Claude/OpenAI 공통 로직 추출)`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 26](../docs/refactor/tasks/26-llm-client-abstraction.md)

  ## 변경 요약
  - LlmClient interface + AbstractLlmClient 추상 클래스 신규
  - Claude/OpenAiService를 상속 구조로 전환 (provider 차이만 override)
  - 중복 프롬프트·파싱 로직 1벌로 통합

  ## HARD LIMIT 준수
  - 파일: LlmClient(신규)/AbstractLlmClient(신규)/ClaudeService/OpenAiService
  - 라인: ~250 (예상 ±30% 내)

  ## 검수 체크리스트 결과
  - [x] 공개 시그니처 불변, 소비자 2곳 diff 0
  - [x] 프롬프트 글자 단위 동일
  - [x] compileJava 통과
  ```
