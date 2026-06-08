# Session Log

> Claude Code 세션별 핸드오프 기록. **append-only** (가장 최근 entry가 맨 아래).
> 각 entry: ~10줄. 새 PC에서 마지막 5 entry를 읽으면 진행 상태 파악 가능.
> 형식: 일시 + 모델 + 한 일 + 핵심 결정 + 다음 진입점 + 미해결.

---

## 2026-05-09 — Session 1: 리팩토링 인프라 전면 구축

**진행자:** 사용자 + Claude Code (Opus 4.7)

**한 일:**
- 리팩토링 전체 계획 수립: Phase 0~6 로드맵 (PLAN.md)
- 인프라 문서: PRINCIPLES.md (§13까지), TASK_TEMPLATE.md, EXECUTION_PLAYBOOK.md
- Copilot 가드: `.github/copilot-instructions.md` (절대 규칙 + 코드 패턴 4개), `.github/workflows/refactor-guard.yml` (critical fail + warn + PR size)
- Phase 0 작업지시서 8개: 00(files/ archive) / 01(handover_log paths) / 02(build baseline, 오케스트레이터) / 03(test coverage, 오케스트레이터) / 04(PR template) / 05(SMOKE_TESTS, Claude 직접) / 06(smoke baseline run, 오케스트레이터) / 07(REVIEW_CHECKLIST)
- Phase 1 작업지시서 3개: 10/11/12 (drift scan INV/SYNC/CODE, 모두 오케스트레이터)
- 다른 AI 평가 2회 받음. 거부 6건 + 부분 채택 1건 → DECISIONS DEC-REJECT-001~006
- Claude Code 운영 정책 신규: CLAUDE.md(진입점), CLAUDE_WORKFLOW.md(정책), DECISIONS.md(누적 결정), 본 SESSION_LOG.md
- 메모리 8개 → repo로 이중 관리 시작 (canonical = repo)

**핵심 결정 (DECISIONS 참조):**
- DEC-WORKFLOW-001~009 (Copilot 실행자, main push 금지, drift 제시-검토, 1~3 묶음, 3겹 방어선, Claude 예외 4조건, 오케스트레이터 패턴, 강의 컨텍스트, repo canonical)
- DEC-PHASE-001~002 (7단계 로드맵, 00~07 사용·08~09 gap)

**다음 세션 진입점 (선택 가능):**
- (a) Phase 1 두 번째 묶음 (Task 13 drift-classify, 14 user-review, 15 task-mapping) 작성
- (b) 사용자가 Phase 0 실행 시작 → EXECUTION_PLAYBOOK 따라 진행 (강의 시간)
- (c) 인프라 일괄 commit + push (현재 모든 새 파일 unstaged)

**미해결:**
- 모든 새 파일 unstaged → 강의 전 commit + push 필요
- DEFERRED.md는 미생성 (필요 시점까지 lazy)
- DRIFT_INVENTORY.md는 Phase 1 Task 10에서 생성 예정
- Phase 2~6 작업지시서 미작성 (Phase 1 결과 보고 진행)

**다른 PC 진입 시 추가 확인:**
- 본 entry + DECISIONS DEC-WORKFLOW-001~009 + DEC-REJECT-001~006 읽기
- 메모리는 비어있겠지만 위만으로 충분 (DEC-WORKFLOW-009)

---

## 2026-05-09 — Session 1.1: 인프라 commit + push (이어서)

**진행자:** 사용자 (push 직접 수행) + Claude Code (Opus 4.7, 커밋 작성)

**한 일:**
- `refactor/main` → `origin` push (이전엔 로컬만)
- `refactor/setup-infrastructure` 분기 후 PRINCIPLES §5 "한 커밋에 한 종류" 룰 따라 4개 commit:
  - `f856f06` chore(refactor): Copilot 가드 (.github/copilot-instructions.md + refactor-guard.yml)
  - `b6d86e6` docs(refactor): 원칙·로드맵·템플릿·실행 가이드 (PRINCIPLES/PLAN/TASK_TEMPLATE/EXECUTION_PLAYBOOK)
  - `fe4d51f` docs(refactor): Claude Code 운영 정책·추적 (CLAUDE/CLAUDE_WORKFLOW/DECISIONS/SESSION_LOG)
  - `1099a6f` docs(refactor): Phase 0/1 작업지시서 11개 (tasks/00~07, 10~12)
- Claude Code의 OAuth가 `workflow` scope 부재로 `.github/workflows/*.yml` push 거부 → 사용자가 본인 환경에서 직접 push 수행

**핵심 결정:**
- 부트스트랩 commit도 PR 패턴 적용 (직접 `refactor/main` commit 회피)
- Workflow 파일 OAuth 제한은 차후 동일 패턴 발생 시 사용자 push 기본 가정

