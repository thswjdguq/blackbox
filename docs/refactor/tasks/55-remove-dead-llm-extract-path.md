# Task 55 — 죽은 LLM extract-lines 경로 제거 (extractActionItems + buildExtractPrompt + parseLines)

## 1. 한 줄 요약
호출자가 0인 `extractActionItems(List<String>)`와, 그것만 호출하는 `buildExtractPrompt`·`parseLines`를 통째로 제거한다. (Task 26이 유예한 죽은 코드 정리.)

## 2. 변경 범위 (HARD LIMIT)

> 실행자는 이 섹션 밖의 파일·메서드를 절대 건드리지 않는다.

- **파일 (이외 금지):**
  - `backend/src/main/java/com/blackbox/service/LlmClient.java`
  - `backend/src/main/java/com/blackbox/service/AbstractLlmClient.java`
  - `backend/src/main/java/com/blackbox/service/OpenAiService.java`
- **메서드/함수/선언 (이외 금지) — 삭제 대상:**
  - `LlmClient`: `List<String> extractActionItems(String notes, String decisions);` 인터페이스 선언 1줄
  - `AbstractLlmClient`: `extractActionItems(...)` 구현 (현 46~49행), `buildExtractPrompt(...)` + 위 NOTE 주석 (현 85~111행), `parseLines(...)` (현 140~147행)
  - `OpenAiService`: `buildExtractPrompt(...)` override + 위 NOTE 주석 (현 105~108행 부근)
- **예상 변경 라인 수:** ~60줄 삭제 (순삭감, 추가 거의 없음)
- **예상 변경 파일 수:** 3개

## 3. 절대 건드리지 말 것 (Out of Scope)

- **`extractStructuredActionItems(...)` (반환 `List<ActionItemDto>`)** — 살아있는 경로. `/ai/extract-actions` 엔드포인트가 사용. **이름이 비슷하니 혼동 금지, 절대 삭제·변경 금지.**
- `summarizeMeeting`, `rawCall`, `isConfigured`, `callModel`, `callModelWithSystem`, `buildSummaryPrompt`, `parseStructured`, `extractJsonArray`, `nvl` — 전부 보존.
- `ClaudeService`는 이 Task에서 파일 자체를 열지 않는다 (override가 없으므로 변경 불필요). `MeetingController`/`GoogleCalendarService`/`AiService` 변경 금지.
- consent(INV-07) 체크 신규 추가 금지.

## 4. 컨텍스트 / 의도

`extractActionItems(List<String>)`는 Task 26 추상화 시 시그니처를 보존했지만 **코드 호출자가 0**이다(전수조사 완료: 정의 2곳 + 문서 언급 외 호출 없음). 이 메서드만 `buildExtractPrompt`와 `parseLines`를 호출하므로, 셋을 함께 제거하면 의존 사슬이 깔끔히 닫힌다.

- `buildExtractPrompt`의 provider drift(Claude엔 "출력 예시" 있고 OpenAI엔 없음)는 **삭제로 자연 해소** — 통일(fix) 불필요.
- 동작 보존: 호출자가 0이므로 외부에서 본 동작 불변. `compileJava`가 숨은 참조에 대한 하드 백스톱.

- 관련 INV: 해당 없음
- 관련 Drift ID: 해당 없음 (fast-track)
- 관련 PRINCIPLES 섹션: §1(중복/불필요 제거), §4(동작 보존)
- 선행 근거: `26-llm-client-abstraction.md`:32 ("죽은 코드 `extractActionItems()` 삭제 금지 — Phase 3에서 별도 처리") → 본 Task가 그 별도 처리.

## 4-1. 의존 관계

- **선행 Task:** 26 (LlmClient 추상화) 머지됨. 27·28 머지됨.
- **후행 Task:** 없음.
- **API surface 변경 여부:** No. `extractActionItems`는 어떤 REST 엔드포인트로도 노출되지 않음 → 문서 갱신 면제.

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §4 (동작 보존 원칙):

리팩토링 커밋은 외부에서 본 동작이 동일해야 한다. 호출자가 없는 죽은 코드 제거는 외부 동작에 영향이 없다.

> PRINCIPLES.md §6 (실행자 협업 규칙):

- 추측 금지. 변경 범위 작업지시서 외 확장 금지.
- 임포트 자동 정리 결과는 수동 확인.

## 6. 작업 절차

1. **`LlmClient.java`**: `List<String> extractActionItems(String notes, String decisions);` 선언 1줄 삭제. (`import java.util.List;`는 `extractStructuredActionItems` 반환 등에 여전히 필요하니 **남길 것** — 수동 확인.)
2. **`AbstractLlmClient.java`**:
   - `@Override public List<String> extractActionItems(...) { ... }` (현 46~49행) 삭제.
   - `buildExtractPrompt(...)` 메서드 + 바로 위 NOTE 주석 블록 (현 85~111행) 삭제.
   - `parseLines(...)` (현 140~147행) 삭제.
   - 삭제 후 사용처 없어진 import가 있으면 제거 (수동 확인). `List`·`ActionItemDto`·Jackson 등은 다른 메서드가 쓰므로 남는다.
3. **`OpenAiService.java`**: `@Override protected String buildExtractPrompt(...)` + 바로 위 NOTE 주석 (현 105~108행 부근) 삭제. 나머지(생성자·callModel 등) 불변.
4. **컴파일 확인:** `cd backend && ./gradlew compileJava` 통과. (실패 시 = 숨은 참조 존재 → 보고하고 중단.)

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — 삭제 대상이 라인 단위로 명확하고, 소비자 0이 전수조사로 증명됨. `compileJava`가 하드 백스톱.
- [ ] **Required**

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `LlmClient`에서 `extractActionItems` 선언 제거. 다른 4개 메서드 시그니처 불변.
- [ ] `AbstractLlmClient`에서 `extractActionItems`·`buildExtractPrompt`·`parseLines` 제거. 나머지 메서드 본문 0줄 변경.
- [ ] `OpenAiService`에서 `buildExtractPrompt` override 제거. 나머지(callModel 등) 0줄 변경.
- [ ] `extractStructuredActionItems`(살아있는 경로)·`/ai/extract-actions` 동작 불변.
- [ ] `ClaudeService`·`MeetingController`·`GoogleCalendarService`·`AiService` diff 0줄.
- [ ] `./gradlew compileJava` 통과. HARD LIMIT 외 변경 없음. CI 통과.

## 9. PR 정보

- **Branch:** `refactor/55-remove-dead-llm-extract-path`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-55] 죽은 LLM extract-lines 경로 제거 (extractActionItems/buildExtractPrompt/parseLines)`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 55](../docs/refactor/tasks/55-remove-dead-llm-extract-path.md)

  ## 변경 요약
  - 호출자 0인 extractActionItems(List<String>) + 그것만 쓰는 buildExtractPrompt/parseLines 제거
  - buildExtractPrompt provider drift는 삭제로 자연 해소 (통일 fix 불필요)
  - Task 26이 유예한 죽은 코드 정리

  ## HARD LIMIT 준수
  - 파일: LlmClient/AbstractLlmClient/OpenAiService (3개)
  - 라인: ~60 순삭감

  ## 검수 체크리스트 결과
  - [x] 소비자 0 전수조사 + compileJava 백스톱으로 동작 보존
  - [x] 살아있는 extractStructuredActionItems / extract-actions 불변
  ```
