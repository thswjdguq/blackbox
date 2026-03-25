# Team Blackbox — 개발 우선순위 & TODO

## 현재 Phase: MVP (1~8주차)

---

## Phase 1: 인프라 & 기반 구축 (1~2주)

### 🔴 P0 — Docker Compose 인프라 셋업
- [ ] 프로젝트 루트 `docker-compose.yml` 작성
- [ ] PostgreSQL 16 컨테이너 설정 (`db` 서비스)
- [ ] `pgdata` 볼륨 (DB 영속), `uploads` 볼륨 (파일 저장)
- [ ] Spring Boot Dockerfile 작성 (multi-stage 빌드)
- [ ] Next.js Dockerfile 작성 (standalone 빌드)
- [ ] Nginx 설정 (`/` → frontend, `/api` → backend, `/uploads` → 정적)
- [ ] `docker compose up -d` 로 전체 스택 기동 확인
- [ ] `.env` 환경변수 파일 구성 (DB 비밀번호, JWT 시크릿 등)
- **참조:** `docs/docker.md`

### 🔴 P0 — 프로젝트 초기화
- [ ] Spring Boot 프로젝트 생성 (Java 17, Gradle)
- [ ] Next.js 프로젝트 생성 (App Router, TypeScript)
- [ ] GitHub 리포지토리 생성 & 브랜치 전략 확정
- [ ] CI/CD 기본 설정 (GitHub Actions — Docker 빌드 확인)

### 🔴 P0 — DB 스키마 v1 배포 (Flyway)
- [ ] Flyway 의존성 추가 & 설정
- [ ] `V1__init_users.sql` — users 테이블
- [ ] `V2__init_projects.sql` — projects, project_members 테이블
- [ ] `V3__init_tasks.sql` — tasks, task_assignees 테이블
- [ ] `V4__init_meetings.sql` — meetings, meeting_attendees 테이블
- [ ] `V5__init_activity_logs.sql` — activity_logs 테이블 + 인덱스
- [ ] `V6__init_file_vault.sql` — file_vault + immutable 트리거 + tamper_detection_log
- [ ] `V7__init_scores_alerts.sql` — contribution_scores, alerts 테이블
- [ ] `docker compose up` 시 Flyway 자동 마이그레이션 확인
- **참조:** `docs/database.md`

---

## Phase 2: 인증 & 프로젝트 관리 (3~4주)

### 🔴 P0 — 백엔드: 인증
- [ ] User 엔티티 & Repository
- [ ] 회원가입 API (`POST /api/auth/signup`)
- [ ] 로그인 API (`POST /api/auth/login`) — JWT 발급
- [ ] JWT 필터 & SecurityConfig
- [ ] Refresh Token 로직
- [ ] 역할 기반 접근 제어 (STUDENT / PROFESSOR / TA)
- **참조:** `backend/modules/auth.md`

### 🔴 P0 — 백엔드: 프로젝트 관리
- [ ] Project CRUD API
- [ ] 초대 코드 생성 & 참여 API
- [ ] 멤버 관리 (역할 변경, 탈퇴)
- [ ] ProjectAccessChecker (LEADER/MEMBER/OBSERVER 권한 검증)
- [ ] 데이터 수집 동의 기록 API
- **참조:** `backend/modules/project.md`

### 🟡 P1 — 프론트엔드: 인증 & 프로젝트
- [ ] 로그인/회원가입 페이지
- [ ] JWT 토큰 관리 (Zustand store)
- [ ] Axios 인터셉터 (토큰 자동 첨부, 갱신)
- [ ] 프로젝트 목록 페이지
- [ ] 프로젝트 생성 모달
- [ ] 초대 코드 참여 페이지
- [ ] 동의 플로우 온보딩 UI
- **참조:** `frontend/pages/auth.md`, `frontend/pages/dashboard.md`

---

## Phase 3: 칸반 & 회의록 (5~6주)

### 🔴 P0 — 백엔드: 태스크
- [ ] Task CRUD API (생성/수정/삭제/목록)
- [ ] 상태 변경 API (TODO → IN_PROGRESS → DONE)
- [ ] 담당자 배정 API
- [ ] 태스크 이벤트 → `activity_logs` 자동 기록
- **참조:** `backend/modules/task.md`

### 🔴 P0 — 백엔드: 회의록
- [ ] Meeting CRUD API
- [ ] 체크인 코드 생성 & 체크인 API
- [ ] 회의록 작성/수정 API
- [ ] 액션 아이템 → 태스크 생성 연결
- [ ] 회의 이벤트 → `activity_logs` 자동 기록
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

## Phase 4: Hash Vault & Score Engine (5~7주, Phase 3과 병행)