**다음 세션 진입점:**
- (a) 사용자: setup-infrastructure PR 생성·검토·머지 (`refactor/main`으로 squash merge)
- (b) PR 머지 후: Phase 1 두 번째 묶음 (Task 13/14/15) 작성 또는 Phase 0 실행 시작 (강의 시간)

**미해결:**
- PR 생성·검토·머지 (사용자 액션)
- 본 SESSION_LOG 갱신(이 entry)이 5번째 commit 후보로 남음 → 어떻게 머지 PR에 포함할지 결정 필요
- DRIFT_INVENTORY.md, DEFERRED.md는 lazy 생성 (Phase 1 시점)
- Phase 2~6 작업지시서 미작성 (Phase 1 결과 보고 진행)

---

## 2026-05-11 — Task 70: 로그인 auth 리다이렉트 수정

**진행자:** 사용자 + Copilot

**한 일:**
- `frontend/src/components/AuthProvider.tsx` 신규 추가
- `frontend/src/app/layout.tsx` 에서 `AuthProvider`로 `children` 감싸기만 수행
- `frontend/src/app/login/page.tsx` 에서 마운트 시 auth 체크 + `router.replace('/dashboard')` 적용
- `frontend/src/lib/store/authStore.ts` 의 `initFromStorage()` 를 boolean 반환으로 보강하고, 토큰 미보유 시 localStorage/store 정리
- 로그인 성공 후 이동도 `router.push` 대신 `router.replace` 로 변경

**핵심 결정:**
- layout은 서버 컴포넌트로 유지하고, 인증 재수화/서버 검증은 클라이언트용 `AuthProvider` 에서 처리
- 브라우저 히스토리에 `/login` 이 남지 않도록 로그인 성공 리다이렉트는 `replace` 사용

**검증 상태:**
- 정적 검사로 신규 컴포넌트/스토어 수정은 통과
- 로그인 페이지 쪽 타입 경고는 의존성 갱신 후 해소
- 브라우저 뒤로/앞으로 시나리오 수동 검증은 다음 단계

**다음 진입점:**
- 수동 브라우저 검증 3건 실행 후 PR 작성

**미해결:**
- 뒤로/앞으로 및 새 탭 직접 진입 시나리오 수동 검증 미완료
- PR 생성 전 최종 diff 확인 필요

---

## 2026-06-01 — AI LLM 클라이언트 통합 작업지시서 작성 (Task 26~28)

**진행자:** 사용자(팀장) + Claude Code (Opus 4.8, 계획자)

**한 일:**
- `origin/main` 6커밋 fast-forward pull (Notion 설정·Discord 알림·Calendar 확장 등). 로컬 미커밋 md 2개는 충돌 없이 보존.
- 리팩토링 후보 조사: `ClaudeService`(217줄) ↔ `OpenAiService`(214줄)가 ~95% 중복(프롬프트·파싱 byte 단위 동일), 폴백 분기가 3곳(MeetingController ×2, GoogleCalendarService ×1) 복붙됨을 확인.
- 소비자 전수조사: 두 AI 서비스 호출자는 `MeetingController`·`GoogleCalendarService` **단 2곳**. `extractActionItems()`(List<String>)는 호출자 0 = 죽은 코드.
- 작업지시서 3개 신규 작성:
  - `26-llm-client-abstraction` — `LlmClient` interface + `AbstractLlmClient`로 공통 추출 (4파일, §13 예외 명기)
  - `27-ai-service-orchestrator` — `AiService`(List<LlmClient> + @Order 폴백) 도입, MeetingController 인라인 폴백 제거
  - `28-calendar-use-ai-service` — GoogleCalendarService를 AiService.rawCall 위임

**핵심 결정:**
- **Phase 게이트 fast-track:** PLAN상 이 작업은 Phase 2A(cross-cutting)라 Phase 0/1 뒤에 와야 하나, 사용자가 **단독 패스트트랙(26~28만)** 결정. 순수 내부 리팩토링(동작 보존)이라 리스크 낮음.
- **패키지 평면 유지:** `service/ai/` 이동 대신 `service/` 평면 유지 — 소비자 import 깨짐·move 커밋 혼입 방지(PRINCIPLES §5), §13 파일수 룰 유리.
- **폴백 순서 보존 수단:** Spring `List<LlmClient>` 주입 순서 미보장 → `@Order(1/2)`로 Claude 우선 명시(Task 27).
- API surface 불변 → 전 Task 문서 갱신 면제(PLAN §2B).

