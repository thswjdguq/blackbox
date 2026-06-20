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

---

## 2026-06-15 — AI fast-track 마무리 + Phase 0 완주 + 발산 관리 체계 구축

**진행자:** 사용자(팀장) + Claude Code (Opus 4.8, 계획자) + refactor-executor (Sonnet 4.6)

**한 일 (PR 다수):**
- **AI fast-track 완결:** Task 27(AiService 폴백 중앙화, #11)·28(Calendar 위임, #12)·55(죽은 extract-lines 제거, #13). AI 폴백 분기 3곳→0곳. DEC-PHASE-003 등재(#14).
- **거버넌스 동기화(①/①b):** 핵심 정책 4문서 Copilot→실행자 표현 정렬(#15), EXECUTION_PLAYBOOK 서브에이전트 모델로 전면 재작성(#16).
- **Phase 0 완주:** Task 02(baseline #17)·03(test coverage #18)·04(PR템플릿 #19)·05(SMOKE 작성 #21)·06(SMOKE 베이스라인 실행 #23, S1~S7 전체 통과)·07(REVIEW_CHECKLIST #26). 작업지시서 02·03을 실행자-직접 모델로 개정. **Phase 0 완료조건 8개 전부 [x].**
- **Task 72 fix(#22):** 회원가입 페이지 인증 인터셉터가 /signup에서 /login으로 튕기는 버그(SMOKE S1 발견) 수정.
- **발산 관리 체계:** DEC-WORKFLOW-011(전방통합 우선) 등재(#20) + CI 발산 감지 job·`scripts/sync.sh`·플레이북 참조(#27).
- **CI 가드 부활:** refactor-guard.yml YAML 90행 문법오류 + INV-02 주석 false positive 수정(#25). 여태 미실행이던 refactor-guard가 실제 동작.

**핵심 결정·사건:**
- **전방통합 2회 실전:** 팀원이 main에 푸시(`bafc8fe`, `060d8f6`/`a40a931`)해 발산 시작 → main→refactor/main merge로 흡수. node_modules/bin 쓰레기 untrack(+.gitignore 보강), GoogleCalendarService 자동머지(빌드검증), api.ts는 팀원 `PUBLIC_PATHS`로 네이밍 정렬(향후 충돌 제거).
- **회원가입 fix는 main에 안 올림:** 팀원이 동일 fix를 main에 먼저 반영(`PUBLIC_PATHS`) → 중복이라 PR #24 close. **`main`은 우리가 전혀 미변경**, DEC-WORKFLOW-002 유지.
- **nginx upstream startup race** 발견(SMOKE 셋업 중): frontend 기동 후 nginx 재생성 필요. Task 73 후보.
- 실행자 셸이 WSL/Git Bash 불확정 → gradle/npm은 계획자가 WSL 내부 재실행으로 검증(Task 03·머지 빌드 등).

**다음 진입점:**
- (a) **Phase 1**(Drift 스캔 10~15) — 본격 리팩토링 진입. 또는
- (b) **Task 73**(nginx race fix — resolver 패턴).

**미해결 / 주의:**
- `stash@{0}` 쿠키 인증 md 변경 여전히 미처리(무관, 보존).
- task 작업지시서 10~15의 오케스트레이터 표현은 Phase 1 실행 시 실행자-직접으로 개정(02·03 선례).
- 세션 시작 시 `bash scripts/sync.sh`로 전방통합 먼저(누락 시 CI divergence-check가 경고).
- `main` 미변경(origin/main = a40a931, 팀원 커밋만). main push는 전체 완료 시(DEC-WORKFLOW-002).

---

## 2026-06-18 — Phase 1 완주 (Drift 인벤토리) + Phase 2 핸드오프

**진행자:** 사용자(팀장) + Claude Code (Opus 4.8, 계획자) + refactor-executor (Sonnet 4.6)

**한 일 (Phase 1 전체):**
- **Task 10·11·12 (스캔, PR #29·30·31):** 실행자가 gc.md INV/SYNC/CODE grep 직접 실행 → `DRIFT_INVENTORY.md` 채움. 계획자 spot-check 검증.
  - INV: clean 3(INV-02/03/05) + 후보 5. SYNC: 10건. **CODE: 전부 clean**(`:any`·debug·시크릿 0).
- **Task 13 (분류, PR #32):** Claude 등급/결정 1차안 + `DEFERRED.md` 신규 + **DEC-DRIFT-001**.
- **Task 14 (사용자 확정, PR #33):** 사용자 결정 컬럼. *(주의: Task 14에서 D2·env·TS↔DTO 6행의 결정안↔사용자결정 셀이 collapse되는 버그 발생 → Task 15에서 8칸 복구.)*
- **Task 15 (매핑, 본 PR):** Task 컬럼 채움 + PLAN Phase 2~4 반영 + Phase 1 완료조건 [x] + Task 14 셀 정정.

**핵심 결정 (사용자):**
- **"기획서엔 있으나 MVP에 없는 기능 갭" = 리팩토링 범위 밖 → 차후 고도화·팀 전원**(DEC-DRIFT-001). 점수체계 0~150 정규화·consent UI·OBSERVER 강제·consent 가드 → `DEFERRED.md`(DEFER-01~03).
- **D2 문서 항목:** Phase 6 갱신 시 "기존 문서↔현행 코드 비교 후" 조건.
- 점수체계 괴리가 INV-06 + SYNC-05-A/B/C로 3중 확인됐으나 **수정 안 함**(고도화 유예).

**Phase 1 최종 매핑 (실제 리팩토링 D1은 3 Task뿐):**
- D1 → `22-activity-log-coverage`(D-INV-01a/b·최후순위) · `42-types-organize`(D-SYN-02-B) · `29-fix-env-config`(D-SYN-04, 신규 chore)
- D2 4건 → Phase 6 / 유예 7건 → DEFERRED.md
- **CRITICAL(하드코딩 시크릿) 0.**

**다음 세션 진입점 — Phase 2 (새 세션 권장):**
- 시작 시 `bash scripts/sync.sh` 전방통합 먼저. `docs/refactor/CLAUDE.md §3` 통독.
- **Phase 2A 첫 묶음(1~3개):** `20-exception-handling-unify` · `21-webclient-error-pattern` · `22-activity-log-coverage`(D-INV-01) 중에서. `29-fix-env-config`(소규모)도 끼울 수 있음.
- 입력: `DRIFT_INVENTORY.md`(Task 컬럼)·`PLAN.md` Phase 2A 후보·`DEFERRED.md`.

**미해결 / 주의:**
- `stash@{0}` 쿠키 인증 md 변경 미처리(무관, 보존).
- CODE-03 행 코드실제에 셀 내부 `|`(`ls V*.sql | sort -V`) 잔존 — clean 행이라 무해, 추후 escape 가능.
- `main` 미변경(origin/main = a40a931). main push는 전체 완료 시(DEC-WORKFLOW-002).
