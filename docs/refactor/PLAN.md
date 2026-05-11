# 리팩토링 로드맵 (PLAN)

> 이 문서는 `refactor/main` 브랜치 전체 리팩토링의 Phase별 로드맵이다.
> 각 Phase는 완료 조건이 명시되어 있고, 그 조건이 충족되어야 다음 Phase로 진입한다.
> 원칙은 `PRINCIPLES.md`, 작업지시서 양식은 `TASK_TEMPLATE.md` 참조.

---

## 0. 운영 개요

### 작업 흐름 (반복 단위)

```
[계획] Claude: 다음 1~3개 Task의 작업지시서 1차안 작성
   ↓
[검토] 사용자: 작업지시서 검토 → 피드백 → 확정
   ↓
[실행] Copilot Edits/Workspace: 확정된 작업지시서 받아 실행
   ↓
[PR]   사용자: refactor/<task-id>-<name> 브랜치에서 PR (base: refactor/main)
   ↓
[CI]   refactor-guard.yml 자동 실행 (critical fail / warn)
   ↓
[검수] Claude or 사용자: REVIEW_CHECKLIST + Task별 acceptance 검토
   ↓
[머지] squash merge → 작업 브랜치 삭제 → 다음 묶음으로
```

### 산출물 일람

| 산출물 | 위치 | 작성/유지 주체 |
|---|---|---|
| 정본 원칙 | `docs/refactor/PRINCIPLES.md` | Claude (사용자 검토) |
| 로드맵 | `docs/refactor/PLAN.md` (이 파일) | Claude (사용자 검토) |
| Task 템플릿 | `docs/refactor/TASK_TEMPLATE.md` | Claude |
| 작업지시서 | `docs/refactor/tasks/NN-xxx.md` | Claude → 사용자 검토 |
| Drift 인벤토리 | `docs/refactor/DRIFT_INVENTORY.md` | Phase 1에서 Copilot 1차 채움 → Claude 결정안 → 사용자 확정 |
| 의사결정 로그 | `docs/refactor/DECISIONS.md` | Claude (사용자 결정 기록) |
| 검수 체크리스트 | `docs/refactor/REVIEW_CHECKLIST.md` | Claude |
| 유예 항목 | `docs/refactor/DEFERRED.md` | 작업 중 발생 시 추가 |
| Copilot always-on | `.github/copilot-instructions.md` | Claude (PRINCIPLES 변경 시 동기화) |
| CI 가드 | `.github/workflows/refactor-guard.yml` | Claude |

### Task ID 체계

| 범위 | Phase | 용도 |
|---|---|---|
| `00`~`09` | Phase 0 | 안전망·문서 정리 |
| `10`~`19` | Phase 1 | 인벤토리·Drift 분석 |
| `20`~`29` | Phase 2 | Cross-cutting (예외 처리·공통 유틸·DB 레거시) |
| `30`~`39` | Phase 2 | 백엔드 도메인 (auth/project/task/meeting/vault/score/외부연동) |
| `40`~`49` | Phase 2 | 프론트엔드 도메인 (페이지·컴포넌트·store 정리) |
| `50`~`59` | Phase 3 | 백엔드 코드 품질·가독성 |
| `60`~`69` | Phase 3 | 프론트엔드 코드 품질·가독성 |
| `70`~`79` | Phase 4 | 발견된 버그·UX 수정 |
| `80`~`89` | Phase 5 | 마감 (테스트·동작 확인) |
| `90`~`99` | Phase 6 | 문서화 |

번호는 작업 순서가 아니라 **Phase 식별자**. 같은 Phase 안에서 의존성 따라 순서 결정.

---

## Phase 0 — 안전망 + 문서 베이스라인 (선행 필수)

**목적:** 본격 리팩토링 전에, 회귀를 잡을 그물과 정확한 정본 문서를 확보.

### Task 후보

- `00-cleanup-files-folder` — `files/` 폴더의 outdated 문서(MySQL/Vercel) 처리
  - 결정 옵션: 삭제 vs `_deprecated` rename vs `md/`로 통합
  - **사용자 결정 필요** (1차안: 삭제)
