# Task 07 — REVIEW_CHECKLIST.md 작성

## 1. 한 줄 요약
PR 검수의 단일 source인 `docs/refactor/REVIEW_CHECKLIST.md`를 신규 작성해 모든 PR이 머지 전 동일 기준으로 검수받도록 한다. (PRINCIPLES §11에 명시되어 있으나 파일 부재.)

## 2. 변경 범위 (HARD LIMIT)

- **신규 파일:**
  - `docs/refactor/REVIEW_CHECKLIST.md`
- **수정 파일:** 없음
- **메서드/함수/클래스:** N/A
- **예상 변경 라인 수:** ~120줄 (체크리스트 전체)
- **예상 변경 파일 수:** 1개

## 3. 절대 건드리지 말 것 (Out of Scope)

- 다른 어떤 파일도 수정 금지
- §6의 체크리스트 본문을 임의 편집 금지 — **그대로 transcription**
- "정리"·"개선"·"중복 제거" 시도 금지 (모든 항목 의도적)

## 4. 컨텍스트 / 의도

PRINCIPLES §11이 "REVIEW_CHECKLIST.md로 검수"를 의무화하는데 파일이 없음. Task 00의 첫 PR부터 필요한 도구라 Phase 0에 들어가야 맞음.

체크리스트는 다음을 통합:
- 모든 PR 공통 (HARD LIMIT·CI·base·커밋 분리 등)
- Task type별 (refactor / fix / docs)
- 실행 패턴별 (오케스트레이터·Claude 직접 환각 점검)
- INV 사후 점검 (CI 보완)
- Phase별 추가 항목

- 관련 PRINCIPLES: §11 (검수), §13 (저수준 모델 대응 — 환각 점검)

## 4-1. 의존 관계

- **선행 Task:** 없음 (Phase 0 첫 묶음과 병렬 가능)
- **후행 Task:** 모든 후속 PR (검수 도구로 사용)
- **API surface 변경 여부:** No

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §11 (검수):

각 PR 머지 전 사용자 또는 Claude가 `docs/refactor/REVIEW_CHECKLIST.md`로 검수한다. 통과 못 하면 머지 금지.

> PRINCIPLES.md §13 (저수준 모델 대응 — 명령 실행 + 출력 캡처 Task의 패턴):

환각 위험이 본질적이라 Copilot에게 직접 시키지 않는다. Copilot은 안내만, 사용자가 실행, 받은 출력을 정리.

## 6. 작업 절차

### 6-1. `docs/refactor/REVIEW_CHECKLIST.md` 신규 작성

다음 본문을 그대로 작성:

```markdown
# Review Checklist

> 모든 PR 머지 전 본 체크리스트로 검수 (PRINCIPLES §11).
> 검수자: 사용자 또는 Claude. 통과 못 하면 머지 금지.
> 결과는 PR description 또는 review comment에 §F 양식으로 기록.

---

## A. 모든 PR 공통

- [ ] 작업지시서 §8 acceptance 모든 항목 충족
- [ ] HARD LIMIT 외 파일·메서드 수정 없음 (`git diff --stat` 검토)
- [ ] CI(refactor-guard) critical job 모두 통과
- [ ] CI warn 항목 검토 (수용 가능한지 판단)
- [ ] PR diff 크기 가드 통과 (작업지시서 §2 예상치 ±30% 안)
- [ ] PR base가 `refactor/main` (절대 `main` 아님)
- [ ] PR 제목 `[refactor-NN] <한 줄 요약>` 양식
- [ ] 한 커밋에 한 종류 변경만 (rename / move / refactor / fix / feat / docs / chore 분리)
- [ ] `// [CONFIRM:?]` 잔존이 있다면 PR description에 명시
- [ ] 머지 방식: squash merge

## B. Task type별 추가

### B-1. refactor: (동작 보존)
- [ ] 외부에서 본 동작이 변경 전후 동일 (수동 또는 테스트로 확인)
- [ ] 의도적 동작 변경이 섞이지 않음 (섞였다면 별도 `fix:` 커밋으로 분리되어 있어야 함)
- [ ] 동작 보존이 의심되는 변경은 PRINCIPLES §7에 따라 유예 또는 별도 결정

### B-2. fix: (의도적 동작 변경)
- [ ] 변경된 동작이 PR description에 "Before/After"로 명시
- [ ] 영향 범위가 작업지시서 HARD LIMIT 안
- [ ] SMOKE_TESTS 해당 시나리오 통과 또는 시나리오 갱신

### B-3. docs:
- [ ] 실제 코드 기반 (추측 0)
- [ ] "지금 어떻게 동작한다" 서술 (변경 이력 X)
- [ ] 예시 코드는 실제 파일에서 가져옴

### B-4. chore: (인프라·설정)
- [ ] 영향이 빌드·CI에 한정
- [ ] 기존 동작에 부작용 없음

## C. 환각·품질 점검 (실행 패턴별)

### C-1. 오케스트레이터 패턴 (Task 02·03·06·10·11·12 등)
- [ ] **모든 출력은 사용자 실제 실행 결과** (Copilot이 만들어낸 출력 0건)
- [ ] 사용자 미제공 항목은 `<사용자 미제공>` 표기
- [ ] 명령 결과 spot check 1건 이상 (PR 작성자가 다시 실행해서 일치 확인)

