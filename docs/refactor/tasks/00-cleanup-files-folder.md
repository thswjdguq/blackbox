# Task 00 — files/ 폴더 outdated 문서 아카이브

## 1. 한 줄 요약
루트 `files/` 폴더의 outdated 문서 2개를 `docs/_archive/`로 이동하고 헤더 마커·README·Copilot instructions 보강으로 비활성화한다 (삭제 X, 진화 흔적 보존).

## 2. 변경 범위 (HARD LIMIT)

- **이동 (git mv):**
  - `files/claude.md` → `docs/_archive/2026-05-09_legacy-claude.md`
  - `files/todo.md` → `docs/_archive/2026-05-09_legacy-todo.md`
- **수정 (이동된 파일에 헤더 추가):**
  - `docs/_archive/2026-05-09_legacy-claude.md` 최상단에 ARCHIVED 헤더
  - `docs/_archive/2026-05-09_legacy-todo.md` 최상단에 ARCHIVED 헤더
- **신규:**
  - `docs/_archive/README.md` (아카이브 폴더 안내)
- **수정 (1줄 추가):**
  - `.github/copilot-instructions.md` — "작업 범위 제한" 섹션에 `docs/_archive/` 인용 금지 규칙 1줄 추가
- **삭제:**
  - 빈 `files/` 디렉터리 (이동 후 자동 정리 또는 `rmdir files`)
- **메서드/함수/클래스:** N/A (코드 변경 없음)
- **예상 변경 라인 수:** ~30줄 (헤더 12줄 × 2 + README 약 15줄 + instructions 1줄)
- **예상 변경 파일 수:** 4개

### 단일 Task 파일 수 룰 예외 사유 (PRINCIPLES §13)

본 Task는 4파일 변경으로 "단일 Task ≤ 3 파일" 룰 초과. 다음 사유로 분할 금지·단일 Task 유지:
- 4 파일이 모두 **archive 정책의 단일 단위**: 이동 2건은 결과물(archived 파일), 신규 1건(README)은 그 정책 안내, 수정 1건(instructions)은 AI 차단
- 분할하면 archive 정책이 부분적으로 적용된 중간 상태가 생김(예: 파일은 옮겨졌는데 instructions에 인용 금지가 안 박힘)
- 변경 라인 총합 ~30줄로 모델 부담 작음 (룰의 본질인 "한 번에 변경량 ↓"는 충족)

## 3. 절대 건드리지 말 것 (Out of Scope)

- **이동된 파일의 본문 내용 — 헤더 추가 외 한 글자도 수정 금지** (오타·서식·문장 정리 등 어떤 "친절"도 금지. 보존이 목적)
- `md/` 폴더의 어떤 파일도 수정 금지
- `docs/` 폴더의 다른 파일 (`docker.md`, `refactor/*` 등) 수정 금지
- `.github/copilot-instructions.md`의 다른 섹션 수정 금지 (지정된 1줄 추가만)
- 다른 어떤 코드/문서·빌드 설정도 수정 금지
- `files/` 안에 만약 `claude.md`/`todo.md` 외 다른 파일이 있다면 → 작업 중지하고 사용자에게 보고

## 4. 컨텍스트 / 의도

`files/claude.md`, `files/todo.md`는 프로젝트 초기 버전의 잔존물:
- `files/claude.md`: MySQL 사용 (실제는 PostgreSQL), Vercel/Railway/Render 배포 (실제는 Docker 자체완결)
- `files/todo.md`: week 1-8까지의 옛 진행 상태 (현재 week9까지 진행)

정본은 `md/claude.md`, `md/todo.md`. 같은 이름 문서가 두 곳에 있어 Claude·Copilot·신규 팀원이 잘못된 정보를 인용할 위험.

**삭제하지 않는 이유:** 이 문서들은 프로젝트의 진화 흔적(MySQL→PostgreSQL, 외부 PaaS→Docker 자체완결)이라는 컨텍스트 가치가 있다. 보존하되 정본 혼동을 차단하는 방식(아카이브 + 헤더 마커 + Copilot 차단)을 선택.

- 관련 INV: gc.md INV-05 (외부 managed 서비스 금지) — outdated 문서가 위반된 것처럼 보이게 함
- 관련 Drift ID: 추후 D-DOC-01로 등록
- 관련 PRINCIPLES 섹션: §2 (문서의 권위 등급)

## 4-1. 의존 관계

- **선행 Task:** 없음
- **후행 Task:** 01, 02 (병렬 가능)
- **API surface 변경 여부:** No

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §2 (문서의 권위 등급) 발췌:

`files/` 폴더의 문서들은 outdated 중복본이다(MySQL/Vercel 등 폐기 정보). **인용 금지, Phase 0에서 정리**한다.

> PRINCIPLES.md §6 (Copilot 협업 규칙) 일부:

- 변경 범위 작업지시서 외 확장 금지 — 옆 파일이 더러워도 작업지시서에 없으면 건드리지 말 것
- 추측 금지 — 모르는 동작은 빈 함수로 두지 말고 작업지시서에 `[CONFIRM]` 태그로 질문 적기

## 6. 작업 절차

### 6-1. files/ 구성 사전 확인
```
ls files/
```
기대 결과: `claude.md`, `todo.md` 2개. 다른 파일이 있으면 작업 중지하고 사용자에게 보고.

### 6-2. docs/_archive/ 디렉터리 준비
```
mkdir -p docs/_archive
```

### 6-3. 파일 이동 (git mv로 history 보존)
```
git mv files/claude.md docs/_archive/2026-05-09_legacy-claude.md
git mv files/todo.md docs/_archive/2026-05-09_legacy-todo.md
```

### 6-4. 헤더 마커 추가