- `01-fix-handover-log-paths` — `md/handover_log.md`의 `c:\blackbox` 경로 표기를 정확히 (또는 환경 무관 표기로)
- `02-build-baseline` — backend `./gradlew build`, frontend `npm run build` + `npm run type-check` 통과 베이스라인 확인 + 결과 캡처 (`docs/refactor/baseline.md`)
- `03-test-coverage-snapshot` — 현재 테스트 존재 여부·커버리지 파악 (없으면 그대로 기록, 추가는 본 리팩토링 범위 외)
- `04-add-pr-template` — `.github/PULL_REQUEST_TEMPLATE.md` 추가 (HARD LIMIT 준수 + DRIFT_INVENTORY ID 참조 + 검수 체크리스트)
- `05-smoke-test-scenarios` — `docs/refactor/SMOKE_TESTS.md` 작성. **자동 테스트가 없으므로 수동 smoke test 시나리오를 문서화**. **Claude 직접 작성**(예외 실행, PRINCIPLES §13 참조). 핵심 happy path 7개:
  1. 회원가입 → 로그인 → JWT 발급
  2. 프로젝트 생성 → 초대코드 → 멤버 참여
  3. 칸반 태스크 생성 → 드래그앤드롭 상태 변경 → 삭제
  4. 회의 생성 → 체크인 → 회의록 저장 → 액션아이템 → 태스크 변환
  5. 파일 업로드 → SHA-256 해시 생성 → 동일 파일 재업로드 → 변조 감지
  6. 점수 재계산 → 기여도 페이지 표시
  7. 무결성 PDF 다운로드 → 증거 패키지 ZIP 다운로드
  - 각 시나리오마다 "이전 동작 / 예상 결과" 명시
  - Phase 2~5 마일스톤마다 수동 실행
- `06-smoke-test-baseline-run` — Task 05의 SMOKE_TESTS 시나리오 7개를 **실제로 1회 실행**하고 결과를 SMOKE_TESTS.md "실행 이력" 표 첫 행에 기록.
  - **Copilot 오케스트레이터 + 사용자 실행 패턴** (PRINCIPLES §13)
  - 의존: Task 02 빌드 통과 + Task 05 머지
- `07-add-review-checklist` — `docs/refactor/REVIEW_CHECKLIST.md` 신규. PRINCIPLES §11이 명시하는 검수 도구. Task 00 PR부터 사용.
- `08~09` — **의도적 미사용 (gap)**. Phase 0에 추가 작업 발생 시 채움. lazy 생성 항목(DECISIONS.md, DEFERRED.md)은 Task ID 없이 필요 시점에 생성.

### 완료 조건

- [ ] `files/` 폴더 처리 완료, `docs/_archive/`로 이동 (Task 00)
- [ ] handover_log 경로 정정 (Task 01)
- [ ] backend·frontend 빌드 통과 + `docs/refactor/baseline.md` 기록 (Task 02)
- [ ] 테스트 존재 여부 + `TEST_COVERAGE.md` 기록 (Task 03)
- [ ] PR 템플릿 적용 (Task 04)
- [ ] `SMOKE_TESTS.md` 7개 시나리오 작성 (Task 05)
- [ ] SMOKE_TESTS 베이스라인 1회 실행 + 실행 이력 표 첫 행 기록 (Task 06)
- [ ] REVIEW_CHECKLIST.md 적용 (Task 07)

### 다음 진입 조건

위 모든 항목 [x]. baseline 빌드 결과가 깨끗(또는 알려진 실패만 기록).

---

## Phase 1 — Drift 인벤토리 (탐지)

**목적:** 코드를 읽지 않고 추측하지 않기 위해, **gc.md의 SYNC/CODE 검사를 실제 실행**해서 모든 Drift 항목을 한 표에 모은다.

### Task 후보 — 분할 스캔 (체크포인트별 사용자 검토)

> 한 번에 모든 검사를 돌리면 인벤토리가 비대해져 사용자 검토가 형식적이 됨.
> 검사를 3배치로 나누고 각 배치 후 사용자 검토 → 다음 배치 진행.

- `10-drift-scan-INV` — `gc.md` INV-01~07 (불변 규칙 위반) 스캔
  - 우선순위: INV-02(file_vault), INV-05(외부서비스), INV-01(activityLog) → INV-03/04/06/07
  - Copilot이 grep 실행 + 결과를 `DRIFT_INVENTORY.md` (INV 섹션)에 추가
- `11-drift-scan-SYNC` — `gc.md` SYNC-01~05 (크로스파일 일관성) 스캔
  - SYNC-01 (DB↔Entity↔Flyway), SYNC-02 (TS↔Java DTO), SYNC-03 (API↔Controller↔훅), SYNC-04 (env), SYNC-05 (기획서↔구현)
