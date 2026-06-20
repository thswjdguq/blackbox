# Decisions Log

> 누적 의사결정 로그. cross-session·cross-PC 의사결정의 단일 source.
> 새 세션은 본 문서 최근 항목을 읽어 같은 결정을 반복하지 않는다.
> 외부 AI 권고 거부도 여기 기록 (다른 PC에서 같은 권고 재발 시 즉시 동일 결정).

## ID 체계
- `DEC-WORKFLOW-NN` — 운영 워크플로우 결정
- `DEC-PHASE-NN` — Phase 진행 결정
- `DEC-DRIFT-NN` — Drift 처리 결정 (개별 D-XX 항목 결정은 DRIFT_INVENTORY에 기재)
- `DEC-REJECT-NN` — 거부한 외부 권고

## 형식
각 entry:
- **일시·결정·사유** 필수
- 대안·관련 문서·재검토 조건은 선택

---

## DEC-WORKFLOW-001 — Copilot이 실행자, Claude Code가 계획자
- **⚠️ 개정됨:** **DEC-WORKFLOW-010** (2026-06-01) — 실행자가 Copilot → Claude Sonnet 서브에이전트로 교체됨. 아래는 원 결정 기록(계획자/실행자 분리 원칙은 유효).
- **일시:** 2026-05-09
- **결정:** 대규모 리팩토링은 Copilot이 코드를 작성하고, Claude Code는 작업지시서·계획·검수를 담당한다.
- **사유:** Claude Code가 모든 코드를 읽고 직접 수정하면 사용량(Pro 플랜) 부담 큼. Copilot은 단가가 낮고 IDE 통합이 좋음.
- **관련:** PRINCIPLES §0, CLAUDE_WORKFLOW §1, 메모리 `workflow_copilot_executor.md`
- **재검토 조건:** Claude Pro 플랜 한도 변경 또는 Copilot 정책 변경 시

## DEC-WORKFLOW-002 — `main`에 절대 push 금지 (리팩토링 완료 전)
- **일시:** 2026-05-09
- **결정:** 리팩토링 완료 + 팀원 승인 전까지 `main` 브랜치에 어떤 변경도 가하지 않는다. 모든 작업은 `refactor/main` 또는 그 하위.
- **사유:** 리팩토링을 atomic하게 머지하기 위함. 팀원이 게이트키퍼(승인 후 머지).
- **관련:** PRINCIPLES §5, CLAUDE_WORKFLOW §6, 메모리 `project_refactor_vcs_policy.md`
- **재검토 조건:** 사용자가 리팩토링 완료 + 팀원 승인 명시 시

## DEC-WORKFLOW-003 — Drift 처리: 제시 후 검토
- **일시:** 2026-05-09
- **결정:** Drift 항목의 등급·결정안은 Claude Code가 1차안 제시, 사용자가 검토·확정.
- **사유:** 사용자가 모든 항목을 처음부터 분류하면 부담 大. 1차안이 있으면 검토만으로 확정 가능.
- **관련:** PRINCIPLES §3, CLAUDE_WORKFLOW §3, 메모리 `feedback_drift_workflow.md`

## DEC-WORKFLOW-004 — 작업 묶음 1~3 Task 단위
- **일시:** 2026-05-09
- **결정:** 한 번에 1~3개 Task 작업지시서를 묶어 사용자 검토 → 확정 → Copilot 순차 실행.
- **사유:** 1개씩 = 검토 횟수 과다. Phase 전체 = 변경 폭 과다(스파게티 위험). 1~3개가 균형점.
- **관련:** CLAUDE_WORKFLOW §2, 메모리 `feedback_refactor_workflow.md`