**다음 진입점:**
- (a) 사용자: Task 26~28 작업지시서 검토 → 실행자 서브에이전트(refactor-executor, Sonnet)에 Task 26부터 순차 실행(26 머지 후 27, 27 머지 후 28).
- (b) `extractActionItems()` 죽은 코드는 Phase 3 `64-dead-code-remove` 후보로 보류.

**미해결:**
- Task 26~28은 아직 미실행(작업지시서만 작성). `refactor/main` 분기·PR은 사용자/실행자 서브에이전트 액션.
- 로컬 미커밋 md 2개(handover_log/todo 쿠키 인증 동기화) 커밋 여부 미정.
- fast-track 결정을 DECISIONS.md에 정식 등재할지 미정(현재 본 SESSION_LOG에만 기록).

**추가 (같은 세션) — 실행자 교체:**
- 사용자 결정으로 **실행자를 Copilot → Claude Sonnet 4.6 서브에이전트로 교체**.
- `.claude/agents/refactor-executor.md` 신규: 작업지시서 1건을 HARD LIMIT 안에서 실행 + 빌드 자체 검증, git push/main 금지.
- `DECISIONS.md`에 **DEC-WORKFLOW-010**(DEC-WORKFLOW-001 개정) 등재. §13 권장에 따라 Haiku 대신 Sonnet 채택.
- **동기화 완료:** `copilot-instructions.md`(DEPRECATED stub화), `CLAUDE_WORKFLOW.md §1`, `PRINCIPLES.md §0·§12`, `CLAUDE.md §2·§5·§7`, `DECISIONS.md`(DEC-001 개정 포인터 + DEC-005 갱신).
- **후속 동기화 필요(미반영):** `EXECUTION_PLAYBOOK.md`(강의 실행 가이드 — Copilot Edits 절차 전반 재작성 필요), `PLAN.md` 오케스트레이터/Copilot prose(18·37·41·87·120·131), `CLAUDE_WORKFLOW §4·§5`(예외실행·오케스트레이터 전제 변화), 기존 작업지시서 01·04·13·14의 Copilot 표현. SESSION_LOG 과거 entry는 사실 기록이라 보존.

---

## 2026-06-01 — Task 26 실행·머지 (Sonnet 실행자 첫 적용) + 다음 세션 핸드오프

**진행자:** 사용자(팀장) + Claude Code (Opus 4.8, 계획자) + refactor-executor (Sonnet 4.6, 실행자)

**한 일:**
- `refactor/main`을 origin/main 최신(a3418b2: SOFT BLOCK fix·주간보고서 gitignore)까지 동기화 → 리팩토링 인프라 3커밋과 함께 origin push (현재 `refactor/main` = `29737ee`).
- **Task 26 실행** (DEC-WORKFLOW-010 첫 적용): refactor-executor 서브에이전트(Sonnet)가 `LlmClient` interface + `AbstractLlmClient` 추출, `ClaudeService`/`OpenAiService`를 상속 구조로 전환. ClaudeService −136·OpenAiService −113줄, 소비자(MeetingController/GoogleCalendarService) diff 0, `compileJava` BUILD SUCCESSFUL.
- PR **#9** (base `refactor/main`) → squash 머지 완료(`29737ee`). 머지된 로컬·원격 작업 브랜치 정리.

**핵심 결정:**
- **buildExtractPrompt provider 차이 보존** — 원본에 이미 있던 drift(Claude엔 "출력 예시" 있고 OpenAI엔 없음). 동작 보존(PRINCIPLES §4) 위해 OpenAiService가 `@Override`로 유지. `[CONFIRM:?]` → 설명 주석으로 정리. 통일은 보류(후속 fix Task 후보).
- Task 26 4파일 ≤3 §13 예외(2개는 순수 추출 신규)로 진행 — 문제 없었음.

**다음 세션 진입점 — Task 27 실행:**
- 새 세션은 `refactor/main`에서 시작 → `.claude/agents/refactor-executor.md`가 커밋돼 있어 **`subagent_type: refactor-executor`로 바로 호출 가능**(이번 세션엔 미등록이라 general-purpose+sonnet으로 우회했음).
- 절차: `refactor/main`에서 `refactor/27-ai-service-orchestrator` 분기 → 실행자에 `docs/refactor/tasks/27-ai-service-orchestrator.md` 위임 → 검수 → PR(base refactor/main). 의존(Task 26 머지) **충족됨**.
- §7 Pre-write Required(Task 27) — 코드 전 계획 확인.

