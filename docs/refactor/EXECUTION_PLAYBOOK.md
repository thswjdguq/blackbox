# Execution Playbook — 강의 시간 실행 가이드

> 본 리팩토링은 **학교 강의 시간에 진행**한다. 본 문서는 강의 중 단계별 가이드.
> 강의 전: 모든 결정·작업지시서 확정. 강의 중: 본 가이드 따라 실행.

---

## 0. 사전 준비 (강의 전 — 한 번만)

### 0-1. 환경 점검
- [ ] WSL Ubuntu 부팅, Docker Desktop 실행 확인
- [ ] git user 설정 확인 (`git config user.name`)
- [ ] Copilot 로그인 + Edits 모드 사용 가능 확인
- [ ] 모델 선택 가능하면 코드 작업용으로 **GPT-4o** 또는 **Claude Sonnet** 선택
- [ ] Claude Code 사용량 잔여 확인 (Pro 플랜 한도)

### 0-2. 브랜치 상태 확인
```bash
git checkout refactor/main
git status   # clean이어야 함
git log -1   # 최신 커밋 확인
```

### 0-3. 인프라 파일 존재 확인
다음이 모두 `refactor/main`에 머지되어 있어야 함:
- [ ] `.github/copilot-instructions.md`
- [ ] `.github/workflows/refactor-guard.yml`
- [ ] `.github/PULL_REQUEST_TEMPLATE.md` ← Phase 0 Task 04에서 추가될 것
- [ ] `docs/refactor/PRINCIPLES.md`
- [ ] `docs/refactor/PLAN.md`
- [ ] `docs/refactor/TASK_TEMPLATE.md`
- [ ] `docs/refactor/tasks/00~06.md`

(현재 시점에는 unstaged 상태. 강의 시작 전 인프라 commit + push 필요)

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

```bash
# 1. 환경 활성화
cd /home/user/project/team-balckbox/blackbox
git checkout refactor/main
git pull origin refactor/main   # 다른 머신에서 작업했다면

# 2. 어디까지 했는지 확인
cat docs/refactor/PLAN.md | grep -A2 "## Phase"   # Phase별 [x] 체크박스
gh pr list --base refactor/main --state all --limit 10   # 최근 PR

# 3. 다음 Task 결정
# PLAN.md에서 [ ] 미완료 Task 중 선행 Task가 머지된 것 선택
```

---

## 2. Task 1개 실행 사이클 (반복 단위)

### 2-1. 사전 확인
- [ ] 작업지시서 `docs/refactor/tasks/NN-xxx.md` 한 번 통독
- [ ] §4-1 의존 Task가 모두 `refactor/main`에 머지되었는지
- [ ] §7 Pre-write 프로토콜 적용 여부 확인 (Skip / Required)
- [ ] §6 절차의 실행 패턴 인지 (일반 Copilot / 오케스트레이터 / Claude 직접)

### 2-2. 브랜치 분기
```bash
git checkout refactor/main
git pull origin refactor/main
git checkout -b refactor/NN-<short-name>   # 작업지시서 §9에 명시된 이름
```

### 2-3. 실행 (패턴별)

**일반 Copilot 패턴** (Task 00, 01, 04):
1. Copilot Edits 열기
2. 작업지시서 본문 전체를 Edits prompt에 붙여넣기
3. 출력 받기 → 파일별 approve
4. HARD LIMIT 외 변경 있는지 diff 확인
5. 수동 spot check

**오케스트레이터 패턴** (Task 02, 03, 06):
1. Copilot Chat 열기
2. 작업지시서 본문 전체 + "이 작업을 §13 오케스트레이터 패턴으로 진행. 명령은 내가 실행할 테니 너는 안내만." 붙여넣기
3. Copilot이 명령 안내 → 사용자가 실행 → 결과 붙여넣기 (반복)
4. Copilot이 최종 문서 작성 → 검토

**Claude 직접 패턴** (Task 05):
1. Claude(Code) 세션에서 "Task 05 실행해줘" 또는 작업지시서 경로 제공
2. Claude가 Write 1회 수행
3. 사용자 통독 검토

### 2-4. 검수
- [ ] 작업지시서 §8 acceptance 모든 항목 체크
- [ ] HARD LIMIT 외 변경 0건 (`git diff --stat`로 변경 파일·라인 수 확인)
- [ ] `// [CONFIRM:?]` 잔존이 있으면 PR description에 명시
- [ ] 동작 변경이 의도되지 않았다면 SMOKE_TESTS 해당 시나리오만이라도 수동 통과 (Phase 2부터 적용)

### 2-5. 커밋·푸시·PR
```bash
git add <변경된 파일들>   # git add -A 금지 (의도치 않은 파일 방지)
git commit -m "<type>: <한 줄 요약>

<왜 변경했는가, 필요 시>
"
git push -u origin refactor/NN-<short-name>

# PR 생성 (PR 템플릿 자동 채워짐)
gh pr create --base refactor/main --title "[refactor-NN] <제목>" --body-file <PR body 임시 파일>
# 또는 GitHub UI에서 작성
```

### 2-6. CI 통과 확인
```bash
gh pr checks <PR번호>   # critical fail 없는지
```
- ✅ 모두 통과 → 다음 단계
- ❌ critical fail → §3-3 fallback
- ⚠️ warn만 → 검토 후 진행 가능