## DEC-WORKFLOW-005 — 3겹 방어선 (실행자 가드)
- **일시:** 2026-05-09 (2026-06-01 갱신)
- **결정:** 실행자 규칙 전달은 (1) 실행자 시스템 프롬프트 always-on + (2) 작업지시서 인라인 발췌 + (3) CI lint 3겹.
- **사유:** 실행자가 PRINCIPLES.md를 자동으로 안 읽음. 운영 가능한 형태로 분산 필요.
- **갱신(DEC-WORKFLOW-010):** 1차 always-on이 `.github/copilot-instructions.md`(Copilot) → **`.claude/agents/refactor-executor.md`**(Claude Sonnet 서브에이전트)로 이전. 구 파일은 DEPRECATED 참조 stub으로 보존.
- **관련:** PRINCIPLES §12, CLAUDE_WORKFLOW §1, DEC-WORKFLOW-010

## DEC-WORKFLOW-006 — Claude 예외 실행 4조건
- **일시:** 2026-05-09
- **결정:** 다음 4조건 모두 만족 시 Claude가 직접 Write 가능: (1) 정적 문서 생성 (2) 콘텐츠 완전 정의 (3) Copilot lost-in-the-middle 위험 큼 (4) 사용자 명시 승인.
- **사유:** Copilot이 250줄+ 단일 출력에서 약식화 빈번. 단순 transcription은 Claude가 1회 Write로 끝.
- **관련:** PRINCIPLES §13, CLAUDE_WORKFLOW §4, 메모리 `feedback_claude_as_executor_exception.md`
- **첫 적용:** Task 05 (SMOKE_TESTS.md 작성)

## DEC-WORKFLOW-007 — 명령 실행은 오케스트레이터 패턴
- **일시:** 2026-05-09
- **결정:** Copilot이 명령(grep/build/test 등)을 직접 실행하지 않음. 사용자가 실행, Copilot이 결과 정리.
- **사유:** 환각(hallucinate) 위험이 본질적. 작은 모델일수록 명령 출력을 만들어냄.
- **관련:** PRINCIPLES §13, CLAUDE_WORKFLOW §5
- **적용 Task:** 02, 03, 06, 10, 11, 12, 13(일부)

## DEC-WORKFLOW-008 — 강의 시간 실행 컨텍스트
- **일시:** 2026-05-09
- **결정:** 본 리팩토링 코드 변경은 학교 강의 시간에 진행. 모든 결정·작업지시서 강의 전 확정.
- **사유:** 강의 중 시간 압박, 즉석 결정 어려움. 사전 준비가 필수.
- **관련:** EXECUTION_PLAYBOOK, CLAUDE_WORKFLOW §7, 메모리 `project_class_execution_context.md`

## DEC-WORKFLOW-009 — Repo가 canonical, 메모리는 per-PC 캐시
- **일시:** 2026-05-09
- **결정:** 메타 결정·정책은 repo 먼저 갱신. 메모리는 선택적 동기화. 메모리에만 있는 항목은 cross-PC에서 잃음.
- **사유:** 다른 PC에서 Claude Code 신규 세션을 위해 단일 source 필요.
- **관련:** CLAUDE_WORKFLOW §8