### C-2. Claude 직접 실행 패턴 (Task 05 등)
- [ ] 작업지시서 본문(§6 골격)과 결과 파일이 1:1 대응
- [ ] Claude가 임의로 추가·삭제·변형한 부분 없음

### C-3. Copilot 일반 패턴
- [ ] 출력이 작업지시서 acceptance를 모두 다룸 (lost-in-the-middle 의심 점검)
- [ ] HARD LIMIT 외 파일에 변경이 들어가지 않았는지 diff 재확인

## D. INV 위반 사후 점검 (CI 보완)

- [ ] **INV-01**: 새 service 메서드에 `activityLogService.log()` 호출 포함 (해당 시)
- [ ] **INV-02**: `file_vault`에 INSERT 외 SQL 추가 없음
- [ ] **INV-03**: 새 OAuth scope 추가 시 read-only 확인
- [ ] **INV-04**: OBSERVER 권한이 GET·PUT(weights) 외로 확장 안 됨
- [ ] **INV-05**: 외부 managed 서비스 호스트·SDK 추가 없음
- [ ] **INV-06**: 점수 정규화 시 150 클리핑 적용
- [ ] **INV-07**: 새 데이터 수집 시 consent 체크 포함

> 위 7개는 CI도 일부 잡지만, 모든 패턴을 자동 탐지할 수 없어 사람이 사후 확인.

## E. Phase별 추가 점검

### E-1. Phase 0
- [ ] PLAN.md Phase 0 완료 조건 [x] 갱신 (해당 Task가 완료 조건이면)

### E-2. Phase 1
- [ ] DRIFT_INVENTORY.md 갱신이 의도한 섹션만 (다른 Task 섹션 침범 0)
- [ ] 등급 분류·결정안이 PRINCIPLES §3 규칙 따름

### E-3. Phase 2
- [ ] SMOKE_TESTS 수동 재실행 + 실행 이력 표 갱신 (Phase 2 마일스톤 PR)
- [ ] API surface 변경 시 `md/handover_log.md` API 표 + `src/types/<domain>.ts` 갱신 (PLAN §Phase 2)
- [ ] DROP COLUMN 시 2단계 분리 확인 (코드 사용 제거 PR → 다음 PR에서 컬럼 삭제)
- [ ] Cross-cutting(2A) 완료 후에 도메인(2B/2C) 진입했는지

### E-4. Phase 3
- [ ] CI warn 카운트(`: any`, debug print 등) 감소 또는 유지
- [ ] 네이밍·매직 값 정리가 작업지시서 범위 안

### E-5. Phase 5
- [ ] Docker 풀 스택 기동 확인
- [ ] gc.md 검사 모두 통과 (또는 통과 못 한 항목이 DECISIONS.md에 기록)

### E-6. Phase 6
- [ ] 실제 코드와 문서 일치 (추측 0)
- [ ] 마이그레이션 노트가 별도 섹션
- [ ] `docs/_archive/` 인용 0건

## F. 검수 결과 기록 양식

PR description 또는 review comment에 다음 형식으로:

```
[REVIEW] 통과 / 보류 / 거부

A: ✅ 모두 통과
B-1: ✅ 동작 보존 확인
C-1: ✅ 오케스트레이터, spot check 통과
D: ✅ INV-02·05 해당 검사 OK
E-2: ✅ Phase 1 갱신, INV 섹션만 수정

비고:
- <보류·거부 사유 또는 발견된 이슈>
```

머지 가능: 모두 ✅ 또는 의도적 예외에 사유 명기.
머지 불가: 한 항목이라도 ❌ 또는 검토 미완.
```

### 6-2. 파일 위치 확인

```bash
ls -la docs/refactor/REVIEW_CHECKLIST.md
```

존재 + 위 내용 그대로면 OK.

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — 단일 신규 파일. §6-1 본문 그대로 transcription. 모델 모호성 0.

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `docs/refactor/REVIEW_CHECKLIST.md` 신규 생성
- [ ] §A~§F 6개 섹션 모두 포함 (공통·Task type·환각·INV·Phase·기록)
- [ ] §6-1 본문과 결과 파일 1:1 대응 (임의 추가·삭제·요약 0)
- [ ] 코드 변경 0줄
- [ ] 다른 파일 수정 없음
- [ ] CI(refactor-guard) 통과

## 9. PR 정보

- **Branch:** `refactor/07-add-review-checklist`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-07] REVIEW_CHECKLIST.md 추가`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 07](../docs/refactor/tasks/07-add-review-checklist.md)

  ## 변경 요약
  - `docs/refactor/REVIEW_CHECKLIST.md` 신규
  - 모든 PR 공통 + Task type별 + 환각 점검 + INV 사후 점검 + Phase별 항목 통합

  ## 효과
  - PRINCIPLES §11이 명시한 검수 도구 부재 해소
  - Task 00 PR부터 동일 기준으로 검수 가능

  ## HARD LIMIT 준수
  - 변경 파일: 신규 1개
  - 코드 변경 0줄

  ## 검수 (자가 적용)
  - A: ✅ docs: 커밋, base refactor/main, 단일 파일
  - B-3: ✅ 추측 0, 실제 §11 명세 기반
  - C-3: ✅ §6-1 본문과 결과 1:1
  ```
