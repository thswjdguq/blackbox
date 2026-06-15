# Smoke Test 시나리오 (수동 회귀 검증)

> 자동 테스트가 부족한 환경의 회귀 안전망. Phase 0 종료, Phase 2/3 종료, Phase 5 마감 시점에 실행.
> 실행 결과는 본 문서 마지막의 "실행 이력" 표에 기록.
>
> **인증 방식 메모 (현행):** 인증은 **HttpOnly 쿠키** 기반이다(`accessToken`·`refreshToken`을
> `Set-Cookie`로 발급, 프론트는 `withCredentials: true`로 자동 전송 — Task 71에서 localStorage
> JWT 방식에서 전환됨). 토큰은 JS에서 읽히지 않으므로 DevTools → Application → **Cookies**에서 확인한다.

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

## 시나리오 S1 — 회원가입 + 로그인 + 쿠키 인증 발급

### 사전 조건
- 새로 기동된 스택 (DB 비어있음)

### 단계
1. http://localhost/signup 접속 → 회원가입 폼 작성 (test1@example.com / Test1234! / 이름)
2. 가입 성공 → 자동 로그인 또는 로그인 페이지로 리다이렉트
3. 로그인 페이지에서 같은 자격증명으로 로그인
4. DevTools Network 탭에서 `/api/auth/login` 응답 헤더 확인
5. 응답에 `Set-Cookie: accessToken=...; HttpOnly` + `Set-Cookie: refreshToken=...; HttpOnly` 확인
6. DevTools → Application → **Cookies** (localhost)에 `accessToken`·`refreshToken`이 **HttpOnly**로 저장됐는지 확인 (localStorage에는 토큰이 **없어야** 정상 — Task 71)

### 예상 결과
- 회원가입 201, 로그인 200. 응답에 두 개의 HttpOnly `Set-Cookie`
- 로그인 후 `/dashboard` 또는 `/projects`로 리다이렉트
- 후속 요청은 쿠키로 자동 인증 (요청에 `Authorization: Bearer` 헤더는 사용하지 않음, `withCredentials`로 쿠키 자동 첨부)

### 실패 시 보고
- 어느 단계에서 실패했는지
- 응답 코드·메시지, `Set-Cookie` 유무
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
- AI 액션아이템 추출은 키 부재 시 `IllegalStateException`(키 안내 메시지). AI 키가 없는 환경에서는 "수동 추가" 경로로 검증 (Task 27 AiService 폴백 동작 보존 확인 겸용)

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
| (미실행) | — | — | — | — | — | — | — | — | — | Task 06에서 Phase 0 baseline 1회 실행 예정 |

각 셀: ✅ pass / ❌ fail (issue link) / ⏭ skipped (사유)
새 실행마다 행 추가. 표는 위에서 아래로 시간순.

---

## 시나리오 추가/수정 정책
- 시나리오 추가는 별도 PR로. 본 문서 자체는 `docs:` 커밋
- 동작 명세가 바뀌면(예: API 응답 코드 변경, 인증 방식 변경) 이력 표 위에 "<날짜> 시나리오 SX 갱신: 사유" 1줄 추가
