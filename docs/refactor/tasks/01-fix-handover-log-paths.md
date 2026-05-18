# Task 01 — handover_log 경로 표기 환경 무관화

## 1. 한 줄 요약
`md/handover_log.md`에 박힌 `c:\blackbox` Windows 절대 경로를 환경 무관 표기로 교체해 WSL/macOS/Linux 사용자도 정확히 이해하게 한다.

## 2. 변경 범위 (HARD LIMIT)

- **파일 (이외 금지):**
  - `md/handover_log.md`
- **메서드/함수/클래스:** N/A (문서)
- **수정 대상 라인:** `c:\blackbox` 또는 `c:\\blackbox` 표기를 포함한 라인만
- **예상 변경 라인 수:** 5~10줄
- **예상 변경 파일 수:** 1개

## 3. 절대 건드리지 말 것 (Out of Scope)

- 같은 파일의 **경로 표기와 무관한 모든 내용** (Bug Fix Log, Technical Decisions, API 표 등 절대 수정 금지)
- 마크다운 스타일·표 정렬·줄바꿈 정리 (별도 Task)
- 다른 `md/` 파일이나 어떤 코드/문서
- §6-2의 단일 치환 룰 외 추가 "정리"·"개선" 시도 금지 (예: 다른 환경 의존 표기, 옛 일자 등 모두 그대로 유지)

## 4. 컨텍스트 / 의도

`md/handover_log.md`는 Antigravity Agent가 작성한 핸드오버 로그로, 작성 당시 Windows 환경 경로(`c:\blackbox`)가 그대로 박혀 있다. 현재 실제 작업 환경은 WSL2(Ubuntu) 안의 `/home/user/project/team-balckbox/blackbox`이며, 다른 팀원이 macOS/Linux에서 작업할 수도 있다. 환경 의존 표기는 신규 팀원 온보딩 시 혼동을 일으킨다.

- 관련 INV: 해당 없음
- 관련 Drift ID: 추후 D-DOC-02로 등록
- 관련 PRINCIPLES 섹션: §2 (문서 권위 등급의 State 등급 — 문서 stale 정정)

## 4-1. 의존 관계

- **선행 Task:** 없음
- **후행 Task:** 없음 (병렬 가능)
- **API surface 변경 여부:** No

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §6 (Copilot 협업 규칙) 일부:

- 같은 파일 안에서도 작업지시서가 명시한 메서드/함수만 수정. 다른 메서드는 import 정리·네이밍·주석 정리도 금지.
- 추측 금지. 모르는 동작은 빈 함수로 두지 말고 `// [CONFIRM:?] ...` 주석으로 질문 남기기.

> PRINCIPLES.md §10 (문서화 원칙):

- "지금 어떻게 동작한다"로 서술. 변경 이력은 git/PR 담당.
- 마이그레이션 노트(이전 → 신규)는 별도 섹션.

## 6. 작업 절차

> 환경: **WSL Ubuntu bash** 기준 (PRINCIPLES §13).

### 6-1. 대상 라인 검색
```bash
grep -nE 'c:\\\\blackbox|c:/blackbox' md/handover_log.md
```
예상 매칭: 메타 표·디렉터리 트리·todo.md 등 다른 파일 경로 참조.

### 6-2. 단일 룰 치환

**모든 매칭은 다음 한 가지 룰을 적용:**
- `c:\blackbox` 또는 `c:/blackbox` (단독, 저장소 루트 의미) → `<repo>`
- `c:\blackbox\<path>` 또는 `c:/blackbox/<path>` (하위 경로) → `<path>` (저장소 루트 기준 상대 경로, 슬래시 사용)

예:
- `저장소 위치 | c:\blackbox` → `저장소 위치 | <repo>`
- `c:\blackbox\md\todo.md` → `md/todo.md`
- 디렉터리 트리의 루트 `c:\blackbox\` → `<repo>/`

### 6-3. 잔존 확인
```bash
grep -nE 'c:\\\\blackbox|c:/blackbox' md/handover_log.md
```
기대 결과: 매칭 0건.

### 6-4. 마크다운 구조 시각 확인
표 정렬·코드블록 펜스·헤더 깊이가 깨지지 않았는지 눈으로 확인 (수정 금지 — 구조가 깨졌다면 치환을 잘못한 것이므로 해당 라인만 다시).

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — 단일 파일·치환 패턴 명확. 5~10줄 범위. 즉시 실행 가능.

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `c:\blackbox` 표기가 `md/handover_log.md`에서 사라짐
- [ ] 마크다운 표·코드블록·헤더 구조 정상
- [ ] 다른 파일 변경 없음 (HARD LIMIT 준수)
- [ ] 같은 파일의 경로와 무관한 내용은 그대로 유지
- [ ] CI(refactor-guard) 통과
- [ ] PR diff: 5~10줄 변경, 1파일

## 9. PR 정보

- **Branch:** `refactor/01-fix-handover-log-paths`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-01] handover_log 경로 표기 환경 무관화`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 01](../docs/refactor/tasks/01-fix-handover-log-paths.md)

  ## 변경 요약
  - `c:\blackbox` 절대 경로 표기를 저장소 루트 기준 상대 경로로 교체
  - 환경별 차이를 추상화 (`<repo root>` 등)

  ## HARD LIMIT 준수
  - 파일: `md/handover_log.md`만
  - 라인: 약 N줄 변경 (경로 관련 라인만)

  ## 검수 체크리스트 결과
  - [x] grep 매칭 0건 확인
  - [x] 마크다운 구조 정상
  - [x] CI 통과
  ```
