# Team Blackbox — 개발 우선순위 & TODO

## 현재 Phase: MVP (1~8주차)

---

## Phase 1: 기반 구축 (1~2주) ✅ 완료

### 🔴 P0 — 프로젝트 초기화
- [x] Spring Boot 프로젝트 생성 (Java 17, Gradle)
- [x] Next.js 프로젝트 생성 (App Router, TypeScript)
- [x] PostgreSQL Docker 설정 (MySQL → PostgreSQL로 변경)
- [x] 환경변수 설정 (.env, application.yml)
- [ ] GitHub 리포지토리 생성 & 브랜치 전략 확정
- [ ] CI/CD 기본 설정 (GitHub Actions)

### 🔴 P0 — DB 스키마 v1 배포 (Flyway V1~V9)
- [x] `users` 테이블 생성 (V1)
- [x] `projects` + `project_members` 테이블 생성 (V2)
- [x] `tasks` + `task_assignees` 테이블 생성 (V3)
- [x] `meetings` + `meeting_attendees` 테이블 생성 (V4)
- [x] `activity_logs` 테이블 생성 (V5)
- [x] `file_vault` + `tamper_detection_log` 테이블 생성 (V6)
- [x] `contribution_scores` + `alerts` + `weight_configs` 테이블 생성 (V7)
- [x] 타임스탬프 자동 갱신 트리거 (V8)
- [x] `file_vault` immutable 트리거 (BEFORE UPDATE/DELETE) (V9)
- [x] Docker Compose 4-container 구성 (db + backend + frontend + nginx)
- **참조:** `docs/database.md`

---

## Phase 2: 인증 & 프로젝트 관리 (3~4주) ✅ 백엔드 완료

### 🔴 P0 — 백엔드: 인증 ✅
- [x] User 엔티티 & Repository
- [x] 회원가입 API (`POST /api/auth/signup`)
- [x] 로그인 API (`POST /api/auth/login`) — JWT 발급
- [x] JWT 필터 & SecurityConfig (stateless)
- [x] Refresh Token 로직 (`POST /api/auth/refresh`)
- [x] 역할 기반 접근 제어 (STUDENT / PROFESSOR / TA)
- **참조:** `backend/modules/auth.md`

### 🔴 P0 — 백엔드: 프로젝트 관리 ✅
- [x] Project CRUD API
- [x] 초대 코드 생성 & 참여 API
- [x] 멤버 관리 (역할 변경, 탈퇴)
- [x] 데이터 수집 동의 기록 API
- **참조:** `backend/modules/project.md`

### 🟡 P1 — 프론트엔드: 인증 & 프로젝트
- [x] 로그인/회원가입 페이지 (다크/라이트 토글 포함)
- [ ] JWT 토큰 관리 (Zustand store)
- [ ] Axios 인터셉터 (토큰 자동 첨부, 갱신)
- [ ] 프로젝트 목록 페이지
- [ ] 프로젝트 생성 모달
- [ ] 초대 코드 참여 페이지
- [ ] 동의 플로우 온보딩 UI
- **참조:** `frontend/pages/auth.md`, `frontend/pages/dashboard.md`

---

## Phase 3: 칸반 & 회의록 (5~6주) ✅ 백엔드 완료

### 🔴 P0 — 백엔드: 태스크 ✅
- [x] Task CRUD API (생성/수정/삭제/목록)
- [x] 상태 변경 API (TODO → IN_PROGRESS → DONE)
- [x] 담당자 배정 API
- [x] 태스크 이벤트 → `activity_logs` 자동 기록
- **참조:** `backend/modules/task.md`

### 🔴 P0 — 백엔드: 회의록 ✅
- [x] Meeting CRUD API
- [x] 체크인 코드 생성 & 체크인 API
- [x] 회의록 작성/수정 API
- [x] 액션 아이템 → 태스크 생성 연결
- [x] 회의 이벤트 → `activity_logs` 자동 기록
- **참조:** `backend/modules/meeting.md`

