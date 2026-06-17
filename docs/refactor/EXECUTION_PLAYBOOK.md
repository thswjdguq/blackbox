# Execution Playbook — 강의 시간 실행 가이드

> 본 리팩토링은 **학교 강의 시간에 진행**한다. 본 문서는 강의 중 단계별 가이드.
> 강의 전: 모든 결정·작업지시서 확정. 강의 중: 본 가이드 따라 실행.
>
> **실행 모델 (DEC-WORKFLOW-010):** 코드 변경 실행자는 **Claude Sonnet 4.6 서브에이전트**
> (`subagent_type: refactor-executor`)다. **계획자 Claude Code(Opus)**가 한 사이클(분기→
> 위임→검수→커밋·PR→동기화)을 오케스트레이션하고, **사용자(팀장)**는 Pre-write 계획 승인과
> PR 머지를 담당한다. 구 GitHub Copilot Edits 워크플로우는 비활성(DEPRECATED).

---

## 0. 사전 준비 (강의 전 — 한 번만)

### 0-1. 환경 점검
- [ ] WSL Ubuntu 부팅, Docker Desktop 실행 확인
- [ ] git user 설정 확인 (`git config user.name`)
- [ ] Claude Code 세션 진입 가능 + `subagent_type: refactor-executor` 호출 가능 확인
  (`.claude/agents/refactor-executor.md`가 `refactor/main`에 머지돼 있어야 함)
- [ ] Claude 플랜 사용량 잔여 확인 (계획자 Opus + 실행자 Sonnet 모두 플랜 소모)

### 0-2. 브랜치 상태 확인
```bash
git checkout refactor/main
git status   # clean이어야 함
git log -1   # 최신 커밋 확인
```

### 0-3. 인프라 파일 존재 확인
다음이 모두 `refactor/main`에 머지되어 있어야 함:
- [ ] `.claude/agents/refactor-executor.md` ← 실행자 always-on 가드 (1차 방어선)
- [ ] `.github/workflows/refactor-guard.yml`
- [ ] `.github/PULL_REQUEST_TEMPLATE.md` ← Phase 0 Task 04에서 추가될 것
- [ ] `docs/refactor/PRINCIPLES.md`
- [ ] `docs/refactor/PLAN.md`
- [ ] `docs/refactor/TASK_TEMPLATE.md`
- [ ] `docs/refactor/tasks/00~06.md`

> 구 `.github/copilot-instructions.md`는 DEPRECATED stub으로만 존재(DEC-WORKFLOW-010).

### 0-4. 강의 시간 안배 추정

| Phase | 예상 소요 (보수적) | 노트 |
|---|---|---|
| Phase 0 (00~06) | 1~2 교시 | 대부분 문서·이동·기록. 코드 변경 0 |
| Phase 1 (10~15) | 1~2 교시 | grep 스캔 + 분류. 사용자 검토 시간 ↑ |
| Phase 2 (20~49) | 5~8 교시 | 가장 큰 비중. 도메인 수만큼 ↑ |
| Phase 3 (50~69) | 2~3 교시 | 품질 정리 |
| Phase 4 (70~79) | 1~2 교시 | 발견된 버그만 |
| Phase 5 (80~89) | 1 교시 | docker compose + smoke test 재실행 |
| Phase 6 (90~99) | 1~2 교시 | 문서 신규·갱신 |

총 12~20 교시 추정. 한 학기 분량.

---

## 1. 강의 시작 시 루틴 (매 교시 시작 5분)

> Session Start 프로토콜은 `docs/refactor/CLAUDE.md §3` 참조 (정책 문서 통독).

> **단축:** 아래 1~2번(동기화 + 전방통합)은 `bash scripts/sync.sh` 한 명령으로 수행 가능
> (발산 없으면 즉시 종료, 있으면 머지+쓰레기 정리까지). 충돌 시 멈추고 안내한다.
> CI(`refactor-guard`의 `divergence-check` job)도 PR마다 refactor/main이 main보다
> 뒤졌는지 경고하므로, 놓쳐도 PR에서 다시 잡힌다.