- `12-drift-scan-CODE` — `gc.md` CODE-01~03 (금지 패턴) 스캔
  - file_vault mutation, 외부 서비스 URL, `: any`, Entity 직접 노출, 하드코딩 시크릿, console.log/System.out
- `13-drift-classify` — Claude가 모든 항목에 등급(Spec/State/Convention) + 결정 1차안(D1~D4) 제시
- `14-drift-user-review` — 사용자 일괄 검토 (배치별 또는 전체). "사용자 확인 필요(D4)" 항목은 별도 질문 묶음으로 한꺼번에
- `15-drift-task-mapping` — 확정된 Drift 항목을 Phase 2~4의 구체 Task로 매핑

### 완료 조건

- [ ] INV/SYNC/CODE 3배치 모두 스캔 완료 (Copilot)
- [ ] `DRIFT_INVENTORY.md`에 모든 항목 기재
- [ ] 모든 항목에 등급 + 결정 1차안 (Claude)
- [ ] 모든 항목에 사용자 확정 결정 기재
- [ ] D1(코드 수정) 항목들이 Phase 2~4의 Task로 매핑됨
- [ ] D2(문서 수정) 항목들이 Phase 6 묶음으로 들어감
- [ ] D3(의도적 차이) 항목들이 `DECISIONS.md`에 기록됨

### 다음 진입 조건

`DRIFT_INVENTORY.md`의 모든 행에 사용자 결정이 들어가 있음.

---

## Phase 2 — 구조/아키텍처 개선 (Cross-cutting 먼저, 도메인 나중)

**목적:** 모듈 경계·레이어 책임·DB 레거시를 정리. 이후 Phase의 토대.

### 진행 순서 (중요)

```
2A. Cross-cutting (20번대) → 모든 도메인 작업의 베이스라인 확립
   ↓
2B. 백엔드 도메인 (30번대)
   ↓
2C. 프론트 도메인 (40번대)
```

Cross-cutting을 먼저 하지 않으면, 도메인 Task들이 각자 다른 패턴을 만들어 스파게티 누적.

### Task 후보 — 2A. Cross-cutting (20~29)

- `20-exception-handling-unify` — `GlobalExceptionHandler` 통일, 미처리 예외가 403으로 변환되는 버그 패턴 차단
- `21-webclient-error-pattern` — `WebClient` 호출부 에러 핸들러(`onErrorReturn` + try-catch) 일괄 점검·정리
- `22-activity-log-coverage` — INV-01 위반 누락 메서드 추가 (Drift 인벤토리 결과 기반)
- `23-projectaccesschecker-usage` — 권한 검증 위치·방식 통일
- `24-db-legacy-score-columns` — V18 마이그레이션: 숫자 점수 컬럼 deprecate (코드에서 사용 제거 PR 먼저, 그 다음 DROP)
- `25-db-oauth-tokens-unify` — `oauth_tokens` ↔ `google_calendar_tokens` 정책 결정 후 통합 또는 명시적 분리

### Task 후보 — 2B. 백엔드 도메인 (30~39)

- `30-auth-module` / `31-project-module` / `32-task-module` / `33-meeting-module` / `34-vault-module` / `35-score-module` / `36-calendar-module` / `37-notion-module` / `38-discord-module` / `39-report-module`
- 각 도메인별로 Service 분리, DTO 정리, 패키지 경계 점검
- 도메인 패키지 구조 결정 필요 (현재는 `controller/`, `service/`, `dto/`, `entity/` 횡단 분류 — 이걸 유지할지 도메인별 패키지로 갈지)

### Task 후보 — 2C. 프론트 도메인 (40~49)

- `40-api-layer-unify` — `lib/api.ts` 사용 일관성, 도메인별 API 모듈(`lib/api/task.ts` 등) 도입 검토
- `41-store-organize` — Zustand store 위치·단위 정리
- `42-types-organize` — `src/types/` 정리 (백엔드 DTO와 1:1 매핑 검증)
- `43-page-routing` — App Router 페이지 정리 (project-scoped vs 전역 shim)
- `44-components-organize` — `components/` 도메인별 그룹핑

### 도메인 Task acceptance 추가 규칙