이동된 두 파일 최상단(첫 라인)에 정확히 다음 헤더를 추가. 본문 내용은 절대 수정 금지.

`docs/_archive/2026-05-09_legacy-claude.md`:
```markdown
<!--
ARCHIVED 2026-05-09 — 정본 아님. 인용 금지.
이 문서는 프로젝트 초기 버전(MySQL / Vercel·Railway·Render 기반)의 스냅샷입니다.
현재는 PostgreSQL + Docker 자체완결 구조로 전환되었습니다.
정본: md/claude.md
-->

```

`docs/_archive/2026-05-09_legacy-todo.md`:
```markdown
<!--
ARCHIVED 2026-05-09 — 정본 아님. 인용 금지.
이 문서는 week 1~8 시점의 옛 진행 상태 스냅샷입니다.
현재 진행 상태는 정본을 참조하세요.
정본: md/todo.md
-->

```

### 6-5. docs/_archive/README.md 신규 작성

```markdown
# docs/_archive — 보존용 아카이브

## 이 폴더는 무엇인가
프로젝트 진화 과정에서 폐기되었지만 **역사적 컨텍스트로서 보존 가치가 있는 문서**들이 있습니다.

## 정본 아님 — 인용·참조 금지
- 여기 있는 어떤 문서도 현재 시스템의 정본이 아닙니다.
- AI(Claude·Copilot 등)·신규 팀원·외부 리뷰어 모두 **인용 금지**.
- 정본 위치: `md/` 폴더 (`md/claude.md`, `md/todo.md` 등)

## 파일 명명 규칙
`YYYY-MM-DD_<의미>.md` — 아카이브 시점을 prefix로.

## 현재 보관 항목
| 파일 | 원래 위치 | 아카이브 사유 |
|---|---|---|
| `2026-05-09_legacy-claude.md` | `files/claude.md` | MySQL/Vercel·Railway 등 폐기된 스택을 담은 초기 컨텍스트 |
| `2026-05-09_legacy-todo.md` | `files/todo.md` | week 1~8 옛 진행 상태 (현재는 week9+) |

## 신규 추가 시 규칙
1. 위 명명 규칙 따라 파일명 결정.
2. 파일 최상단에 ARCHIVED 헤더 마커 추가 (날짜 + 정본 위치).
3. 이 README의 "현재 보관 항목" 표에 행 추가.
```

### 6-6. .github/copilot-instructions.md 보강 (1줄 추가)

"작업 범위 제한 (HARD LIMIT)" 섹션 안에 다음 1줄 추가 (마지막 불릿으로):

```
- `docs/_archive/` 안의 모든 문서는 인용·참조 금지 (deprecated 보존본). 컨텍스트로 사용하지 말 것.
```

위치: "테스트가 깨지면 코드를 다시 보라(테스트가 옳을 가능성)." 다음 줄.

### 6-7. files/ 디렉터리 정리

이동 후 `files/`가 비어있는지 확인 후 제거 (WSL Ubuntu bash):
```bash
ls files/    # 비어있어야 함
rmdir files
```

git이 빈 디렉터리는 자동으로 추적하지 않으므로 추가 git 명령 불필요.

### 6-8. 다른 곳에서의 참조 검색

```
grep -rn "files/claude\.md\|files/todo\.md" . --include="*.md" --include="*.java" --include="*.ts" --include="*.tsx" --include="*.yml"
```

기대 결과: 매칭 0건 (단, 본 작업지시서 자체와 새로 만든 README는 제외).
매칭이 있으면 PR description에 보고만 하고 별도 Task로 처리.

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — 절차 명확. 변경 4~5파일 / ~30줄. HARD LIMIT 안.

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `files/` 디렉터리 제거됨
- [ ] `docs/_archive/2026-05-09_legacy-claude.md`, `docs/_archive/2026-05-09_legacy-todo.md` 존재 (git mv로 history 보존)
- [ ] 두 파일 최상단에 ARCHIVED 헤더 마커 존재
- [ ] **두 파일의 본문(헤더 제외)은 원본과 100% 동일** — `git diff -M --find-renames=80%` 결과가 "헤더 추가" 외 변경 0건이어야 함. 한 글자도 수정 금지(§3 반복 강조)
- [ ] `docs/_archive/README.md` 존재 + 보관 항목 표에 2행 기재
- [ ] `.github/copilot-instructions.md`에 `docs/_archive/` 인용 금지 1줄 추가 (다른 섹션 수정 0건)
- [ ] 코드 0줄 변경
- [ ] CI(refactor-guard) 통과
- [ ] 다른 파일·코드 변경 없음 (HARD LIMIT 준수)

## 9. PR 정보

- **Branch:** `refactor/00-archive-files-folder`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-00] files/ 폴더를 docs/_archive/로 비활성화`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 00](../docs/refactor/tasks/00-cleanup-files-folder.md)

  ## 변경 요약
  - `files/claude.md`, `files/todo.md`를 `docs/_archive/`로 이동 (git mv로 history 보존)
  - 두 파일에 ARCHIVED 헤더 마커 추가 (정본 위치 명시)
  - `docs/_archive/README.md` 신규 — 아카이브 폴더 안내
  - `.github/copilot-instructions.md`에 `docs/_archive/` 인용 금지 1줄 추가
  - `files/` 빈 디렉터리 제거

  ## 보존 이유
  프로젝트 진화 흔적(MySQL→PostgreSQL, 외부 PaaS→Docker 자체완결)이라는 컨텍스트 가치 보존.

  ## HARD LIMIT 준수
  - 변경 파일: 이동 2 + 신규 1 + 수정 1 = 총 4파일
  - 코드 변경 0줄, 문서 변경 ~30줄

  ## 검수 체크리스트 결과
  - [x] grep 검색에서 잔존 참조 없음
  - [x] CI 통과
  ```
