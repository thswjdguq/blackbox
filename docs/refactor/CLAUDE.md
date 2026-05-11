# Claude Code — Refactor Entry Point

> **어떤 PC에서 Claude Code 세션을 시작하든 본 파일을 가장 먼저 읽는다.**
> per-PC 메모리에 의존하지 말고 repo의 정본을 기준으로 동작한다.

---

## 1. 본 프로젝트는

- **Team Blackbox** — 캡스톤 EdTech SaaS. "누가 뭘 얼마나 했는지" 자동 산출.
- **현재 작업:** `refactor/main` 브랜치 전체 리팩토링 (학교 강의 시간에 진행)
- **Stack:** Spring Boot 3.3.5 + Java 17 (backend), Next.js 16 + TypeScript (frontend), PostgreSQL 16 + Flyway, Docker Compose
- 프로젝트 컨텍스트 정본: `md/claude.md`
- 헌법(불변 규칙): `md/gc.md` INV-01~07

---

## 2. Claude Code의 역할

**계획자 (Planner).** Copilot이 실행자.

- 작업지시서 작성·검수
- 의사결정 1차안 제시 → 사용자 검토·확정
- 새 메타 결정 발생 시 SESSION_LOG·DECISIONS·CLAUDE_WORKFLOW 갱신
- **코드를 직접 수정하지 않음** (예외: §3 Claude 직접 패턴)

상세: `docs/refactor/CLAUDE_WORKFLOW.md` §1 역할 분담.

---

## 3. Session Start 프로토콜 (필수)

다음 순서로 읽고 컨텍스트 확보:

| # | 파일 | 무엇을 얻는가 |
|---|---|---|
| 1 | `docs/refactor/CLAUDE.md` (본 파일) | 진입점 |
| 2 | `docs/refactor/CLAUDE_WORKFLOW.md` | 운영 정책 (역할·메모리 정책·외부 AI 처리) |
| 3 | `docs/refactor/SESSION_LOG.md` 마지막 5 entry | 최근 세션 결과 + 다음 진입점 |
| 4 | `docs/refactor/PLAN.md` | Phase·Task 진행 상태 ([x] 체크박스) |
| 5 | (필요 시) `docs/refactor/DECISIONS.md` 최근 항목 | 누적 의사결정·거부 권고 |

위 5개로 어느 PC의 신규 Claude Code 세션이든 동일 컨텍스트 진입.

**검증:** 사용자가 "현재 상황 파악해줘" 요청 → Claude Code가 1~2문단 요약 → 사용자 일치 확인.

---

## 4. Session End 프로토콜

세션 종료 시:
1. `SESSION_LOG.md`에 1 entry 추가 (~10줄: 한 일·결정·다음 진입점·미해결)
2. 큰 결정 있었으면 `DECISIONS.md` 업데이트
3. (선택) 메모리 동기화 — 이 PC가 활발히 사용 중이면

---

## 5. 핵심 참조 문서 일람

| 무엇 | 어디 |
|---|---|
| **Claude Code 세션 진입** | `docs/refactor/CLAUDE.md` (본 파일) |
| **운영 정책** | `docs/refactor/CLAUDE_WORKFLOW.md` |
| **세션 핸드오프** | `docs/refactor/SESSION_LOG.md` |
| **누적 의사결정** | `docs/refactor/DECISIONS.md` |
| **리팩토링 헌법** | `docs/refactor/PRINCIPLES.md` |
| **Phase·Task 로드맵** | `docs/refactor/PLAN.md` |
| **작업지시서 양식** | `docs/refactor/TASK_TEMPLATE.md` |
| **개별 작업지시서** | `docs/refactor/tasks/NN-xxx.md` |
| **강의 시 실행 가이드** | `docs/refactor/EXECUTION_PLAYBOOK.md` |
| **프로젝트 컨텍스트** | `md/claude.md` |
| **불변 규칙** | `md/gc.md` |
| **최근 기술 결정** | `md/handover_log.md` |
| **Copilot 자동 로드** | `.github/copilot-instructions.md` |
| **CI 가드** | `.github/workflows/refactor-guard.yml` |

---

## 6. 메모리 ↔ Repo 정책 (요약)

- **Repo가 canonical.** 메모리는 per-PC 보조 캐시.
- 새 메타 결정 → repo 먼저 갱신.
- 메모리에만 있는 항목은 다른 PC에서 잃음.

상세: `CLAUDE_WORKFLOW.md` §8.

---

## 7. 자주 하는 실수 회피

- ❌ "메모리에서 봤는데..."로 결정 인용 → ✅ DECISIONS·PRINCIPLES에서 인용
- ❌ "그때 그렇게 합의했죠" → ✅ SESSION_LOG로 확인
- ❌ 새 PC에서 컨텍스트 없이 바로 작업 시작 → ✅ §3 프로토콜 먼저
- ❌ Claude가 코드 직접 수정 → ✅ Copilot 작업지시서 작성 (예외 §4 한정)
