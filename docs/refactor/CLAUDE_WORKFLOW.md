# Claude Code 운영 정책

> 본 문서는 Claude Code 세션의 의사결정·작업 패턴을 정의한다.
> per-PC 메모리에만 있던 메타 결정을 repo로 이전한 **단일 source**.
> 변경 시: 본 문서 갱신 → (선택) 메모리 동기화.
> 충돌 시 본 문서가 우선이다.

---

## 1. 역할 분담

### Claude Code (계획자)
- 원칙·계획·작업지시서 작성
- 의사결정 1차안 제시 → 사용자 검토
- 검수 (REVIEW_CHECKLIST 활용)
- 새 메타 결정 발생 시 본 문서·DECISIONS·SESSION_LOG 갱신
- **코드를 직접 수정하지 않음** (예외 §4 참조)

### GitHub Copilot (실행자)
- 작업지시서 받아 실제 코드/문서 변경
- 작업지시서의 HARD LIMIT·Out of Scope 엄수
- 베이스 모델은 mini/haiku급일 수 있음 — 작업지시서가 견고해야 함 (PRINCIPLES §13)

### 사용자 (팀장)
- 모든 결정 최종 확정
- Copilot 실행을 직접 관여 (Edits 모드 approve 등)
- 명령 실행 패턴(§5)에서 실제 명령 수행
- 다른 AI 평가·외부 자료를 Claude Code에 제공

---

## 2. 작업 묶음 정책

- **Task 1~3개 묶음 단위**로 계획·검토·실행
- 1개씩: 검토 횟수 과다, 컨텍스트 단절
- Phase 전체: 변경 폭 과다, 스파게티 위험
- 1~3개: 검토 부담과 일관성의 균형점

---

## 3. Drift 처리 워크플로우 (Phase 1 산출물)

`docs/refactor/DRIFT_INVENTORY.md` 항목 처리:
1. Claude가 등급(Spec / State / Convention) + 결정 1차안(D1~D4) 제시
2. 사용자 일괄 검토 → 확정
3. D4(사용자 결정 필요) 항목은 별도 질문 묶음으로 일괄

결정 타입:
- **D1** 코드 수정 (Spec 위반) → Phase 2~4 Task로 매핑
- **D2** 문서 수정 (State stale) → Phase 6 묶음
- **D3** 의도적 차이로 기록 → DECISIONS.md
- **D4** 사용자 확인 필요 → 별도 질문

---

## 4. Claude 예외 실행 (Copilot 비사용)

다음 **4조건 모두 만족** 시 Claude가 직접 Write:
1. 정적 문서 생성 (코드 변경 아님)
2. 콘텐츠가 작업지시서 본문에 완전히 정의
3. Copilot의 lost-in-the-middle / 약식화 위험이 큼
4. 사용자 명시 승인

작업지시서에 "Copilot 비사용 — Claude가 직접 작성" 명시.
**남용 금지.** 코드 작업에는 절대 적용 안 함.
사례: Task 05 (SMOKE_TESTS.md, 250줄+ 복사 작업).

---

## 5. 명령 실행 + 출력 캡처 패턴 (오케스트레이터)

Copilot이 명령을 직접 실행하면 **환각 위험 본질적**.
대신:
- Copilot은 "다음 명령 실행 후 결과 부탁드려요" 안내만
- 실제 실행과 출력 복사는 사용자
- Copilot은 받은 출력을 문서로 정리

적용 Task: 02 (build baseline), 03 (test coverage), 06 (smoke run), 10/11/12 (drift scan), 13 일부.
PRINCIPLES §13 참조.

---

## 6. 형상관리 정책

- 작업은 **`refactor/main` 브랜치** 진행
- **`main`에 절대 push/PR/merge 금지** (팀원 최종 승인 전까지)
- Task별 `refactor/<task-id>-<name>` 브랜치 → PR base는 항상 `refactor/main`
- 한 커밋에 한 종류 변경만 (rename / move / refactor / fix / feat / docs / chore 분리)
- squash merge로 머지

---

## 7. 강의 시간 실행 컨텍스트