## DEC-WORKFLOW-010 — 실행자를 Copilot → Claude Sonnet 서브에이전트로 교체 (DEC-WORKFLOW-001 개정)
- **일시:** 2026-06-01
- **결정:** 리팩토링 코드 변경의 실행자를 GitHub Copilot 대신 **Claude Sonnet 4.6 서브에이전트(`.claude/agents/refactor-executor.md`)**로 한다. 계획자(Claude Code, Opus)는 그대로 작업지시서 작성·검수만 담당.
- **사유:** 사용자(팀장) 결정. Copilot의 IDE 컨텍스트 전환·인라인 제약 없이, 작업지시서를 직접 읽고 HARD LIMIT 안에서 실행 + **빌드를 스스로 검증**(명령 실행이 환각이 아닌 실제 실행)할 수 있음. PRINCIPLES §13 작업지시서 설계는 저수준 모델 대비로 이미 견고.
- **모델 선택:** §13이 코드 변경에 "Sonnet 또는 GPT-4o 이상" 권장 → 저수준(Haiku) 대신 **Sonnet 4.6** 채택.
- **DEC-WORKFLOW-001 대비 변경점:** 실행자 주체만 교체. 계획자/실행자 분리, main push 금지(DEC-WORKFLOW-002), 1~3 묶음(004), HARD LIMIT 규율은 모두 유지.
- **DEC-WORKFLOW-007 영향:** 오케스트레이터 패턴(명령은 사용자가 실행)의 전제(소형 모델 환각)가 Sonnet 실행자에는 약화됨. refactor-executor는 빌드/검증 명령을 직접 실행 가능. 단 git push/main 변경은 여전히 금지(편집+빌드까지가 범위).
- **관련:** `.claude/agents/refactor-executor.md`, PRINCIPLES §0/§13, CLAUDE_WORKFLOW §1, DEC-WORKFLOW-001/002/007
- **재검토 조건:** 서브에이전트 실행 품질이 HARD LIMIT을 반복 위반하거나, 비용이 Copilot보다 불리할 때
- **첫 적용:** Task 26 (llm-client-abstraction)

## DEC-WORKFLOW-011 — 장수 브랜치 발산 관리: main→refactor/main 전방통합 우선
- **일시:** 2026-06-08
- **배경:** 리팩토링이 길어지는 동안 팀원이 `main`에서 개발을 이어가면, `refactor/main`과의 발산(divergence)이 누적돼 나중에 큰 충돌·통합 오류 위험. (측정: 2026-06-08 기준 refactor/main이 main보다 15커밋 앞, 팀원 main 신규 푸시 0 — 아직 발산 미시작. 분기 기준 `a3418b2`.)
- **결정:** 발산은 **백머지를 일찍 하는 것**이 아니라 **전방통합(forward-integration)을 자주 하는 것**으로 관리한다.
  1. **main→refactor/main 정기 전방통합:** 매 세션 시작 시(그리고 Phase 2 도메인 Task 착수 전) `origin/main`을 `refactor/main`에 **merge**(rebase 금지 — 공유 브랜치)해서 팀원 작업을 계속 흡수. 충돌은 refactor 쪽에서 작게·자주 해소. 머지 커밋: `merge: origin/main 전방통합 (refactor/main 최신화)`. 새 main 커밋을 흡수했으면 SESSION_LOG에 1줄.
  2. **핫파일 조율:** 충돌은 같은 파일을 동시에 고칠 때 큼. 진행 중 도메인(예: "이번 주 meeting")을 팀원에게 공유해 겹침 회피.
  3. **충돌 해소는 refactor/main 측에서:** main은 건드리지 않는다(DEC-WORKFLOW-002 유지).
- **DEC-WORKFLOW-002와의 관계:** **백머지(refactor/main→main) 동결은 유지.** main 통합은 리팩토링 완료 + 안전망(SMOKE_TESTS Task 05/06) green + 팀원 승인 시점에 원자적으로. 전방통합은 그 전까지 발산을 0 근처로 유지하는 수단.
- **보류(별도 결정 필요):** "작은 동작보존·빌드검증된 리팩토링은 일반 PR로 main에 직배"하는 완화는 DEC-WORKFLOW-002 개정이 필요 → 팀원 승인 + 본 로그 별도 등재 시에만. 현재는 채택 안 함.
- **관련:** CLAUDE_WORKFLOW §6, EXECUTION_PLAYBOOK §1, DEC-WORKFLOW-002, SESSION_LOG(2026-06-08)
- **재검토 조건:** 전방통합에도 충돌이 과대해지거나, 사용자가 main 통합 시점을 앞당기기로 결정 시.

---