각 도메인 Task가 **API surface(엔드포인트·DTO 시그니처)를 변경**하면 acceptance에 다음 포함:
- `md/handover_log.md`의 해당 도메인 API 표 갱신
- `src/types/<domain>.ts` 갱신 (백엔드 DTO와 1:1)
- 자체 narrative 문서는 Phase 6에서 일괄. **여기서는 사실(API 목록·DTO 형태)만 갱신**.

API surface 변경이 없는 순수 내부 리팩토링은 문서 갱신 면제.

### 완료 조건

- [ ] 2A 모든 Task 완료 (cross-cutting 베이스라인 확립)
- [ ] 모든 도메인의 백엔드·프론트 패키지 구조가 PRINCIPLES §8/§9에 부합
- [ ] DB 레거시 deprecate 결정된 컬럼이 코드에서 사용 안 됨
- [ ] API surface 변경된 도메인은 handover_log API 표·types 갱신 완료
- [ ] CI(refactor-guard) 모든 critical 통과
- [ ] 빌드·타입체크 통과
- [ ] `SMOKE_TESTS.md` 7개 시나리오 수동 통과 (Phase 2 종료 시점)

### 다음 진입 조건

위 완료 조건 모두. 도메인 Task 진행 중 발견된 추가 Drift는 `DRIFT_INVENTORY.md`에 추가.

---

## Phase 3 — 코드 품질·가독성

**목적:** 구조가 정리된 위에서, 함수 단위·표현 단위 정리.

### Task 후보 — 백엔드 (50~59)

- `50-naming-consistency` — 메서드/변수 네이밍 통일 (도메인 용어집 기반)
- `51-magic-values-extract` — 매직 넘버·문자열 → 상수/enum
- `52-large-method-split` — 임계 라인 수(예: 50줄) 초과 메서드 분리
- `53-validation-pattern` — `@Valid` + DTO 어노테이션 일관 적용
- `54-logger-replace-sysout` — `System.out.println` 잔류 → `Logger`

### Task 후보 — 프론트 (60~69)

- `60-naming-consistency`
- `61-component-extract` — 거대 컴포넌트 분리 기준 (예: 200줄)
- `62-typescript-strict` — `: any` 제거, 타입 보강 (Drift 결과 기반)
- `63-design-token-apply` — `frontend-design.md` 토큰 일관 적용 점검
- `64-dead-code-remove` — 죽은 코드·미사용 import 제거

### 완료 조건

- [ ] CI warn 항목들이 의미있게 감소 (`: any` 카운트, debug print 카운트)
- [ ] 도메인 용어집 정리 (`docs/refactor/GLOSSARY.md` 신규)
- [ ] 빌드·타입체크 통과

---

## Phase 4 — 발견된 버그·UX 수정

**목적:** Phase 1~3 진행 중 발견된 진짜 버그·UX 이슈를 별도 Task로 처리.

### Task 후보 (70~79)

- 인벤토리 작성 중 발견된 항목들로 채움
- 형식: `7N-fix-<short-description>` (예: `70-fix-meeting-save-blank-string`)
- 이 Task들은 `refactor:` 가 아니라 `fix:` 커밋

### 완료 조건

- [ ] 모든 발견된 버그가 Task로 등록되어 처리되었거나, `DEFERRED.md`에 사유와 함께 기재
- [ ] 동작 변경(fix)은 별도 PR로 분리되어 머지됨

---

## Phase 5 — 마감 (테스트·동작 확인)

**목적:** 머지 직전, 전체가 동작하는지 확인.

### Task 후보 (80~89)

- `80-build-full` — backend·frontend 클린 빌드
- `81-docker-up-smoke` — `docker compose up -d --build` 전체 스택 기동 + 기본 시나리오 동작 확인
- `82-flyway-verify` — V1~V18+ 순차 실행 확인 (`flyway_schema_history`)
- `83-key-flow-walkthrough` — 핵심 시나리오 수동 워크스루 (로그인→프로젝트→칸반→회의→Vault→리포트)
- `84-gc-final-run` — `md/gc.md`의 전체 GC 검사 한 번 실행

### 완료 조건

- [ ] Docker 풀 스택 기동 성공
- [ ] 핵심 시나리오 수동 통과
- [ ] gc.md 검사 모두 통과 (또는 통과 못 한 항목이 `DECISIONS.md`에 기록)

---

## Phase 6 — 문서화

**목적:** 리팩토링 결과를 문서에 반영. **반드시 Phase 5 완료 후**에 진행 (실제 코드 보고 써야 함).