### 🟡 P1 — 프론트엔드: 칸반 보드
- [ ] 3단 칼럼 레이아웃 (To Do / In Progress / Done)
- [ ] 태스크 카드 컴포넌트
- [ ] 드래그 앤 드롭 (@dnd-kit)
- [ ] 태스크 생성/편집 모달
- [ ] 필터 (담당자, 태그, 우선순위)
- **참조:** `frontend/pages/board.md`

### 🟡 P1 — 프론트엔드: 회의록
- [ ] 회의 목록 페이지
- [ ] 회의 상세 (참석자, 내용, 결정사항)
- [ ] 체크인 UI (QR 표시 or 링크)
- [ ] 액션 아이템 → 태스크 생성 버튼
- **참조:** `frontend/pages/meeting.md`

---

## Phase 4: Hash Vault & Score Engine (5~7주, Phase 3과 병행) ✅ 백엔드 완료

### 🔴 P0 — 백엔드: Hash Vault ✅
- [x] 파일 업로드 API (로컬 스토리지 `/data/uploads/`)
- [x] SHA-256 해시 생성 & `file_vault` INSERT
- [x] 재업로드 시 해시 비교 로직 (버전 관리)
- [x] 변조 감지 시 `tamper_detection_log` + `alerts` 기록
- [x] 파일 이력 조회 API / 다운로드 API
- **참조:** `backend/modules/vault.md`

### 🔴 P0 — 백엔드: Score Engine (MVP 버전) ✅
- [x] 플랫폼 활동 로그 기반 점수 산출
- [x] 팀 평균 기준 정규화 (상한 150 클리핑)
- [x] 항목별 점수 계산 (Git/문서/회의/태스크)
- [x] 종합 점수 = Σ(항목 × 가중치)
- [x] 점수 재계산 스케줄러 (30분 간격)
- [x] 기본 가중치 설정 (w1=0.30, w2=0.25, w3=0.20, w4=0.25)
- **참조:** `backend/modules/score.md`

### 🔴 P0 — 백엔드: 경보 시스템 (규칙 기반) ✅
- [x] FREE_RIDE 감지 (점수 < 팀 평균 60%)
- [x] DROPOUT 감지 (2주 연속 활동 없음)
- [x] OVERLOAD 감지 (1인이 전체 60% 이상)
- [x] 경보 생성 → `alerts` 테이블 (중복 방지 포함)
- **참조:** `backend/modules/alert.md`

### 🟡 P1 — 프론트엔드: 파일 & 점수
- [ ] 파일 업로드 UI
- [ ] 파일 이력 뷰 (Hash Vault 타임라인)
- [ ] 내 기여도 요약 카드
- **참조:** `frontend/pages/vault.md`, `frontend/pages/analytics.md`

---

## Phase 5: 교수 대시보드 & MVP 마무리 (7~8주) 🔄 진행 중

### 🔴 P0 — 프론트엔드: 메인 대시보드 (다음 작업)
- [ ] 프로젝트 목록 페이지 (로그인 후 랜딩)
- [ ] 프로젝트 생성 모달
- [ ] 초대 코드 참여 페이지
- [ ] JWT Zustand store + Axios 인터셉터

### 🔴 P0 — 프론트엔드: 교수 대시보드
- [ ] 팀 목록 오버뷰 (카드 뷰)
- [ ] 팀 상세: 기여도 바 차트 (Recharts)
- [ ] 프로젝트 진행률 표시
- [ ] 경보 뱃지 & 목록
- [ ] 건강도 지표 (🟢🟡🟠🔴)
- **참조:** `frontend/pages/professor.md`

### 🔴 P0 — 통합 & 중간발표 준비
- [ ] 프론트-백 통합 테스트
- [ ] 데모 시나리오 리허설
- [ ] 버그 수정 & 안정화
- [ ] **8주차 중간발표 데모:**
  1. 프로젝트 생성 → 팀원 초대
  2. 칸반 태스크 생성/완료
  3. 회의록 작성 + 체크인
  4. 파일 업로드 → 해시 고정 시연
  5. 교수 대시보드에서 기여도 확인
  6. (보너스) 해시 변조 감지 데모