```bash
# 1. 환경 활성화
cd /home/user/project/team-blackbox/blackbox
git fetch origin --prune
git checkout refactor/main
git merge --ff-only origin/refactor/main   # 다른 머신에서 작업했다면 최신화

# 2. 전방통합 — origin/main 흡수 (DEC-WORKFLOW-011, 발산 누적 방지)
#    팀원이 main에 새로 푸시했는지 확인하고, 있으면 refactor/main에 merge.
#    ↓ 1~2번을 한 번에: bash scripts/sync.sh
git log --oneline origin/refactor/main..origin/main   # 비어있으면 흡수할 것 없음
git merge origin/main -m "merge: origin/main 전방통합 (refactor/main 최신화)"
#  - 충돌 시: refactor/main 측에서 해소(작게·자주). main은 건드리지 않음.
#  - 새 main 커밋을 흡수했으면 SESSION_LOG에 1줄. rebase 금지(공유 브랜치).
git push origin refactor/main   # 흡수 머지가 있었으면

# 3. 어디까지 했는지 확인
cat docs/refactor/PLAN.md | grep -A2 "## Phase"   # Phase별 [x] 체크박스
gh pr list --base refactor/main --state all --limit 10   # 최근 PR
tail -40 docs/refactor/SESSION_LOG.md   # 직전 세션 핸드오프

# 4. 다음 Task 결정
# PLAN.md에서 [ ] 미완료 Task 중 선행 Task가 머지된 것 선택
# Phase 2 도메인 Task 착수 전에도 전방통합 1회 (핫파일 충돌 최소화)
```

---

## 2. Task 1개 실행 사이클 (반복 단위)

> **계획자(Claude Code)가 사이클을 운전한다.** 코드 작성만 실행자 서브에이전트에 위임.
> 계획자는 코드를 직접 수정하지 않는다(§4 Claude 예외 실행 제외).

### 2-1. 사전 확인 (계획자)
- [ ] 작업지시서 `docs/refactor/tasks/NN-xxx.md` 한 번 통독
- [ ] §4-1 의존 Task가 모두 `refactor/main`에 머지되었는지
- [ ] §7 Pre-write 프로토콜 적용 여부 확인 (Skip / Required)
- [ ] §2 HARD LIMIT·§3 Out of Scope 숙지 (위임 시 실행자에게 그대로 전달)
- [ ] 라인 참조가 있으면 현재 코드와 일치하는지 점검 (밀렸으면 구조 기준으로 재탐색)

### 2-2. 브랜치 분기 (계획자)
```bash
git checkout refactor/main
git pull origin refactor/main
git checkout -b refactor/NN-<short-name>   # 작업지시서 §9에 명시된 이름
```

### 2-3. 실행 (패턴별)

**실행자 위임 패턴** (대부분의 코드 변경 Task):
1. 계획자가 `subagent_type: refactor-executor`로 위임. 프롬프트에 포함:
   - 작업지시서 경로 + "먼저 통독" 지시
   - §2 HARD LIMIT (변경 가능 파일 목록) + §3 Out of Scope
   - 보존해야 할 불변(동작·예외 메시지·시그니처 등)
   - **"커밋/푸시/PR 금지 — 편집 + `./gradlew compileJava` 검증까지가 범위"**
2. **§7 Pre-write = Required인 경우:** 실행자는 **코드 쓰기 전 변경 계획 3~5불릿**만 제시.
   계획자가 사용자에게 전달 → **사용자 승인** → 계획자가 실행자에 구현 지시(같은 컨텍스트
   이어가기 또는 승인된 계획을 명시해 재호출).
