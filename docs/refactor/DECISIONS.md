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

## DEC-WORKFLOW-005 — 3겹 방어선 (Copilot 가드)
- **일시:** 2026-05-09
- **결정:** Copilot 규칙 전달은 (1) `.github/copilot-instructions.md` always-on + (2) 작업지시서 인라인 발췌 + (3) CI lint 3겹.
- **사유:** Copilot이 PRINCIPLES.md를 자동으로 안 읽음. 운영 가능한 형태로 분산 필요.
- **관련:** PRINCIPLES §12, CLAUDE_WORKFLOW (외부 PRINCIPLES 위임)

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
