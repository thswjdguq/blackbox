# Task 10 — Drift Scan: INV (불변 규칙)

## 1. 한 줄 요약
`md/gc.md` INV-01~07 검사를 사용자가 실행하고 발견된 위반을 `docs/refactor/DRIFT_INVENTORY.md`의 INV 섹션에 표 형식으로 누적한다.

## 2. 변경 범위 (HARD LIMIT)

- **신규 파일:**
  - `docs/refactor/DRIFT_INVENTORY.md` (없으면 신규 생성, 있으면 INV 섹션만 추가)
- **수정 파일:** 없음 (다른 코드·문서·설정 0건)
- **메서드/함수/클래스:** N/A (코드 변경 없음)
- **예상 변경 라인 수:** ~150줄 (스켈레톤 50줄 + INV 7개 결과 행)
- **예상 변경 파일 수:** 1개

## 3. 절대 건드리지 말 것 (Out of Scope)

- 발견된 위반의 **수정 시도 절대 금지** — 본 Task는 *기록*만. 수정은 Phase 2~4 Task에서 별도
- DRIFT_INVENTORY.md의 SYNC/CODE 섹션 — Task 11/12에서 채움. 본 Task는 헤더 placeholder만 만들고 비워둠
- 코드·테스트·다른 문서 일체 수정 금지

## 4. 컨텍스트 / 의도

`md/gc.md`는 본 프로젝트의 헌법(INV-01~07 불변 규칙). 본 Task는 그 규칙을 **자동 검사**로 돌려 위반 현황을 가시화. 결과는 Phase 2~4 작업의 직접 입력이 됨.

INV 검사는 가장 critical (위반 시 시스템 핵심 가치 훼손). Spec 등급으로 분류되어 D1(코드 수정) Task로 직결.

- 관련 INV: INV-01~07 전체
- 관련 PRINCIPLES: §3 (Drift 처리 규칙), §13 (오케스트레이터 패턴)

## 4-1. 의존 관계

- **선행 Task:** 없음 (Phase 0 완료 후 Phase 1 진입 가정)
- **후행 Task:** 11, 12 (DRIFT_INVENTORY.md 같은 파일 사용. 11/12는 10 머지 후)
- **API surface 변경 여부:** No

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §3 (Drift 처리 규칙):

`md/gc.md`의 SYNC-01~05 / CODE-01~03 검사를 실제 실행하여 다음 표를 채운다.
형식: `| ID | 출처 | 문서 진술 | 코드 실제 | 등급(Spec/State/Conv) | 결정안 | 사용자 결정 | Task ID |`

> PRINCIPLES.md §13 (오케스트레이터 패턴):

명령은 사용자가 실행. Copilot은 안내·정리만. 환각 회피.

## 6. 작업 절차 (오케스트레이터 + 사용자 실행 패턴)

> **Copilot은 grep 명령을 직접 실행하지 않는다.** 사용자에게 명령 실행을 요청하고, 받은 출력을 표로 정리.
> 환경: **WSL Ubuntu bash** (PRINCIPLES §13).

### 6-1. DRIFT_INVENTORY.md 스켈레톤 생성

Copilot이 직접 작성 (검색 명령 없음, 정적 콘텐츠):