3. **§7 Pre-write = Skip인 경우:** 실행자가 바로 구현 + 검증.
4. 실행자가 `./gradlew compileJava`(또는 해당 빌드/타입체크)를 **직접 실행**하고 결과 보고.
   (Sonnet 실행자는 명령을 실제 실행한다 — 환각 아님. DEC-WORKFLOW-010.)

**Claude 직접 패턴** (정적 문서 생성, §4 4조건 만족 — 예: Task 05, 본 playbook):
1. 계획자(Claude Code)가 직접 Write 수행 (코드 아님, 콘텐츠가 작업지시서에 완전 정의).
2. 사용자 통독 검토.

**(레거시) 오케스트레이터 패턴** — 저수준 모델 실행자이거나 명령 출력 신뢰가 어려울 때만:
1. 실행자는 명령 안내만, 사용자가 실행, 받은 출력을 실행자가 문서로 정리.
2. 현 Sonnet 실행자에는 보통 불필요(직접 실행 가능). PRINCIPLES §13 참조.

### 2-4. 검수 (계획자 — diff 직접 검토)
> Copilot UI의 파일별 approve가 아니라, **계획자가 PR diff를 직접 읽어** 검수한다.
- [ ] 작업지시서 §8 acceptance 모든 항목 체크
- [ ] HARD LIMIT 외 변경 0건 (`git diff --stat` + 실제 diff 통독)
- [ ] 보존 불변 확인 (동작·예외 메시지·시그니처가 byte 단위로 유지됐는지)
- [ ] 빌드 검증 결과가 SUCCESSFUL인지 (실행자 보고를 계획자가 재확인 가능)
- [ ] `// [CONFIRM:?]` 잔존이 있으면 PR description에 명시
- [ ] 동작 변경이 의도되지 않았다면 SMOKE_TESTS 해당 시나리오만이라도 수동 통과 (Phase 2부터)
- 위반·미흡 발견 시 → 실행자에 수정 재위임 (계획자가 직접 고치지 않음)

### 2-5. 커밋·푸시·PR (계획자)
```bash
git add <변경된 파일들>   # git add -A 금지 (의도치 않은 파일 방지)
git commit -m "<type>: <한 줄 요약>

<왜 변경했는가, 필요 시>

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
"
git push -u origin refactor/NN-<short-name>

# PR 생성 (base는 항상 refactor/main — 절대 main 아님)
gh pr create --base refactor/main --title "[refactor-NN] <제목>" --body "<...>"
```
- 한 커밋에 한 종류만 (PRINCIPLES §5). 작업지시서(docs)와 코드(refactor)는 커밋 분리.
- PR 생성 후 **base가 `refactor/main`인지 반드시 재확인** (`gh pr view <N> --json baseRefName`).

### 2-6. CI 통과 확인
```bash
gh pr checks <PR번호>   # critical fail 없는지
```
- ✅ 모두 통과 → 다음 단계
- ❌ critical fail → §3-3 fallback
- ⚠️ warn만 → 검토 후 진행 가능

### 2-7. 머지 (사용자)
```bash
gh pr merge <PR번호> --squash --delete-branch
```
> 머지는 사용자(게이트키퍼) 액션. 계획자는 PR을 준비·검수까지.

### 2-8. 동기화 + 다음 Task로 (계획자)
```bash
git checkout refactor/main
git pull --ff-only origin refactor/main
git branch -D refactor/NN-<short-name>   # 머지된 로컬 브랜치 정리
```
- [ ] 머지 후 PLAN.md 해당 Phase 완료 조건 [x] 체크 (Phase 종료 시 일괄 docs PR로)

---

## 3. 자주 발생할 상황 — Fallback

### 3-1. 실행자가 작업지시서를 약식 처리
- **증상**: 출력이 짧음, HARD LIMIT 일부 무시, 시나리오·항목 누락
- **대응**:
  1. 계획자가 "위 작업지시서의 §X 누락. HARD LIMIT 안에서 모든 섹션 충실히 재실행" 재위임
  2. 그래도 실패 → Task 분할 검토 (강의 후 작업지시서 수정)