### Task 후보 (90~99)

- `90-readme-create` — 루트 `README.md` 신규 작성 (현재 없음)
- `91-claude-md-create` — 루트 `CLAUDE.md` 신규 작성 (Claude Code 컨텍스트)
- `92-md-claude-update` — `md/claude.md` 정본 갱신 (변경된 구조·도메인 반영)
- `93-handover-log-update` — `md/handover_log.md`에 리팩토링 완료 항목 추가
- `94-todo-md-sync` — `md/todo.md`의 [x] 표시를 실제 상태와 동기화
- `95-docs-architecture-create` — `docs/architecture.md` 신규 (현재 docker.md만 있음)
- `96-docs-database-create` — `docs/database.md` 신규 (V1~V18+ 스키마 정리)
- `97-docs-api-create` — `docs/api-design.md` 신규 (handover_log의 API 표를 정본화)

### 문서화 작업지시서의 강제 규칙 (TASK_TEMPLATE에 박힘)

- 실제 코드를 읽고 쓴다 (옛 구조나 추측 금지)
- "지금 어떻게 동작한다" (변경 이력 X)
- 예시 코드는 실제 파일에서 발췌
- 마이그레이션 노트(이전 → 신규)는 별도 섹션

### 완료 조건

- [ ] 루트 README.md, CLAUDE.md 존재
- [ ] `md/` 정본이 실제 코드 상태와 일치
- [ ] DRIFT_INVENTORY의 모든 D2(문서 수정) 항목 반영

---

## 1. Phase 간 의존성

```
Phase 0 (안전망·문서 정리)
   │
   ▼
Phase 1 (Drift 인벤토리)         ← 코드 본격 수정 전 필수
   │
   ▼
Phase 2A (Cross-cutting 20~29)   ← 도메인 작업 전 필수
   │
   ▼
Phase 2B (백엔드 도메인 30~39)   ┐
                                  ├─ 병렬 가능 (도메인 간 독립적)
Phase 2C (프론트 도메인 40~49)   ┘
   │
   ▼
Phase 3 (품질·가독성 50~69)      ← 구조가 안정된 후
   │
   ▼
Phase 4 (버그·UX 70~79)         ← 동시 진행 가능 (Phase 2~3 중에도)
   │
   ▼
Phase 5 (마감 80~89)
   │
   ▼
Phase 6 (문서화 90~99)           ← 코드 변경 종료 후
   │
   ▼
사용자: 팀원 승인 요청 → main 머지
```

**병렬 가능 지점:**
- Phase 2B와 2C: 도메인이 다르고 백엔드·프론트가 분리되어 있어 동시 진행 가능. 단, 같은 도메인의 BE/FE Task는 순차(BE 먼저).
- Phase 4: Phase 2~3 중에 발견되는 즉시 별도 PR로 처리 가능.

**병렬 금지:**
- Phase 1 완료 전에 Phase 2 Task 시작 금지 (Drift 분류 없이 작업하면 추측이 코드에 박힘).
- Phase 2A 완료 전에 도메인 Task 시작 금지 (cross-cutting 베이스라인 없이 도메인 정리하면 패턴 불일치).

---

## 2. 진행 상태 추적

각 Phase의 완료 조건을 체크박스로 추적. 진행 상태는 이 PLAN.md의 각 Phase 섹션에 직접 [x] 표기. 별도 진행 상태 파일은 만들지 않음 (단일 source).

Drift 인벤토리는 `DRIFT_INVENTORY.md`가 단일 source. 결정 변경 이력이 필요하면 `DECISIONS.md`에.

---

## 3. 일정 가이드

명시적 데드라인은 없음(사용자 페이스 + 팀원 승인 대기). 다만:

- **Phase 0~1**은 빠르게 (1~2 작업 세션). 본격 작업 전 의사결정 단계
- **Phase 2~3**이 시간의 대부분을 차지. 묶음 단위(1~3 Task)로 반복
- **Phase 4**는 자연 발생 처리
- **Phase 5~6**은 머지 직전 짧게

---

## 4. 참조

- `PRINCIPLES.md` — 모든 Task가 따라야 할 원칙
- `TASK_TEMPLATE.md` — Task 작성 표준 양식
- `md/gc.md` — INV·SYNC·CODE 검사 정의
- `md/claude.md`, `md/handover_log.md`, `md/todo.md` — 프로젝트 컨텍스트
