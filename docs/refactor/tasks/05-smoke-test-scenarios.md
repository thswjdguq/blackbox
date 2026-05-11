# Task 05 — 수동 Smoke Test 시나리오 작성

> **실행 주체: Claude (예외 실행, PRINCIPLES §13).** Copilot 비사용.
> 본 작업지시서 §6-1의 SMOKE_TESTS.md 골격이 완전히 정의되어 있어 Claude가 1회 Write로 transcription 수행. 사용자는 결과 PR 단계에서 검수.

## 1. 한 줄 요약
자동 테스트가 부족한 환경에서 회귀 검증의 안전망 역할을 할 7개 핵심 happy path 시나리오를 `docs/refactor/SMOKE_TESTS.md`에 명세한다. Claude가 본 작업지시서 §6-1을 그대로 옮긴다.

## 2. 변경 범위 (HARD LIMIT)

- **신규 파일:**
  - `docs/refactor/SMOKE_TESTS.md` (신규 생성)
- **수정 파일:** 없음
- **메서드/함수/클래스:** N/A
- **예상 변경 라인 수:** ~250~350줄 (7개 시나리오 + 실행 기록 표)
- **예상 변경 파일 수:** 1개 (신규)

**중요:** 이 Task는 **시나리오 명세 작성만** 함. **실제 시나리오 실행은 별도 작업**(Phase 0 완료 시점 + Phase 2~5 마일스톤마다).

## 3. 절대 건드리지 말 것 (Out of Scope)

- 코드·설정·다른 문서 수정 금지
- 시나리오 실행 결과 채우기 (별도 작업) — 이 Task는 빈 결과 표만 만듦
- 자동 테스트 작성 (본 리팩토링 범위 외)

## 4. 컨텍스트 / 의도

`Task 03 TEST_COVERAGE`에서 자동 테스트 부족이 확인될 가능성 ↑ (특히 프론트엔드는 프레임워크 미설정 추정).

자동 테스트가 없으면 리팩토링 시 회귀를 잡을 그물이 없음. 자동 테스트 추가는 본 리팩토링 범위 외이므로, **수동이지만 표준화된 시나리오**를 명세해 다음 시점마다 일괄 실행:
- Phase 0 종료 (베이스라인 동작 확인)
- Phase 2 종료 (cross-cutting + 도메인 변경 후)
- Phase 3 종료 (품질 개선 후)
- Phase 5 마감 (전체)

각 시점의 결과를 `SMOKE_TESTS.md`의 실행 이력 표에 기록.

- 관련 INV: 해당 없음
- 관련 Drift ID: 해당 없음
- 관련 PRINCIPLES 섹션: §0 (대전제 — 회귀 그물망), §11 (검수)

## 4-1. 의존 관계

- **선행 Task:** 없음 (단, Task 02 baseline + Task 03 coverage가 먼저 있으면 어느 영역이 HIGH 위험인지 알고 시나리오 우선순위 정할 수 있음)
- **후행 Task:** Phase 2~5의 마일스톤 검증
- **API surface 변경 여부:** No

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §10 (문서화 원칙):

- 실제 코드를 읽고 쓴다. 옛 구조나 추측 금지.
- "지금 어떻게 동작한다"로 서술.

> PRINCIPLES.md §11 (검수):

각 PR 머지 전 사용자 또는 Claude가 `docs/refactor/REVIEW_CHECKLIST.md`로 검수한다. 통과 못 하면 머지 금지.

## 6. 작업 절차

### 6-1. `docs/refactor/SMOKE_TESTS.md` 작성

다음 골격으로 작성. 7개 시나리오를 모두 포함하고 각 시나리오는 같은 구조 따름.