### 3-2. 실행자가 HARD LIMIT 밖 파일 건드림
- **증상**: 작업지시서에 없는 파일이 변경됨 (계획자 diff 검수에서 발견)
- **대응**:
  1. **즉시 `git checkout -- <해당 파일>`** 으로 되돌림 (또는 실행자에 해당 파일 원복 지시)
  2. 실행자에게 "그 파일은 HARD LIMIT 밖. 작업지시서 §3 Out of Scope 재확인 후 재실행"
  3. CI PR 크기 가드도 잡아주지만 계획자 사전 검수가 1차 차단

### 3-3. CI가 critical fail
- **file_vault mutation 검출**: INV-02 위반. 코드 즉시 수정. 같은 PR에서 처리
- **외부 managed 서비스**: INV-05 위반. 추가된 호스트명 제거
- **기존 V*.sql 수정**: 되돌리기 + 새 V18+ 파일로 재작업
- **PR base가 main**: PR 닫고 base를 `refactor/main`으로 다시 만들기

### 3-4. 빌드/검증 결과 불일치 의심
- **증상**: 실행자 보고가 의심스러움 (드묾 — Sonnet은 실제 실행)
- **검증**: 계획자가 같은 명령(`./gradlew compileJava`)을 직접 재실행 → 결과 비교
- **대응**: 다른 결과면 실행자 보고 무시, 계획자 재실행 결과를 신뢰

### 3-5. 빌드 실패 (Phase 0 Task 02 또는 후속)
- **수정 시도 금지** — 작업지시서가 명시한 범위 안에서만
- 발견된 실패는 baseline.md "Known Issues"에 기록
- Phase 4 후보 Task로 별도 발의 → 다음 강의 시작 전 작업지시서 작성

### 3-6. Pre-write가 Required인데 실행자가 무시하고 코드부터 출력
- **증상**: Required인데 계획 없이 코드부터 나옴
- **대응**: 위임 프롬프트에 "이번은 PRE-WRITE 단계 — 코드/파일 작성 금지, 변경 계획 3~5불릿만"을
  명시해 재위임. 코드가 이미 나왔으면 `git checkout -- .`로 원복 후 계획부터 다시.

### 3-7. Claude 플랜 사용량 부족
- **증상**: 계획자(Opus) 또는 실행자(Sonnet) 호출 한도 근접
- **대응**:
  - 강의 중 새 Task 작성·계획 수정 보류 → 강의 후 진행
  - 진행 중인 Task는 작업지시서가 명확하면 실행자(Sonnet)만으로 계속
  - 다음 강의 전 계획 보강

### 3-8. 시간 부족 (강의 종료 임박)
- 진행 중 Task가 있으면 **현재 commit + push** (PR 생성 안 해도 됨)
- 다음 강의 시작 시 같은 브랜치에서 이어서
- 절대 미완성 PR을 머지하지 말 것
- SESSION_LOG.md에 핸드오프 1 entry 추가 (다음 진입점 명시)

---

## 4. Phase별 종료 체크리스트

각 Phase 마지막 Task 머지 후 다음 모두 확인 → 다음 Phase 진입.

### Phase 0 종료
- [ ] PLAN.md Phase 0 완료 조건 7개 모두 [x]
- [ ] `docs/_archive/` 아래 2 legacy 파일 + README 존재
- [ ] `docs/refactor/baseline.md` 존재 (Task 02 결과)
- [ ] `docs/refactor/TEST_COVERAGE.md` 존재 (Task 03 결과)
- [ ] `.github/PULL_REQUEST_TEMPLATE.md` 존재
- [ ] `docs/refactor/SMOKE_TESTS.md` 존재 + 실행 이력 표 1행
- [ ] `md/handover_log.md`에 `c:\blackbox` 잔존 0건

