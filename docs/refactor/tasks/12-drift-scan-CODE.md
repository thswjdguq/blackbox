# Task 12 — Drift Scan: CODE (금지 패턴)

## 1. 한 줄 요약
`md/gc.md` CODE-01~03 검사를 사용자가 실행하고 발견된 금지 패턴(any 타입, 하드코딩 시크릿, Entity 노출 등)을 `docs/refactor/DRIFT_INVENTORY.md`의 CODE 섹션에 누적한다.

## 2. 변경 범위 (HARD LIMIT)

- **수정 파일:**
  - `docs/refactor/DRIFT_INVENTORY.md` — CODE 섹션 placeholder를 표로 교체
- **수정 라인 수:** ~50~120줄 (CODE-01의 7개 검사 결과)
- **변경 파일 수:** 1개

## 3. 절대 건드리지 말 것 (Out of Scope)

- 발견된 패턴의 **수정 시도 절대 금지** — 본 Task는 *기록*만
- INV(Task 10) / SYNC(Task 11) 섹션 수정 금지
- CI(refactor-guard.yml)가 이미 잡는 항목과 일부 중복될 수 있음 — DRIFT_INVENTORY는 *결정 추적*용. CI는 *예방*용. 둘 다 필요
- 코드·테스트·다른 문서 일체 수정 금지

## 4. 컨텍스트 / 의도

CODE 검사는 **금지 패턴의 잔존 여부**를 본다. Phase 3(품질 개선)의 직접 입력. CODE-01의 일부 항목(`: any`, debug print)은 CI warn으로 이미 잡히지만, 본 Task는 위치·맥락·결정을 추적용으로 기록.

- 관련 CODE: CODE-01~03 전체
- 관련 PRINCIPLES: §3 (Drift 처리 규칙), §13 (오케스트레이터 패턴)

## 4-1. 의존 관계

- **선행 Task:** 10 (DRIFT_INVENTORY.md 스켈레톤)
- **후행 Task:** 13 (분류)
- **API surface 변경 여부:** No

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §3 (Drift 처리 규칙): SYNC/CODE 검사 실제 실행하여 표를 채운다.
> PRINCIPLES.md §13: 명령은 사용자 실행. Copilot은 안내·정리만.

## 6. 작업 절차 (오케스트레이터 + 사용자 실행 패턴)

> 환경: **WSL Ubuntu bash**.

### 6-1. CODE 섹션 placeholder 교체

```markdown
## CODE (금지 패턴) — Task 12

| ID | 출처 | 문서 진술 | 코드 실제 | 등급 | 결정안 | 사용자 결정 | Task |
|---|---|---|---|---|---|---|---|
```

### 6-2. CODE-01-A — file_vault UPDATE/DELETE
INV-02와 중복. **본 Task에서는 skip.** 실행 로그 비고에 "INV-02와 중복, Task 10 결과 참조" 기록.

### 6-3. CODE-01-B — 외부 managed 서비스 URL
INV-05와 중복. **skip.**

### 6-4. CODE-01-C — TypeScript `: any` 사용

Copilot 발화:
> "다음 명령 결과 부탁드려요:
> ```bash
> grep -rnE ':\\s*any(\\s|;|,|\\)|\\]|>|$)' \
>   --include='*.ts' --include='*.tsx' \
>   --exclude-dir=node_modules --exclude-dir=.next \
>   frontend/src/ 2>/dev/null
> ```
> 결과를 모두 알려주세요. 비면 'none'."

Copilot 처리: 파일별로 위치 그룹화. 1행에 한 파일(여러 라인이면 카운트)로 기록 (Convention 등급, D1 1차).

### 6-5. CODE-01-D — Controller에서 Entity 직접 노출

Copilot 발화:
> "다음 명령 결과 부탁드려요:
> ```bash
> grep -rnE 'ResponseEntity<.*Entity[ ,>]|return [a-zA-Z]+Entity\\b' \
>   backend/src/main/java/com/blackbox/controller/ \
>   --include='*.java'
> ```
> 결과 모두. 비면 'none'."

Copilot 처리: 파일:라인별로 1행 (Convention, D1).

### 6-6. CODE-01-E — 하드코딩 비밀번호/시크릿