### 🔴 P0 — 백엔드: Hash Vault (로컬 파일 저장)
- [ ] FileStorageService (로컬 디스크 저장: `/data/uploads/{projectId}/`)
- [ ] SHA-256 해시 생성 (HashService)
- [ ] 파일 업로드 API (`POST /projects/:id/files` — multipart)
- [ ] `file_vault` INSERT + 버전 관리
- [ ] 재업로드 시 해시 비교 로직
- [ ] 파일 다운로드 API (`GET /files/:id/download` — 스트리밍)
- [ ] 변조 감지 시 `tamper_detection_log` + `alerts` 기록
- [ ] 파일 이력 조회 API
- **참조:** `backend/modules/vault.md`

### 🔴 P0 — 백엔드: Score Engine (MVP 버전)
- [ ] 플랫폼 활동 로그 기반 점수 산출
- [ ] 팀 평균 기준 정규화 (상한 150 클리핑)
- [ ] 항목별 점수 계산 (태스크/회의/파일)
- [ ] 종합 점수 = Σ(항목 × 가중치)
- [ ] 점수 재계산 스케줄러 (30분 간격 or 이벤트 트리거)
- [ ] 기본 가중치 설정 (w1=0.30, w2=0.25, w3=0.20, w4=0.25)
- **참조:** `backend/modules/score.md`

### 🔴 P0 — 백엔드: 경보 시스템 (규칙 기반)
- [ ] 불균형 감지 (점수 편차 > 40%)
- [ ] 이탈 감지 (2주 연속 활동 없음)
- [ ] 과부하 감지 (1인이 60% 이상)
- [ ] 경보 생성 → `alerts` 테이블
- **참조:** `backend/modules/alert.md`

### 🟡 P1 — 프론트엔드: 파일 & 점수
- [ ] 파일 업로드 UI
- [ ] 파일 이력 뷰 (Hash Vault 타임라인)
- [ ] 내 기여도 요약 카드
- **참조:** `frontend/pages/vault.md`, `frontend/pages/analytics.md`

---

## Phase 5: 교수 대시보드 & MVP 마무리 (7~8주)

### 🔴 P0 — 프론트엔드: 교수 대시보드
- [ ] 팀 목록 오버뷰 (카드 뷰)
- [ ] 팀 상세: 기여도 바 차트 (Recharts)
- [ ] 프로젝트 진행률 표시
- [ ] 경보 뱃지 & 목록
- [ ] 건강도 지표 (🟢🟡🟠🔴)
- **참조:** `frontend/pages/professor.md`

### 🔴 P0 — Docker 배포 안정화
- [ ] Docker Compose production 프로필 구성
- [ ] Nginx SSL 설정 (self-signed for demo)
- [ ] `docker compose -f docker-compose.prod.yml up -d` 검증
- [ ] 데모 환경에서 전체 플로우 동작 확인

### 🔴 P0 — 통합 & 중간발표 준비
- [ ] 프론트-백 통합 테스트
- [ ] 데모 시나리오 리허설
- [ ] 버그 수정 & 안정화
- [ ] **8주차 중간발표 데모:**
  1. `docker compose up` 으로 전체 스택 기동 시연
  2. 프로젝트 생성 → 팀원 초대
  3. 칸반 태스크 생성/완료
  4. 회의록 작성 + 체크인
  5. 파일 업로드 → 해시 고정 시연
  6. 교수 대시보드에서 기여도 확인
  7. (보너스) 해시 변조 감지 데모

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

---

## Phase 7: 확장 2 — AI & 고도화 (13~15주, 시간 허용 시)

### 🟢 P2 — AI Analyzer
- [ ] Claude API 연동
- [ ] 커밋 품질 분석 (배치 10건 단위)
- [ ] quality_score 산출 & `activity_logs` 업데이트
- [ ] Anti-Gaming 로직
- [ ] AI 분석 동의 플로우 (Step 4)
- **참조:** `backend/modules/analyzer.md`

### 🟢 P2 — 피어리뷰 & 리포트
- [ ] 피어리뷰 제출/결과 API
- [ ] 시스템 점수 vs 피어리뷰 크로스체크
- [ ] PDF 리포트 자동 생성
- [ ] 교수 가중치 조정 UI (슬라이더 + 프리셋)
- [ ] 가중치 변경 이력

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
Docker Compose ──→ DB 스키마 (Flyway) ──→ 백엔드 인증 ──→ 프로젝트 API
     │                                          │              │
     ↓                                          ↓              ↓
  Nginx 설정                              프론트 인증     태스크 API
                                                │              │
                                                ↓              ↓
                                          프로젝트 UI    칸반 보드 UI
                                                               │
                                                          회의록 API/UI
                                                               │
                                                          Hash Vault
                                                         (로컬 파일 저장)
                                                               │
                                                          Score Engine
                                                               │
                                                          교수 대시보드
                                                               │
                                                         [MVP 완성] ←─ Docker 배포 안정화
                                                               │
                                                    [확장 1] GitHub/Drive 연동
                                                               │
                                                    [확장 2] AI Analyzer, 피어리뷰
```