### Phase 1 종료
- [ ] DRIFT_INVENTORY.md 모든 행에 사용자 결정 기재
- [ ] D1 항목들이 Phase 2~4 Task로 매핑됨
- [ ] D2 항목들이 Phase 6에 묶임
- [ ] D3 항목들이 DECISIONS.md에 기록됨

### Phase 2 종료
- [ ] 2A(20~29) Cross-cutting 모두 머지
- [ ] 도메인별 BE/FE Task 완료
- [ ] DB 레거시 deprecate 컬럼 코드 사용 0
- [ ] CI 모든 critical 통과
- [ ] SMOKE_TESTS 7개 시나리오 수동 재실행 + 실행 이력 표 추가

### Phase 3 종료
- [ ] CI warn 항목 의미있게 감소 (`: any`, debug print 카운트)
- [ ] GLOSSARY.md 정리

### Phase 4 종료
- [ ] 모든 발견 버그가 처리되었거나 DEFERRED.md 기재
- [ ] fix 커밋이 refactor 커밋과 분리되어 머지

### Phase 5 종료
- [ ] Docker 풀 스택 기동 성공
- [ ] SMOKE_TESTS 7개 모두 통과 (또는 통과 못 한 항목 DECISIONS.md)
- [ ] gc.md 검사 모두 통과

### Phase 6 종료
- [ ] 루트 README.md, CLAUDE.md 존재
- [ ] md/ 정본이 실제 코드와 일치
- [ ] DRIFT_INVENTORY D2 항목 모두 반영

---

## 5. 사용량·토큰 관리

| 주체 | 강의 중 사용 패턴 | 한도 의식 |
|---|---|---|
| 계획자 Claude Code (Opus) | 사이클 운전(분기·위임·검수·PR)·계획·보강 | 플랜 한도. 검수·위임에 집중 |
| 실행자 refactor-executor (Sonnet) | Task당 코드 변경 + 빌드 검증 | 플랜 한도. 코드 작성의 주력 |
| Claude 직접 (예외 실행) | Task 05·문서 생성 등 §4 4조건 만족 시만 | 1회 Write로 끝 |

**강의 중 호출 줄이는 법:**
- 작업지시서가 명확하면 실행자에 1회 위임으로 충분 (재위임 횟수 = 작업지시서 품질의 함수)
- 새 결정이 필요한 경우만 계획자가 사용자에게 질문 (강의 중 즉석 결정 회피 — DEC-WORKFLOW-008)
- 위임 시 컨텍스트(작업지시서 경로·HARD LIMIT·보존 불변) 명시해서 한 번에 끝
- 강의 후 다음 강의를 위한 보강은 별도 시간에

---

## 6. 강의 종료 시 루틴 (매 교시 종료 5분)

```bash
# 1. 진행 상태 push
git status
git push origin <current-branch>   # WIP commit이라도 OK

# 2. 다음 강의 진입점 기록 (Session End 프로토콜 — CLAUDE.md §4)
#    docs/refactor/SESSION_LOG.md 에 1 entry 추가 (한 일·결정·다음 진입점·미해결)

# 3. 큰 결정 있었으면 docs/refactor/DECISIONS.md 갱신
```

---

## 7. 참고 문서

- `docs/refactor/CLAUDE.md` — Claude Code 세션 진입점 + Session Start/End 프로토콜
- `docs/refactor/CLAUDE_WORKFLOW.md` — 역할 분담·운영 정책
- `docs/refactor/PRINCIPLES.md` — 모든 결정의 근거
- `docs/refactor/PLAN.md` — Phase·Task 로드맵
- `docs/refactor/TASK_TEMPLATE.md` — Task 작성 양식
- `docs/refactor/DECISIONS.md` — 누적 의사결정 (DEC-WORKFLOW-010 실행자 교체 등)
- `md/gc.md` — INV·SYNC·CODE 검사 (헌법)
- `.claude/agents/refactor-executor.md` — 실행자 always-on 시스템 프롬프트 (1차 방어선)
