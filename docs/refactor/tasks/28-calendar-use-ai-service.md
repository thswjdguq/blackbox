# Task 28 — GoogleCalendarService를 AiService로 위임

## 1. 한 줄 요약
`GoogleCalendarService.recommendMeetingTimes()`에 복붙된 마지막 "Claude→OpenAI" 인라인 폴백을 `AiService.rawCall()` 위임으로 바꿔, 폴백 로직 3곳 중 마지막 하나를 제거한다.

## 2. 변경 범위 (HARD LIMIT)

> Copilot은 이 섹션 밖의 파일·메서드를 절대 건드리지 않는다.

- **파일 (이외 금지):**
  - `backend/src/main/java/com/blackbox/service/GoogleCalendarService.java`
- **메서드/함수/클래스 (이외 금지):**
  - 필드 선언부 (`claudeService`, `openAiService` → `aiService`)
  - 생성자 (주입 파라미터 교체)
  - `recommendMeetingTimes()` 내부의 AI 호출 분기 (현 219~233행)
- **예상 변경 라인 수:** ~15줄
- **예상 변경 파일 수:** 1개

## 3. 절대 건드리지 말 것 (Out of Scope)

- `GoogleCalendarService`의 다른 메서드: `getAvailability`, `createCalendarEvents`, `handleCallback`, `ensureFreshToken`, `getConnectionStatus`, `disconnect`, `buildAuthorizationUrl`, `getConnectionStatus`, 슬롯 점수 계산(`computeScoredSlots` 등) — **전부 손대지 말 것**.
- `recommendMeetingTimes()`의 try/catch **구조·동작 변경 금지**:
  - `IllegalStateException`은 그대로 전파(`catch (IllegalStateException e) { throw e; }`).
  - 그 외 `Exception`은 삼키고 기본 이유로 대체(`catch (Exception ignored)`).
  이 동작이 바뀌면 AI 호출 실패 시 캘린더 추천 전체가 깨진다 — **반드시 보존**.
- `parseReasonArray`, `defaultReason`, `buildScoredSlotPrompt` 등 호출 메서드 변경 금지.
- consent(INV-07) 체크 신규 추가 금지.

## 4. 컨텍스트 / 의도

`recommendMeetingTimes()`는 AI로 회의 슬롯 추천 이유를 생성할 때 다음 인라인 폴백을 쓴다(현 219~227행):

```java
if (claudeService.isConfigured()) {
    raw = claudeService.rawCall(prompt, 500);
} else if (openAiService.isConfigured()) {
    raw = openAiService.rawCall(prompt, 500);
} else {
    throw new IllegalStateException("AI API 키가 설정되지 않았습니다 (CLAUDE_API_KEY 또는 OPENAI_API_KEY 필요)");
}
```

이는 Task 27이 `MeetingController`에서 제거한 것과 **동일한 패턴의 마지막 잔존**이다.
`AiService.rawCall()`이 내부에서 "설정된 첫 클라이언트 선택 + 미설정 시 동일 메시지의 `IllegalStateException`"을
이미 처리하므로(Task 27), 이 블록을 한 줄 위임으로 줄일 수 있다. 바깥 try/catch가
`IllegalStateException`을 그대로 전파하므로 **AI 미설정 시 동작도 동일하게 보존**된다.

- 관련 INV: 해당 없음
- 관련 Drift ID: 해당 없음 (fast-track)
- 관련 PRINCIPLES 섹션: §1(중복 제거), §4(동작 보존), §8(폴백 패턴 유지)

## 4-1. 의존 관계

- **선행 Task (이게 먼저 머지되어야 함):** **27-ai-service-orchestrator** (`AiService.rawCall` 이 있어야 위임 가능)
- **후행 Task (이 Task 머지 후 진행):** 없음 (AI 통합 마무리)
- **API surface 변경 여부:** No
  - `/meetings/recommend` 응답 형태·동작 불변 → 문서 갱신 면제.

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §4 (동작 보존 원칙):

리팩토링 커밋은 외부에서 본 동작이 동일해야 한다. 행동 보존이 의심되면 유예(§7)한다.

> PRINCIPLES.md §8 (외부 HTTP / AI 서비스):

외부 HTTP는 `WebClient`. 에러 핸들러 필수. 미처리 예외가 Security 필터를 거쳐 403으로
잘못 변환되지 않게 주의. AI 서비스: Claude → OpenAI 폴백 패턴 유지.

> PRINCIPLES.md §6 (Copilot 협업 규칙):

- 추측 금지. 변경 범위 작업지시서 외 확장 금지.
- 임포트 자동 정리 결과는 수동 확인.

## 6. 작업 절차

1. **필드 교체:** `private final ClaudeService claudeService;` / `private final OpenAiService openAiService;`
   두 줄을 `private final AiService aiService;` 한 줄로 교체.
2. **생성자 교체:** 생성자 파라미터에서 `ClaudeService claudeService, OpenAiService openAiService` 제거,
   `AiService aiService` 추가. 본문 할당부도 동일하게 교체.
3. **`recommendMeetingTimes()` 내부 분기 교체** — 현 220~227행의 if/else if/else 블록을:
   ```java
   raw = aiService.rawCall(prompt, 500);
   ```
   한 줄로 교체. **바깥 try/catch(`IllegalStateException` 전파 / 그 외 무시)는 그대로 둔다.**
4. **import 정리:** 사용 안 하게 된 `ClaudeService`/`OpenAiService` import 제거 (수동 확인). `AiService`는 같은 패키지이므로 import 불필요.
5. **컴파일 확인:** `cd backend && ./gradlew compileJava` 통과.

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — HARD LIMIT 명확, 단일 파일·~15줄. 단 §3의 try/catch 보존 제약을 반드시 지킬 것.
- [ ] **Required**

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `GoogleCalendarService`가 `ClaudeService`/`OpenAiService`를 더 이상 주입받지 않음 (오직 `AiService`).
- [ ] `recommendMeetingTimes()`의 AI 분기가 `aiService.rawCall(prompt, 500)` 단일 호출로 축약됨.
- [ ] 바깥 try/catch 동작 보존: AI 미설정 → `IllegalStateException` 전파 / AI 호출 실패 → 기본 이유로 대체.
- [ ] `/meetings/recommend` 응답·동작 불변.
- [ ] 다른 메서드 diff 0줄.
- [ ] `./gradlew compileJava` 통과. HARD LIMIT 외 변경 없음. CI 통과.

## 9. PR 정보

- **Branch:** `refactor/28-calendar-use-ai-service`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-28] GoogleCalendarService를 AiService로 위임`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 28](../docs/refactor/tasks/28-calendar-use-ai-service.md)

  ## 변경 요약
  - recommendMeetingTimes의 인라인 Claude→OpenAI 폴백 제거 → aiService.rawCall 위임
  - ClaudeService/OpenAiService 직접 주입 제거 (AI 폴백 3곳 → 0곳)

  ## HARD LIMIT 준수
  - 파일: GoogleCalendarService
  - 라인: ~15 (예상 ±30% 내)

  ## 검수 체크리스트 결과
  - [x] try/catch 동작 보존 (미설정 전파 / 실패 시 기본 이유)
  - [x] 추천 엔드포인트 동작 불변
  ```