```markdown
# Drift Inventory

> Phase 1 산출물. `md/gc.md` INV/SYNC/CODE 검사 결과 누적.
> 각 행: 등급 분류(Phase 1 §13) → 사용자 결정(§14) → Task 매핑(§15) 순으로 채워짐.

## 표 컬럼 정의
| 컬럼 | 의미 |
|---|---|
| ID | D-INV-NN / D-SYN-NN / D-CODE-NN |
| 출처 | gc.md 검사 항목 (예: INV-02) |
| 문서 진술 | 기대되는 동작/규칙 |
| 코드 실제 | 발견된 위반/패턴 (파일:라인) |
| 등급 | Spec / State / Convention |
| 결정안 | D1(코드 수정) / D2(문서 수정) / D3(의도적 차이로 기록) / D4(사용자 결정) |
| 사용자 결정 | Phase 1 §14에서 확정 |
| Task ID | Phase 1 §15에서 매핑 |

## 실행 로그
| 일시 | 검사 배치 | 실행자 | 결과 행 수 |
|---|---|---|---|
| <YYYY-MM-DD> | INV-01~07 | <name> | <N> |

---

## INV (불변 규칙) — Task 10

| ID | 출처 | 문서 진술 | 코드 실제 | 등급 | 결정안 | 사용자 결정 | Task |
|---|---|---|---|---|---|---|---|

## SYNC (크로스파일 일관성) — Task 11에서 채움

(placeholder)

## CODE (금지 패턴) — Task 12에서 채움

(placeholder)
```

### 6-2~6-8. 사용자에게 INV-01~07 검사 요청 (각각)

각 INV 검사마다 동일 패턴:
1. 명령 안내
2. 사용자 출력 붙여넣기
3. 표에 행 추가 (위반 0건이면 "none — D-INV-NN 없음"으로 실행 로그 비고에 기록)

#### INV-01: activity_logs 기록 누락

Copilot 발화:
> "다음 명령 결과 부탁드려요. Service 메서드 중 activity_logs 기록 호출이 없는 의심 사례를 찾는 보조 검사입니다.
> ```bash
> grep -rn "public.*UUID\|public.*String" backend/src/main/java/com/blackbox/service/ \
>   --include="*.java" | head -50
> echo "---"
> grep -rln "activityLogService\|ActivityLogService" backend/src/main/java/com/blackbox/service/
> ```
> 첫 번째 결과의 메서드 시그니처 목록과 두 번째 결과의 파일 목록을 비교해서, **activityLogService를 import/주입하지 않은 service 파일**을 알려주세요."

받은 결과 → 파일별로 1행씩 추가 (Spec 등급, D1 결정안 1차).

#### INV-02: file_vault UPDATE/DELETE

Copilot 발화:
> "다음 명령 결과 부탁드려요:
> ```bash
> grep -rnE 'file_vault' --include='*.java' backend/src/ \
>   | grep -iE 'update|delete|remove|\\bset\\b' \
>   | grep -v '//'
> ```
> 결과가 비면 'none'이라고 알려주세요."

비면: 행 추가 안 함, 실행 로그에만 "INV-02 violations: 0" 기록.
있으면: 각 파일:라인을 1행으로 추가 (Spec, D1).

#### INV-03: 외부 API 쓰기 권한

Copilot 발화:
> "두 명령 결과 부탁드려요:
> ```bash
> grep -rnE 'scope|Scope' backend/src/main/java/ --include='*.java' | grep -i 'drive\\|github'
> echo '---'
> grep -rnE 'permissions|contents' backend/src/main/java/ --include='*.java' | grep -iE 'write|repo'
> ```
> drive scope에 readonly가 아닌 것, contents에 write가 있으면 알려주세요. 없으면 'none'."

#### INV-04: OBSERVER 데이터 수정

Copilot 발화:
> "다음 명령 결과 부탁드려요:
> ```bash
> grep -rn 'OBSERVER' backend/src/main/java/ --include='*.java'
> ```
> 결과 라인을 모두 붙여주세요. ProjectAccessChecker의 OBSERVER 허용 범위가 GET·PUT(weights)에 한정되어 있는지 함께 확인해주세요."

#### INV-05: Docker 자체완결

Copilot 발화:
> "다음 명령 결과 부탁드려요:
> ```bash
> grep -rnE 'supabase\\.(co|io)|vercel\\.app|render\\.com|railway\\.app|amazonaws\\.com|fly\\.io' \
>   --include='*.java' --include='*.yml' --include='*.yaml' \
>   --include='*.ts' --include='*.tsx' --include='*.json' \
>   --exclude-dir=node_modules --exclude-dir=.next --exclude-dir=build \
>   backend/ frontend/ docs/ md/ 2>/dev/null
> ```
> 비면 'none', 있으면 모두 붙여주세요. (단, `docs/_archive/` 안의 결과는 제외해주세요 — 의도적 보존)"