**미해결 / 주의:**
- **Task 28 라인 참조 주의:** 작업지시서의 "현 219~233행"이 origin의 SOFT BLOCK 수정(55c7092)으로 밀렸을 수 있음 → 실행 시 raw 라인 대신 `recommendMeetingTimes()`의 AI 폴백 if/else 구조 기준으로 찾을 것. (Task 28 실행 전 라인 참조 최신화 권장.)
- `stash@{0}`에 `md/handover_log.md`·`md/todo.md` 쿠키 인증 문서 변경 보존 중(이번 리팩토링 무관, 미처리).
- 후속 거버넌스 동기화 미반영분(직전 entry 참조): EXECUTION_PLAYBOOK·PLAN prose·CLAUDE_WORKFLOW §4·§5·task 01/04/13/14.
- `main`은 미변경(origin/main = a3418b2). main push는 전체 리팩토링 완료 시에만(DEC-WORKFLOW-002).

---

## 2026-06-08 — Task 27·28·55 실행·머지 (AI LLM 통합 fast-track 완결)

**진행자:** 사용자(팀장) + Claude Code (Opus 4.8, 계획자) + refactor-executor (Sonnet 4.6, 실행자)

**한 일:**
- 세션 시작 시 `refactor/main`을 origin 최신까지 동기화(직전 핸드오프 PR #10 머지 확인).
- **Task 27 실행·머지(PR #11, `5f3887d`):** `subagent_type: refactor-executor` 첫 정식 호출. §7 Pre-write Required → 실행자가 변경 계획 5불릿 제시 → 사용자 승인 후 구현. 신규 `AiService`(`List<LlmClient>` + `@Order(1)`Claude/`@Order(2)`OpenAI 폴백 중앙화), `MeetingController` 인라인 폴백 2메서드 제거. 예외 메시지 byte 보존, `compileJava` SUCCESSFUL.
- **Task 28 실행·머지(PR #12, `3cbb68e`):** `GoogleCalendarService.recommendMeetingTimes()`의 마지막 인라인 폴백 → `aiService.rawCall` 위임. 라인 참조(작업지시서 219~233)가 밀려 if/else **구조 기준**으로 찾음(실제 226~232행). 바깥 try/catch 보존. **AI 폴백 분기 3곳 → 0곳 완결.**
- **Task 55 실행·머지(PR #13, `5edd545`):** 트랙 선택(사용자: AI 후속 정리). 죽은 `extractActionItems(List<String>)` + 그것만 호출하는 `buildExtractPrompt`(+OpenAiService override)·`parseLines` 통째 제거(3파일 −65줄). 작업지시서 신규 작성(`55-remove-dead-llm-extract-path.md`).

**핵심 결정:**
- **drift 통일 fix 불필요(삭제로 자연 해소):** 트랙 B 후보 ②(buildExtractPrompt provider drift 통일=동작변경 fix)는, `buildExtractPrompt`가 죽은 `extractActionItems`만 호출함을 의존사슬로 확인 → 통째 삭제(Task 55)하면 drift가 사라져 별도 fix PR 불필요. 두 후속 항목이 단일 `refactor:` PR로 수렴.
- **소비자 0 증명 후 삭제:** repo 전체 grep(코드 호출자 0) + 컴파일 백스톱(인터페이스 메서드 삭제 시 숨은 참조 있으면 fail) 이중 검증. 살아있는 `extractStructuredActionItems`(`/ai/extract-actions`)는 별도 메서드로 미변경.
- Task 28 라인참조는 raw 라인 대신 구조 기준 탐색으로 해결(직전 핸드오프 주의사항 반영).

**다음 진입점:**
- (a) **거버넌스 동기화 부채(미반영, 본 PR 범위 밖):** EXECUTION_PLAYBOOK(Copilot Edits 절차 재작성)·PLAN prose(18·37·41·87·120·131행)·CLAUDE_WORKFLOW §4·§5·task 01/04/13/14의 Copilot→executor 표현. 별도 docs Task 권장.
- (b) **로드맵 본궤도 미착수:** Phase 0 Task 02~07(build baseline·SMOKE_TESTS·REVIEW_CHECKLIST 등)·Phase 1 Task 10~15(drift scan) 작업지시서만 있고 미실행. `DRIFT_INVENTORY.md`·`baseline.md`·`SMOKE_TESTS.md` 부재 확인.

**미해결 / 주의:**
- AI fast-track(26~28) 결정을 `DECISIONS.md`에 **DEC-PHASE-003**으로 정식 등재(본 세션 PR에 포함).
- `stash@{0}` 쿠키 인증 md 변경 여전히 미처리(무관, 보존).
- AI API 키가 다른 PC에 있어 27·28·55 모두 런타임 테스트 불가 → `compileJava`+diff로 동작 보존 검증(작업지시서 §8 허용).
- `main` 미변경(origin/main = a3418b2). main push는 전체 리팩토링 완료 시에만(DEC-WORKFLOW-002).