### 2-7. 머지
```bash
gh pr merge <PR번호> --squash --delete-branch
```
머지 후:
- [ ] PLAN.md 해당 Phase의 완료 조건 [x] 체크 (별도 PR 또는 직접 push 가능 — 본 프로젝트는 PRINCIPLES §5 직접 commit 금지지만 이 체크는 예외 허용 검토. 또는 Phase 종료 시 일괄 PR)

### 2-8. 다음 Task로

---

## 3. 자주 발생할 상황 — Fallback

### 3-1. Copilot이 작업지시서를 약식 처리
- **증상**: 출력이 짧음, HARD LIMIT 일부 무시, 시나리오·항목 누락
- **대응**:
  1. "위 작업지시서의 §X 누락. 다시 시도. 모든 섹션 충실히." 재요청
  2. 그래도 실패 → Task 분할 검토 (강의 후 작업지시서 수정)
  3. Edits 모드면 누락된 파일을 사용자가 직접 알려주고 재요청

### 3-2. Copilot이 HARD LIMIT 밖 파일 건드림
- **증상**: 작업지시서에 없는 파일이 변경됨
- **대응**:
  1. **즉시 `git checkout -- <해당 파일>`** 으로 되돌림
  2. Copilot에게 "그 파일은 HARD LIMIT 밖. 수정 취소. 작업지시서 §3 Out of Scope 재확인"
  3. CI도 PR 크기 가드로 잡아주지만 사전 차단이 안전

### 3-3. CI가 critical fail
- **file_vault mutation 검출**: INV-02 위반. 코드 즉시 수정. 같은 PR에서 처리
- **외부 managed 서비스**: INV-05 위반. 추가된 호스트명 제거
- **기존 V*.sql 수정**: 되돌리기 + 새 V18+ 파일로 재작업
- **PR base가 main**: PR 닫고 base를 `refactor/main`으로 다시 만들기

### 3-4. 환각 의심 (오케스트레이터 패턴에서)
- **증상**: Copilot이 명령 결과를 만들어낸 것 같음
- **검증**: 사용자가 같은 명령 재실행 → 결과 비교
- **대응**: 다른 출력이면 baseline.md/TEST_COVERAGE.md에서 해당 항목을 사용자가 직접 수정

### 3-5. 빌드 실패 (Phase 0 Task 02 또는 후속)
- **수정 시도 금지** — 작업지시서가 명시한 범위 안에서만
- 발견된 실패는 baseline.md "Known Issues"에 기록
- Phase 4 후보 Task로 별도 발의 → 다음 강의 시작 전 작업지시서 작성

### 3-6. Pre-write 프로토콜이 필요한데 Copilot이 무시하고 코드 출력
- **증상**: Required인데 계획 없이 코드부터 나옴
- **대응**: "코드 출력 정지. 먼저 변경 계획을 3~5개 불릿으로 제시. 승인 후 구현." 재요청

### 3-7. Claude Code 사용량 부족
- **증상**: Claude 호출 한도 근접
- **대응**:
  - 강의 중 새 Task 작성·계획 수정 보류 → 강의 후 진행
  - 진행 중인 Task는 작업지시서 따라 Copilot으로 계속
  - 다음 강의 전 Claude로 계획 보강

### 3-8. 시간 부족 (강의 종료 임박)
- 진행 중 Task가 있으면 **현재 commit + push** (PR 생성 안 해도 됨)
- 다음 강의 시작 시 같은 브랜치에서 이어서
- 절대 미완성 PR을 머지하지 말 것

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

| 도구 | 강의 중 사용 패턴 | 한도 의식 |
|---|---|---|
| Copilot Chat/Edits | Task당 평균 5~10 호출 | 무제한 (Pro 가정) |
| Claude Code | Phase 진입 시 계획·보강 | 일별 한도. Task 실행 중에는 최소화 |
| Claude (예외 실행) | Task 05 등 4조건 만족 시만 | 1회 Write로 끝 |

**강의 중 Claude 호출 줄이는 법:**
- 작업지시서가 명확하면 Copilot만으로 충분
- 새 결정이 필요한 경우만 Claude 호출
- 호출 시 컨텍스트(파일 경로·Task ID) 명시해서 한 번에 끝
- 강의 후 다음 강의를 위한 보강은 별도 시간에

---

## 6. 강의 종료 시 루틴 (매 교시 종료 5분)

```bash
# 1. 진행 상태 push
git status
git push origin <current-branch>   # WIP commit이라도 OK

# 2. 다음 강의 진입점 메모
echo "[$(date)] 다음 강의 시작 시: Task NN 부터" >> docs/refactor/_session_log.md

# 3. 발생한 이슈·결정 보류 항목 기록
# DEFERRED.md 또는 강의 노트에 기재
```

---

## 7. 참고 문서

- `docs/refactor/PRINCIPLES.md` — 모든 결정의 근거
- `docs/refactor/PLAN.md` — Phase·Task 로드맵
- `docs/refactor/TASK_TEMPLATE.md` — Task 작성 양식
- `md/gc.md` — INV·SYNC·CODE 검사 (헌법)
- `.github/copilot-instructions.md` — Copilot 자동 로드 규칙