#### INV-06: 점수 정규화 상한

Copilot 발화:
> "다음 명령 결과 부탁드려요:
> ```bash
> grep -rnE 'normalize|Normalizer|git_score|task_score|meeting_score|doc_score' \
>   backend/src/main/java/com/blackbox/service/ --include='*.java' | head -50
> echo '---'
> grep -rnE 'Math\\.min.*150|min\\(150' backend/src/main/java/ --include='*.java'
> ```
> 첫 결과에서 점수 계산 위치를, 두 번째 결과에서 150 클리핑 위치를 확인. 클리핑 누락 의심되는 곳 알려주세요."

#### INV-07: 동의 없는 데이터 수집

Copilot 발화:
> "두 명령 결과 부탁드려요:
> ```bash
> grep -rn 'consent_platform\\|consent_github\\|consent_ai_analysis' \
>   backend/src/main/java/ --include='*.java'
> echo '---'
> grep -rn 'activityLogService\\.log\\|ActivityLogService' \
>   backend/src/main/java/com/blackbox/service/ --include='*.java' | head -30
> ```
> consent 체크가 ActivityLogService 또는 호출 측에 있는지, 누락된 곳이 있는지 알려주세요."

### 6-9. 실행 로그 갱신 + 사용자 검증

Copilot 발화:
> "INV-01~07 검사 완료. DRIFT_INVENTORY.md 실행 로그에 다음 행 추가했습니다:
> [행 인용]
> INV 섹션에 총 N개 행 추가. 검토 부탁드립니다."

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — 절차 6-1~6-9 명확. 사용자 응답 의존.
- **검색은 Copilot이 직접 실행하지 않음** (PRINCIPLES §13 환각 회피)

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `docs/refactor/DRIFT_INVENTORY.md` 신규 생성
- [ ] 표 컬럼 정의·실행 로그·INV/SYNC/CODE 섹션 헤더 모두 포함
- [ ] **모든 행 데이터는 사용자가 실제 실행해서 붙인 grep 결과 기반** (환각 0)
- [ ] INV-01~07 7개 모두 검사 (위반 없으면 "none" 명시)
- [ ] 위반 발견 시 파일·라인이 명시됨
- [ ] 등급 = Spec, 결정안 = D1 (1차안, Task 13에서 재검토)
- [ ] 사용자 결정·Task 칸은 비어있음 (Task 14·15에서 채움)
- [ ] SYNC/CODE 섹션은 placeholder (본 Task 범위 외)
- [ ] 코드 변경 0줄 (HARD LIMIT)
- [ ] CI(refactor-guard) 통과

## 9. PR 정보

- **Branch:** `refactor/10-drift-scan-INV`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-10] Drift Scan INV-01~07`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 10](../docs/refactor/tasks/10-drift-scan-INV.md)

  ## 변경 요약
  - DRIFT_INVENTORY.md 신규 (스켈레톤 + INV 섹션)
  - INV-01~07 검사 결과 기록

  ## 검사 결과 요약
  - INV-01 (activity_logs): <N>건
  - INV-02 (file_vault): <N>건
  - INV-03 (외부 쓰기 권한): <N>건
  - INV-04 (OBSERVER 권한): <N>건
  - INV-05 (외부 managed 서비스): <N>건
  - INV-06 (점수 정규화): <N>건
  - INV-07 (동의 체크): <N>건

  ## HARD LIMIT 준수
  - 변경 파일: 신규 1개
  - 코드 변경 0줄

  ## 검수 체크리스트 결과
  - [x] 환각 0 (모든 결과 사용자 실행)
  - [x] CI 통과
  ```