---

## Phase 6: 확장 1 — 외부 연동 (9~12주)

### 🟡 P1 — GitHub App 연동
- [ ] GitHub App 등록 & 설정
- [ ] Installation webhook 수신 엔드포인트
- [ ] Push/PR/Issue webhook 파싱
- [ ] 커밋 데이터 → `activity_logs` (source: GITHUB)
- [ ] 폴백 폴링 (30분 보정)
- **참조:** `backend/modules/collector.md`

### 🟡 P1 — Google Drive 연동
- [ ] Google OAuth 2.0 연동
- [ ] Drive Push Notification (Changes: watch)
- [ ] Revision history 수집
- [ ] 댓글 수집
- [ ] 데이터 → `activity_logs` (source: GOOGLE_DRIVE)
- **참조:** `backend/modules/collector.md`

### 🟡 P1 — Score Engine 확장
- [ ] 외부 데이터 통합 점수 산출
- [ ] Git 기여 점수 세부 수식
- [ ] 문서 기여 점수 세부 수식
- [ ] 신뢰도 가중치 적용 (자동 1.0, 수동 0.7)
- **참조:** `backend/modules/score.md`

### 🟡 P1 — 프론트: 확장 UI
- [ ] 외부 연동 설정 페이지
- [ ] 활동 타임라인 (소스별 색상 구분)
- [ ] 교수 상세 대시보드 확장
- **참조:** `frontend/pages/analytics.md`

---

## Phase 7: 확장 2 — AI & 고도화 (13~15주, 시간 허용 시)

### 🟢 P2 — AI Analyzer
- [ ] Claude API 연동
- [ ] 커밋 품질 분석 (배치 10건 단위)
- [ ] quality_score 산출 & `activity_logs` 업데이트
- [ ] Anti-Gaming 로직 (통계 이상치 + AI 교차검증)
- [ ] AI 분석 동의 플로우 (Step 4)
- **참조:** `backend/modules/analyzer.md`

### 🟢 P2 — 피어리뷰 & 리포트
- [ ] 피어리뷰 제출/결과 API
- [ ] 시스템 점수 vs 피어리뷰 크로스체크
- [ ] PDF 리포트 자동 생성
- [ ] 교수 가중치 조정 UI (슬라이더 + 프리셋)
- [ ] 가중치 변경 이력

### 🟢 P2 — 수동 작업 신고
- [ ] 수동 작업 신고 API & UI
- [ ] 신뢰도 0.7 적용
- [ ] 교수 대시보드에서 수동/자동 구분 표시

---

## 우선순위 범례

| 레벨 | 의미 | 시점 |
|------|------|------|
| 🔴 P0 | MVP 필수 — 이것 없으면 중간발표 불가 | 1~8주 |
| 🟡 P1 | 핵심 차별화 — 외부 연동으로 제품 완성도 확보 | 9~12주 |
| 🟢 P2 | 고도화 — 시간 허용 시 추가, 없어도 시스템 동작 | 13~15주 |

---

## 의존성 그래프

```
DB 스키마 ──→ 백엔드 인증 ──→ 프로젝트 API ──→ 태스크 API ──→ Score Engine
                                    │              │
                                    ↓              ↓
                              프론트 인증     칸반 보드 UI
                                    │              │
                                    ↓              ↓
                              프로젝트 UI    회의록 API/UI
                                                   │
                                                   ↓
                                            Hash Vault ──→ 파일 이력 UI
                                                   │
                                                   ↓
                                            교수 대시보드 ──→ [MVP 완성]
                                                   │
                                                   ↓
                                         [확장 1] GitHub/Drive 연동
                                                   │
                                                   ↓
                                         [확장 2] AI Analyzer, 피어리뷰
```