```markdown
# Smoke Test 시나리오 (수동 회귀 검증)

> 자동 테스트가 부족한 환경의 회귀 안전망. Phase 0 종료, Phase 2/3 종료, Phase 5 마감 시점에 실행.
> 실행 결과는 본 문서 마지막의 "실행 이력" 표에 기록.

## 사전 준비

```bash
# 전체 스택 기동 (clean state)
docker compose down -v       # 기존 데이터 초기화 (주의: 개발 데이터 사라짐)
docker compose up -d --build
docker compose logs -f backend  # 별도 터미널에서 모니터링
```

확인:
- http://localhost/  → 로그인 페이지 표시
- `docker compose ps`  → db / backend / frontend / nginx 모두 healthy/running

각 시나리오는 깨끗한 상태에서 시작하거나, 명시된 사전 조건 후 시작.

---

## 시나리오 S1 — 회원가입 + 로그인 + JWT 발급

### 사전 조건
- 새로 기동된 스택 (DB 비어있음)

### 단계
1. http://localhost/signup 접속 → 회원가입 폼 작성 (test1@example.com / Test1234! / 이름)
2. 가입 성공 → 자동 로그인 또는 로그인 페이지로 리다이렉트
3. 로그인 페이지에서 같은 자격증명으로 로그인
4. DevTools Network 탭에서 `/api/auth/login` 응답 확인
5. localStorage에 `accessToken`, `refreshToken` 저장 확인 (DevTools → Application → Local Storage)

### 예상 결과
- 회원가입 200 응답, accessToken/refreshToken 발급
- 로그인 후 `/dashboard` 또는 `/projects`로 리다이렉트
- `Authorization: Bearer <token>` 헤더가 후속 요청에 자동 첨부

### 실패 시 보고
- 어느 단계에서 실패했는지
- 응답 코드·메시지
- 콘솔 에러

---

## 시나리오 S2 — 프로젝트 생성 + 초대 코드 + 멤버 참여

### 사전 조건
- S1 완료 (test1 로그인 상태)

### 단계
1. 대시보드에서 "새 프로젝트" 클릭
2. 프로젝트 생성 (이름 "Test Project", 설명, 기간)
3. 생성된 프로젝트 클릭 → 초대 코드 확인 (8자리)
4. 시크릿 창 또는 다른 브라우저에서 test2 회원가입
5. test2 로그인 → "프로젝트 참여" → 초대 코드 입력
6. 두 사용자 모두 멤버 목록에 표시 확인

### 예상 결과
- 프로젝트 생성 200, invite_code 발급
- test2가 LEADER가 아닌 MEMBER로 가입
- 양쪽 대시보드에서 프로젝트 보임

### 실패 시 보고
- 단계·응답·에러

---

## 시나리오 S3 — 칸반 태스크 생성 + 드래그앤드롭 + 삭제

### 사전 조건
- S2 완료 (프로젝트 + 멤버 2명)

### 단계
1. 프로젝트 → 칸반 보드 진입
2. TODO 칼럼에서 "새 태스크" → 제목·우선순위·담당자·마감일·태그 입력
3. 생성된 카드를 IN_PROGRESS로 드래그
4. 새로고침 후 IN_PROGRESS에 카드 유지 확인 (낙관적 업데이트 + 백엔드 저장)
5. 다시 DONE으로 드래그
6. 카드 클릭 → 삭제 (확인 모달 → 삭제)

### 예상 결과
- POST /tasks 201
- PATCH /tasks/:id/status (TODO→IN_PROGRESS, IN_PROGRESS→DONE) 200
- 새로고침 후 상태 유지
- DELETE /tasks/:id 204

### 실패 시 보고
- 어느 전환에서 실패
- 새로고침 후 stale 여부

---

## 시나리오 S4 — 회의 생성 + 체크인 + 회의록 + 액션아이템

### 사전 조건
- S2 완료 (프로젝트 + 멤버 2명)

### 단계
1. 회의록 페이지 진입
2. 새 회의 생성 (제목, 일시, 안건)
3. 체크인 코드 확인 (8자리)
4. test2 계정으로 다른 브라우저에서 체크인 코드 입력 → 체크인
5. 양쪽에서 참석자 목록에 test2 표시 확인
6. 회의록 본문에 결정사항 입력 → 저장 (자동 또는 명시 저장)
7. 액션아이템 영역에서 "수동 추가" 또는 "AI 자동 추출" 시도
8. 추출된 액션아이템 → "태스크 생성" 클릭
9. 칸반에 새 태스크 생성 확인

### 예상 결과
- POST /meetings 201
- POST /meetings/checkin 200
- PATCH /meetings/:id (회의록 저장) 200
- POST /meetings/:id/action-items → 새 태스크 생성

### 알려진 위험 (handover_log §8.1)
- saveNow()가 빈 문자열을 보내 회의록 본문이 사라지는 버그가 있었음 → 회귀 시 즉시 보고

### 실패 시 보고
- 단계·응답·에러

---

## 시나리오 S5 — 파일 업로드 + SHA-256 + 변조 감지

### 사전 조건
- S2 완료

### 단계
1. Hash Vault 페이지 진입
2. 임의 파일 (예: small text file) 드래그 또는 선택 업로드
3. 업로드 완료 후 표시되는 SHA-256 해시 메모
4. 동일 파일을 다시 업로드 → 응답 확인 (변조 감지 X, version 2)
5. 같은 파일명이지만 내용 다른 파일 업로드 → "변경 내역" 또는 "변조 감지" 표시 확인 (text 정확히 확인)
6. 파일 다운로드 → 파일 정상 다운로드

### 예상 결과
- POST /files multipart 201, file_hash 64자리
- 동일 파일 재업로드 시 tamperDetected=false, version=2
- 다른 내용 재업로드 시 tamperDetected=true 또는 변경 내역 표시
- INV-02: file_vault에 UPDATE/DELETE 발생 안 함 (DB 트리거가 막음)

### 실패 시 보고
- 해시 길이가 64 아니면 즉시 보고
- 변조 감지 동작 안 하면 즉시 보고 (핵심 가치)

---

## 시나리오 S6 — 점수 재계산 + 기여도 페이지

### 사전 조건
- S2~S5 완료 (활동 데이터 누적)

### 단계
1. 기여도 분석 페이지 진입
2. "수동 재계산" 버튼 클릭
3. 멤버별 참여 현황 (FULL/PARTIAL/NONE) 표시 확인
4. 막대/레이더 차트 표시 확인
5. 위험도 게이지 (RadialBarChart) 표시 확인

### 예상 결과
- POST /scores/recalculate 200
- 응답 후 페이지 데이터 갱신
- 차트 렌더링 정상

### 실패 시 보고
- BigDecimal 변환 이슈 의심 시 콘솔 확인

---

## 시나리오 S7 — 무결성 PDF + 증거 패키지 ZIP

### 사전 조건
- S2~S6 완료

### 단계
1. 기여도 페이지 → "리포트 발급" 클릭 → PDF 다운로드
2. 다운로드된 PDF 열기 → 참여 여부 테이블·SHA-256 해시·전체 리포트 해시 표시 확인
3. 프로젝트 홈 → "증거 패키지" 또는 "팀플 종료" 버튼 → ZIP 다운로드
4. ZIP 풀어서 회의록·PDF·Vault 이력 포함 확인

### 예상 결과
- GET /report PDF 스트리밍 정상
- GET /evidence-package ZIP 정상
- ZIP 내부에 회의록·기여도 PDF·Hash Vault 이력 포함

### 실패 시 보고
- 단계·파일 누락·해시 검증 실패

---

## 실행 이력

| 일시 | 실행자 | 시점(Phase) | S1 | S2 | S3 | S4 | S5 | S6 | S7 | 비고 |
|---|---|---|---|---|---|---|---|---|---|---|
| 2026-MM-DD | <name> | Phase 0 baseline | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | <link to PR or issue> |

각 셀: ✅ pass / ❌ fail (issue link) / ⏭ skipped (사유)
새 실행마다 행 추가. 표는 위에서 아래로 시간순.

---

## 시나리오 추가/수정 정책
- 시나리오 추가는 별도 PR로. 본 문서 자체는 `chore: docs:` 커밋
- 동작 명세가 바뀌면(예: API 응답 코드 변경) 이력 표 위에 "<날짜> 시나리오 SX 갱신: 사유" 1줄 추가
```

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — Claude 실행. 본 작업지시서 §6-1을 그대로 SMOKE_TESTS.md로 transcription. 모델 모호성 없음.
- 사용자 검수: 결과 파일 머지 전 한 번 통독.

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `docs/refactor/SMOKE_TESTS.md` 신규 생성
- [ ] 7개 시나리오(S1~S7) 모두 포함
- [ ] 각 시나리오에 사전 조건·단계·예상 결과·실패 시 보고 명시
- [ ] 사전 준비 섹션 (docker compose 명령) 포함
- [ ] 실행 이력 표 (빈 골격 + 컬럼 정의) 포함
- [ ] 시나리오 추가/수정 정책 포함
- [ ] 코드 변경 0줄
- [ ] 다른 파일 수정 없음 (HARD LIMIT 준수)
- [ ] CI(refactor-guard) 통과

## 9. PR 정보

- **Branch:** `refactor/05-smoke-test-scenarios`
- **PR base:** `refactor/main`
- **PR title:** `[refactor-05] 수동 Smoke Test 시나리오 7개 명세`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 05](../docs/refactor/tasks/05-smoke-test-scenarios.md)

  ## 변경 요약
  - `docs/refactor/SMOKE_TESTS.md` 신규
  - 7개 happy path 시나리오: 인증 / 프로젝트 / 칸반 / 회의 / Vault / 점수 / PDF·증거 패키지
  - Phase 0/2/3/5 마일스톤마다 수동 실행

  ## 효과
  - 자동 테스트 부족한 환경에서 회귀 안전망
  - 실행 이력이 단일 source로 누적됨

  ## HARD LIMIT 준수
  - 변경 파일: 신규 1개
  - 코드 변경 0줄

  ## Follow-up
  - 본 PR 머지 후 즉시 Phase 0 baseline 1회 실행 (별도 작업)
  ```