Copilot 발화:
> "두 명령 결과 부탁드려요:
> ```bash
> grep -rnE 'password\\s*[:=]\\s*\"[^\\$\"][^\"]*\"' \
>   --include='*.java' --include='*.yml' --include='*.yaml' \
>   --include='*.ts' --include='*.tsx' \
>   --exclude-dir=node_modules --exclude-dir=.next \
>   --exclude-dir=test --exclude-dir=tests \
>   backend/ frontend/ 2>/dev/null \
>   | grep -viE 'example|placeholder|change_me|your_|test|mock'
> echo '---'
> grep -rnE 'apiKey|secret|token' \
>   --include='*.java' --include='*.yml' --include='*.yaml' \
>   --include='*.ts' --include='*.tsx' \
>   --exclude-dir=node_modules --exclude-dir=.next \
>   --exclude-dir=test --exclude-dir=tests \
>   backend/ frontend/ 2>/dev/null \
>   | grep -E '\"[A-Za-z0-9_\\-]{20,}\"' \
>   | grep -viE 'example|placeholder|your_|sample'
> ```
> 결과 비면 'none'. 있으면 모두 붙여주세요. (검토자 판단 필요할 수 있음)"

Copilot 처리: 의심 항목 1행씩 (**Spec 등급**, D1 — 즉시 수정 필요. CRITICAL 표시).

### 6-7. CODE-01-F — console.log / System.out.println 잔류

Copilot 발화:
> "두 명령 결과 부탁드려요:
> ```bash
> grep -rnE 'console\\.(log|debug|info)' \
>   --include='*.ts' --include='*.tsx' \
>   --exclude-dir=node_modules --exclude-dir=.next \
>   frontend/src/ 2>/dev/null | wc -l
> echo '---'
> grep -rnE 'System\\.(out|err)\\.print' \
>   --include='*.java' \
>   backend/src/main/java/ 2>/dev/null | wc -l
> ```
> 두 숫자 알려주세요. 0이 아니면 위 명령에 `| head -50` 붙여서 위치도 부탁드려요."

Copilot 처리: 카운트만 1행으로 기록 (Convention, D1, Phase 3에서 일괄 처리). 위치 너무 많으면 "N건 — Phase 3 batch fix" 1행만.

### 6-8. CODE-02 — activity_logs 커버리지

INV-01과 일부 중복. 본 Task에서는 INV-01에서 이미 수집된 결과를 참조 표시만:
> "INV-01 결과(Task 10) 참조. 추가 검사 불필요."

실행 로그 비고에 "CODE-02 INV-01과 중복" 기록.

### 6-9. CODE-03 — Flyway 연속성·실행 상태

Copilot 발화:
> "다음 명령 결과 부탁드려요:
> ```bash
> ls backend/src/main/resources/db/migration/V*.sql | sort -V
> ```
> V1~V17 순서가 끊기지 않는지 확인 부탁드려요. 끊긴 번호 있으면 알려주세요."

Copilot 처리:
- 끊긴 번호 있으면 1행 (Spec, D1)
- 없으면 'none' (실행 로그)

### 6-10. 실행 로그 갱신 + 사용자 검증

Copilot 발화:
> "CODE-01~03 검사 완료. 실행 로그에 추가, CODE 섹션에 N개 행 기록.
> 중복 항목(CODE-01-A/B, CODE-02): INV-02·05·01에서 이미 처리.
> 검토 부탁드립니다."

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — 절차 명확.
- **검색은 Copilot이 직접 실행하지 않음**

## 8. 검수 기준 (Acceptance Criteria)

- [ ] DRIFT_INVENTORY.md CODE 섹션 채워짐
- [ ] **모든 행 데이터는 사용자 실행 기반** (환각 0)
- [ ] CODE-01-C/D/E/F + CODE-03 5개 검사 완료
- [ ] CODE-01-A/B + CODE-02 중복 항목은 실행 로그 비고에 명시 (행 추가 X)
- [ ] 하드코딩 시크릿(CODE-01-E) 발견 시 CRITICAL 표시
- [ ] INV(Task 10)·SYNC(Task 11) 섹션 수정 0건
- [ ] 코드 변경 0줄
- [ ] CI 통과

## 9. PR 정보

- **Branch:** `refactor/12-drift-scan-CODE`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-12] Drift Scan CODE-01~03`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 12](../docs/refactor/tasks/12-drift-scan-CODE.md)
  - 의존: Task 10 머지

  ## 검사 결과 요약
  - CODE-01-C (`: any`): <N>건
  - CODE-01-D (Entity 노출): <N>건
  - CODE-01-E (하드코딩 시크릿): <N>건 (CRITICAL: <Y/N>)
  - CODE-01-F (debug print): <N>건
  - CODE-03 (Flyway 연속성): <OK/끊김>

  ## 중복 처리
  - CODE-01-A (file_vault): INV-02 참조
  - CODE-01-B (외부 서비스): INV-05 참조
  - CODE-02 (activity_logs): INV-01 참조

  ## HARD LIMIT 준수
  - 변경 파일: DRIFT_INVENTORY.md만
  - 코드 변경 0줄

  ## 검수
  - [x] 환각 0
  - [x] CI 통과
  ```