## DEC-PHASE-001 — Phase 0~6 7단계 로드맵
- **일시:** 2026-05-09
- **결정:** Phase 0(안전망·문서 정리) → 1(Drift 인벤토리) → 2(구조: 2A cross-cutting → 2B/2C 도메인) → 3(품질) → 4(버그) → 5(마감) → 6(문서화).
- **사유:** Drift 분석을 코드 작업 전 완료 → 추측 방지. Cross-cutting 우선 → 스파게티 방지. 문서화는 코드 안정 후.
- **관련:** PLAN.md

## DEC-PHASE-002 — Phase 0 Task ID 체계: 00~07 사용, 08~09 의도적 gap
- **일시:** 2026-05-09
- **결정:** Phase 0은 Task 00~07 (8개). 08·09는 의도적 미사용 (Phase 0 추가 발생 시 채움).
- **사유:** Task ID 100단위로 Phase 식별 (00~09 = Phase 0). DECISIONS·DEFERRED 등은 lazy 생성, Task ID 없음.
- **관련:** PLAN.md

## DEC-PHASE-003 — AI LLM 클라이언트 통합 fast-track (Phase 게이트 예외)
- **일시:** 2026-06-01 (결정) / 2026-06-08 (완결·정식 등재)
- **결정:** PLAN상 Phase 2A(cross-cutting)에 해당하는 AI LLM 클라이언트 통합(Task 26→27→28)을, Phase 0/1(안전망·Drift 인벤토리)을 건너뛰고 **단독 패스트트랙**으로 선실행한다. 후속 죽은 코드 정리(Task 55)도 같은 트랙으로 처리.
- **사유:** 사용자(팀장) 결정. `ClaudeService`↔`OpenAiService` ~95% 중복 + 폴백 분기 3곳 복붙은 **순수 내부 리팩토링(동작 보존)**이라 Drift 분류 없이도 리스크 낮음. 소비자가 `MeetingController`·`GoogleCalendarService` 2곳뿐으로 전수조사 가능.
- **완결 결과:** Task 26(LlmClient 추상화, PR #9) → 27(AiService 폴백 중앙화, PR #11) → 28(Calendar 위임, PR #12) → 55(죽은 extract-lines 경로 제거, PR #13) 모두 머지. AI 폴백 인라인 분기 **3곳 → 0곳**. AI API 키 부재 환경이라 `compileJava`+diff로 동작 보존 검증.
- **DEC-PHASE-001 대비:** Phase 순서 게이트의 **일회적 예외**. 본궤도 Phase 0(02~07)·Phase 1(10~15)은 여전히 미착수 상태로 남아 있으며, 추후 진행 시 본 fast-track과 무관하게 정상 순서로 수행.
- **관련:** PLAN.md, SESSION_LOG(2026-06-01·2026-06-08 entry), DEC-WORKFLOW-010(실행자 Sonnet 첫 적용은 Task 26)
- **재검토 조건:** 추가 fast-track 요청 시 동일 기준(동작 보존 + 소비자 전수조사 가능 여부)으로 개별 판단.

## DEC-PHASE-004 — Task 21(webclient-error-pattern) 일괄 통일 미수행 (변동이 의도적)
- **일시:** 2026-06-20
- **결정:** Phase 2A Task 21 "WebClient 호출부 에러 핸들러 일괄 점검·통일"을 **수행하지 않는다.** WebClient 6개 호출부의 에러 처리 변동은 대부분 **의도적(맥락 적합)**이다.
- **근거(조사):**
  - Discord 2종(`DiscordNotificationService`/`DiscordNotionNotifier`): 알림은 fire-and-forget — `try-catch` + `log.warn` 후 삼킴(실패해도 본 흐름 진행). 의도적.
  - `NotionService`: Notion 내보내기는 사용자 액션 — 실패 시 `RuntimeException` 재전파(일부 best-effort 분기 혼재). 의도적.
  - `Claude`/`OpenAiService`: 예외 전파 → `AiService` 폴백이 처리(Task 26~28에서 이미 정리됨).
  - `GoogleCalendarService`: 비핵심 busy-slot 조회는 `onErrorReturn(Map.of())`로 graceful degrade. 의도적.
- **사유:** 단일 패턴으로 "통일"하면 의미 있는 차이(알림=삼킴 vs 사용자액션=전파 vs 비핵심=degrade)를 뭉개 동작이 바뀐다(PRINCIPLES §4 위반). **순수 동작보존형 refactor가 존재하지 않음.**
- **범위:** 향후 특정 WebClient 호출부의 실제 버그(예: 사용자 액션을 잘못 삼킴)가 발견되면 그때 **좁은 fix Task로 개별 처리.** blanket 통일은 안 함.
- **관련:** PLAN Phase 2A, SESSION_LOG(2026-06-20), PRINCIPLES §4
- **(사용자 결정:** "21 의도적기록 → 23 audit")

## DEC-PHASE-005 — Task 23(projectaccesschecker-usage) 감사 완료 + getProject 표준화 Phase 4 유예
- **일시:** 2026-06-20
- **감사 결과(read-only):**
  - `requireMember`/`requireLeader` 우회 **없음**(양호).
  - **유일한 실 불일치:** `AlertService`(:57)·`ScoreService`(:65,151)·`GoogleCalendarService`(:114,180,283) **6곳**이 `accessChecker.getProject()` 대신 수동 `findById().orElseThrow(IllegalArgumentException/RuntimeException)` → 없는 프로젝트가 **404 아닌 500**.
  - 수동 role 문자열 비교(`"LEADER".equals` 등, `TaskService`/`ProjectService`)는 last-leader 보호 등 **비즈니스 로직 + 매직스트링** → Phase 3 `51-magic-values-extract` 영역(23 아님).
  - 누락 멤버체크 신규 추가 = 보안 동작변경(OBSERVER/DEFERRED 인접) → **23 범위 밖.**
- **결정:** getProject() 표준화(6곳→checker)를 **Phase 4 fix로 유예**한다.
  - (1) 6곳 전부 비-NotFound 예외라 통일 시 **500→404 동작변경(fix)** — 순수 refactor형 없음.
  - (2) `GoogleCalendarService`는 외부 API(Google Calendar) 연동 서비스 → 현 PC에 키 부재로 **런타임 검증 불가**(사용자 결정: 외부 API 재설정 부담으로 차후).
- **Phase 4 후보:** `7x-fix-getproject-404-consistency`. 내부 서비스(`AlertService`/`ScoreService`)는 외부 API 무관이라 **분리해 먼저 진행 가능**, `GoogleCalendarService`는 외부 API 검증 가능한 환경에서.
- **관련:** `ProjectAccessChecker.java`, PLAN Phase 2A/4, DEC-WORKFLOW-002, SESSION_LOG(2026-06-20)

---

## DEC-DRIFT-001 — 기획서↔MVP 기능 갭은 리팩토링 범위 밖, 차후 고도화로 유예
- **일시:** 2026-06-18
- **항목:** D-INV-04, D-INV-06, D-INV-07, D-SYN-05-A/B/C/D (Phase 1 Drift 인벤토리)
- **결정:** "기획서엔 있으나 MVP에 구현 안 된" 기능 갭 — **점수체계 0~150 정규화, consent 4단계 온보딩 UI, OBSERVER 권한 강제, consent 가드** — 은 이번 리팩토링에서 **D1(코드 수정)으로 다루지 않는다.** `DEFERRED.md`(DEFER-01~03)로 유예하고, **리팩토링 완료 후 고도화 단계에서 팀 전원이 함께** 결정·구현.
- **사유:** 사용자(팀장) 판단. 이를 구현하는 것은 **동작 보존이 아니라 새 기능 개발**이라 PRINCIPLES §1(신규 기능 추가 안 함) 위반. 또 점수체계·동의·권한 정책은 제품 방향 결정이라 1인(계획자/팀장)이 단정할 게 아니라 팀 합의 사안.
- **범위 내 유지(대조):** activityLog 누락(D-INV-01)은 INV 준수 + 계측 성격이라 D1 유지(Phase 2A `22`, 최후순위). env 누락(D-SYN-04)은 설정 보완 D1. 문서 stale(D-SYN-01-A/02-A/03/05-E)은 D2(Phase 6).
- **관련:** DRIFT_INVENTORY.md(Task 13 분류), DEFERRED.md, PRINCIPLES §1·§7
- **재검토 조건:** 리팩토링 완료·main 통합 후 고도화 단계 착수 시 DEFERRED.md를 입력으로 팀 회의.

---

## DEC-REJECT-001 — Next.js 16 존재 여부 무시
- **일시:** 2026-05-09
- **거부한 권고:** 다른 AI가 "Next.js 16은 존재하지 않는다(15가 최신)"고 평가
- **거부 사유:** 사용자가 Next.js 16.2.6 실제 존재 확인. 외부 AI 지식이 outdated/환각.
- **관련:** package.json `"next": "^16.2.1"`

## DEC-REJECT-002 — `: any` PR Block 즉시 적용 거부
- **일시:** 2026-05-09
- **거부한 권고:** CI에서 `: any` 발견 시 PR fail로 처리하라
- **거부 사유:** 기존 코드에 `: any` 잔존 가능성. 즉시 fail이면 모든 PR 막힘. Phase 3 `62-typescript-strict` 완료 후 카운트 0 시점에 warn→fail 플립이 자연스러움.
- **재검토 조건:** Phase 3 완료 + `: any` 카운트 0

## DEC-REJECT-003 — 자동 테스트 풀세트 추가 거부
- **일시:** 2026-05-09
- **거부한 권고:** Phase 2 진입 전 핵심 도메인(Auth, Project)에 자동 통합 테스트 추가
- **거부 사유:** 프론트엔드 테스트 프레임워크 자체가 없음. 자동 테스트 추가는 별도 프로젝트 규모. 리팩토링 범위 외.
- **대안:** 수동 SMOKE_TESTS 7개 시나리오로 회귀 검증 (Task 05)
- **재검토 조건:** 별도 프로젝트로 테스트 인프라 구축 시

## DEC-REJECT-004 — Instruction 파일 분리 (코딩룰 vs Git룰) 거부
- **일시:** 2026-05-09
- **거부한 권고:** copilot-instructions.md를 코딩룰·Git룰로 분리 (인라인 토큰 절약)
- **거부 사유:** `.github/copilot-instructions.md`는 인라인 자동완성에 로드되지 않음 (Chat/Edits/Agent에만). 분리해도 인라인 절약 안 됨. 전제 오류.

## DEC-REJECT-005 — `[CRITICAL]` 영문 태그 전체 적용 거부 (부분 채택)
- **일시:** 2026-05-09
- **거부한 권고:** 모든 절대 규칙에 `[CRITICAL]` 영문 prefix 강화
- **부분 채택:** copilot-instructions.md의 5개 절대 규칙에만 `**[CRITICAL]**` prefix 추가
- **거부 사유 (전체 적용):** 한국어 본문에 영어 태그 과다 시 가독성 ↓. 효과 미미.

## DEC-REJECT-006 — Axios → fetch 마이그레이션 권고 거부
- **일시:** 2026-05-09
- **거부한 권고:** App Router에서 RSC fetch caching을 위해 axios 제거
- **거부 사유:** 현재 프로젝트는 클라이언트 컴포넌트 위주 (JWT localStorage·인터셉터 패턴). RSC fetch caching이 필요한 페이지 구조가 아님. 리팩토링 원칙이 아닌 아키텍처 결정 — 별도 의사결정 필요.

---

> 새 결정 추가 시: 본 문서 갱신 → SESSION_LOG에도 1줄 언급. 메모리 동기화는 선택.