본 리팩토링 코드 변경 작업은 **학교 강의 시간에 진행** (캡스톤 수업).
- 모든 결정·작업지시서는 강의 전 확정
- 강의 중 즉석 결정 회피 (시간 압박, 컨텍스트 전환 비용)
- 강의 중 새 결정 필요해지면 DEFERRED.md 또는 다음 강의로 미룸
- 가이드: `docs/refactor/EXECUTION_PLAYBOOK.md`

---

## 8. 메모리 ↔ Repo 정책 (cross-PC 핵심)

| 출처 | 위치 | PC 간 공유 | 권위 |
|---|---|---|---|
| Repo | git | ✅ | **canonical (정본)** |
| Per-machine 메모리 | `~/.claude/projects/.../memory/` | ❌ | per-PC 보조 캐시 |

**규칙:**
1. **새 메타 결정·정책 변경 발생 시: 먼저 repo 갱신** (본 문서 또는 DECISIONS)
2. 메모리는 선택적 동기화 (이 PC에서 빠르게 회상하고 싶다면)
3. **메모리에만 있고 repo에 없는 항목은 cross-PC에서 잃음** → 회피
4. 다른 PC에서 Claude Code 진입 시 §9 프로토콜로 메모리 없이도 동작 가능

---

## 9. Session Start / End 프로토콜

### Session Start (어떤 PC에서든)
1. `docs/refactor/CLAUDE.md` 읽기 (entry point)
2. 본 문서 (`CLAUDE_WORKFLOW.md`)
3. `docs/refactor/SESSION_LOG.md` **마지막 5 entry**
4. `docs/refactor/PLAN.md` 진행 상태 (Phase별 [x] 체크박스)
5. (필요 시) `docs/refactor/DECISIONS.md` 최근 항목

→ 위 5개를 읽으면 어느 PC의 Claude Code 세션이든 동일 컨텍스트 진입.

신규 PC 진입 검증: 사용자가 "현재 상황 파악해줘" 요청 → Claude Code가 1~2문단 요약 → 사용자 일치 확인.

### Session End
1. `SESSION_LOG.md`에 1 entry 추가 (~10줄)
2. 큰 결정 있었으면 `DECISIONS.md` 갱신
3. (선택) 메모리 동기화

---

## 10. 외부 AI 평가 처리 정책

다른 AI(GPT, 다른 Claude 인스턴스 등)가 평가·제안을 제공하면:
1. **명시적 분류**: 채택 / 부분 채택 / 거부 / 보류
2. **거부 시 사유 명기** → `DECISIONS.md`의 DEC-REJECT-NN으로 기록
3. **부분 채택 시 어느 부분만 채택했는지 명기**
4. 사용자 승인 후 적용

거부 항목이 다음 세션·다른 PC에서 다시 같은 권고로 들어와도 즉시 같은 결정 적용 (DECISIONS 참조).

---

## 11. 작업지시서 생성 룰 (PRINCIPLES §13 요약)

저수준 모델(mini/haiku) 대응:
- 단일 Task ≤ 3 파일 변경 (예외 시 §C에 사유 명기)
- 단일 작업지시서 본문 ≤ 200줄 (코드 패턴 인라인 발췌 제외)
- HARD LIMIT는 §2에 위치 (모델 주의력 분배)
- 핵심 규칙은 §3 + §6 + §8에 3회 반복
- 모호 형용사 금지 ("적절히", "신중히")
- 명령은 복사-실행 가능 (변수 치환 없음)
- 사용자 환경 단일 명시 (WSL Ubuntu bash)

상세: `PRINCIPLES.md` §13.

---

## 12. 참조

- `docs/refactor/CLAUDE.md` — Claude Code 세션 진입점
- `docs/refactor/PRINCIPLES.md` — 리팩토링 헌법
- `docs/refactor/PLAN.md` — Phase·Task 로드맵
- `docs/refactor/SESSION_LOG.md` — 세션별 핸드오프
- `docs/refactor/DECISIONS.md` — 누적 의사결정
- `docs/refactor/EXECUTION_PLAYBOOK.md` — 강의 시 가이드
- `md/claude.md`, `md/handover_log.md`, `md/gc.md` — 프로젝트 컨텍스트
