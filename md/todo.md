# Team Blackbox — 개발 우선순위 & TODO

## 현재 Phase: MVP 완성 + AI/Notion 확장 (진행 중)

> **업데이트 2026-04-03:** MVP 핵심 기능 완성. 이제 AI·Notion 연동으로 차별화 강화.

---

## Phase 1: 인프라 & 기반 구축 ✅

### 🔴 P0 — Docker Compose 인프라 셋업
- [x] 프로젝트 루트 `docker-compose.yml` 작성
- [x] PostgreSQL 16 컨테이너 설정 (`db` 서비스)
- [x] `pgdata` 볼륨 (DB 영속), `uploads` 볼륨 (파일 저장)
- [x] Spring Boot Dockerfile 작성 (multi-stage 빌드)
- [x] Next.js Dockerfile 작성 (standalone 빌드)
- [x] Nginx 설정 (`/` → frontend, `/api` → backend, `/uploads` → 정적)
- [x] `docker compose up -d` 전체 스택 기동 확인 ✅
- [x] `.env` 환경변수 파일 구성 (DB 비밀번호, JWT 시크릿 등)

### 🔴 P0 — DB 스키마 v1 배포 (Flyway)2
- [x] V1~V9 마이그레이션 전체 완료 (users / projects / tasks / meetings / logs / vault / scores / triggers)

---

## Phase 2: 인증 & 프로젝트 관리 ✅

### 🔴 P0 — 백엔드: 인증
- [x] 회원가입/로그인 API, JWT 발급, Refresh Token
- [x] JWT 필터 & SecurityConfig
- [ ] 역할 기반 접근 제어 (PROFESSOR/TA 분기 로직) — 미구현

### 🔴 P0 — 백엔드: 프로젝트 관리
- [x] Project CRUD, 초대코드, 멤버 관리, ProjectAccessChecker

### 🟡 P1 — 프론트엔드: 인증 & 프로젝트
- [x] 로그인/회원가입, JWT Zustand store, Axios 인터셉터
- [x] 프로젝트 목록/생성/삭제/참여 (LEADER 전용 삭제 모달)
- [ ] 동의 플로우 온보딩 UI

---

## Phase 3: 칸반 & 회의록 ✅

### 🔴 P0 — 백엔드
- [x] Task CRUD, 상태 변경, 담당자 배정, activity_logs 기록
- [x] Meeting CRUD, 체크인 코드, 회의록 작성, 액션아이템 → 태스크

### 🔴 P0 — 프론트엔드: 칸반 보드
- [x] 3단 칼럼, 드래그앤드롭(@dnd-kit), 태스크 카드, 생성/편집/삭제 모달
- [ ] 필터 (담당자/태그/우선순위)

### 🟡 P1 — 프론트엔드: 회의록
- [x] 회의 목록 페이지 (월별 타임라인)
- [x] 회의 상세 (체크인 코드, 참석자, 회의록 편집, 결정사항)
- [x] 체크인 코드 UI (대형 표시 + 재생성)
- [x] 액션아이템 → 태스크 생성 (수동 + **AI 자동 추출** ✨)
- [x] AI 회의 요약 버튼 (Claude API) ✨

---

## Phase 4: Hash Vault & Score Engine ✅

### 🔴 P0 — 백엔드
- [x] FileStorageService, SHA-256 HashService, 파일 업로드/다운로드/버전관리/변조감지
- [x] Score Engine (30분 스케줄러, 항목별 점수, 팀 평균 정규화, 수동 재계산)
- [x] 경보 시스템 (FREE_RIDE / DROPOUT / OVERLOAD)

### 🟡 P1 — 프론트엔드
- [x] Hash Vault 페이지 (파일 업로드/다운로드/이력/해시 표시)
- [x] 기여도 분석 페이지 (KPI 카드, 인사이트, 멤버 순위, 차트 3종, 경보 표시)
- [x] 점수 재계산 버튼

---

## Phase 5: 리포트 & 배포 안정화 (진행 중)

### 🔴 P0 — 무결성 PDF 리포트
- [x] ReportService (기여도/태스크/Vault/경보 + 행별 SHA-256 + 리포트 해시)
- [x] `GET /api/projects/:id/report` → PDF 스트리밍 다운로드
- [x] 기여도 분석 페이지 "리포트 발급" 버튼

### 🔴 P0 — Docker 배포 안정화
- [x] Docker Compose production 프로필 구성 (`docker-compose.prod.yml`)
- [x] Nginx SSL 설정 (`nginx/nginx-ssl.conf`, self-signed, `ssl/gen-certs.sh`)
- [ ] `docker compose -f docker-compose.prod.yml up -d` 검증 (Docker 재기동 후)

### 🔴 P0 — 통합 & 중간발표 준비
- [ ] 프론트-백 통합 테스트
- [ ] 데모 시나리오 리허설
- [ ] **8주차 중간발표 데모 시나리오:**
  1. `docker compose up` 으로 전체 스택 기동 시연
  2. 프로젝트 생성 → 팀원 초대
  3. 칸반 태스크 생성/완료
  4. 회의록 작성 + 체크인 + **AI 요약/액션아이템 추출** ✨
  5. 파일 업로드 → 해시 고정 시연
  6. 기여도 분석 + **무결성 PDF 발급** ✨
  7. (보너스) 해시 변조 감지 데모

---

## Phase 5.5: AI & Notion 연동 차별화 기능 🚀

> **우선순위 기준:** 난이도 vs 임팩트 (교수 설득력 + 학생 체감) 분석 결과

### 🔴 P0 — 1순위: 회의록 → Notion 자동 정리 ✅
> "블랙박스만 쓰면 노션 문서가 쌓인다" — 가장 강력한 차별화 스토리
- [x] 백엔드: `NotionService` (WebClient → Notion API POST /v1/pages)
- [x] 백엔드: `POST /api/projects/:id/meetings/:mid/notion/export` 엔드포인트
- [x] Claude로 회의록 → Notion 페이지 형식(헤더/요약/결정사항/액션아이템) 변환
- [x] 프론트: 회의 상세 페이지 "Notion으로 내보내기" 버튼
- [x] `.env`: `NOTION_API_KEY`, `NOTION_PARENT_PAGE_ID` 추가

### 🟡 P1 — 2순위: 역할 불균형 감지 UI 강화 ✅
> 백엔드 FREE_RIDE/OVERLOAD 경보 이미 있음 — UI만 강화하면 됨
- [x] 기여도 분석 페이지: 멤버별 역할별 기여 분포 (스택 가로 바)
- [x] 멤버별 역할 편중도 시각화 (태스크/회의/파일/Git 비율 막대)

### 🟡 P1 — 3순위: 마감 지연 위험도 예측 ✅
> 규칙 기반 (완료율 / 지연 태스크 / 남은 일수 / 최근 활동)
- [x] 백엔드: `RiskService` + `GET /api/projects/:id/risk` (점수 0~100, 레벨, 이유)
- [x] 프론트: 기여도 분석 페이지 — 위험도 게이지 (RadialBarChart) + 통계

### 🟡 P1 — 4순위: 칸반 태스크 → Notion 내보내기 ✅
> 회의록 Notion 연동 구현 후 자연스럽게 확장
- [x] 백엔드: `POST /api/projects/:id/tasks/notion/sync` — 전체 태스크 Notion 페이지 스냅샷
- [x] 상태별(TODO/IN_PROGRESS/DONE) 그룹화, 우선순위 이모지, 담당자 표시
- [x] 프론트: 칸반 보드 상단 "Notion" 버튼 + 생성된 페이지 URL 표시

### 🟢 P2 — 5순위: 팀플 증거 패키지 자동 생성
> 기존 PDF 리포트 확장 — 프로젝트 종료 시 종합 증빙 ZIP
- [ ] 회의록 전체 + 기여도 PDF + Hash Vault 이력을 하나의 ZIP으로 묶기
- [ ] `GET /api/projects/:id/evidence-package` → ZIP 다운로드
- [ ] 프론트: 대시보드 "팀플 종료 — 증거 패키지 발급" 버튼

### 🟢 P2 — 6순위: 회의 시간 자동 조율 + Google Calendar 등록
- [ ] 팀원 가능 시간 입력 UI (타임슬롯 그리드)
- [ ] 겹치는 시간 자동 추천 알고리즘
- [ ] Google Calendar API 연동 → 일정 생성

### 🟢 P2 — 7순위: 발표자료 변경점 자동 추적
> Google Drive OAuth 필요 — 난이도 상
- [ ] Drive 파일 버전 이력 수집
- [ ] 버전별 슬라이드 수/제목 비교 요약

---

## Phase 6: 확장 1 — 외부 연동 (9~12주)

### 🟡 P1 — GitHub App 연동
- [ ] GitHub App 등록, Webhook 수신, 커밋/PR/Issue → `activity_logs`

### 🟡 P1 — Google Drive 연동
- [ ] Google OAuth 2.0, Drive Push Notification, Revision history → `activity_logs`

### 🟡 P1 — Score Engine 확장
- [ ] 외부 데이터(GitHub/Drive) 통합 점수 산출, 신뢰도 가중치

---

## Phase 7: 확장 2 — AI 고도화 (13~15주)

### 🟢 P2 — AI Analyzer
- [ ] 커밋 품질 분석 (Claude API), quality_score 산출, Anti-Gaming

### 🟢 P2 — 피어리뷰
- [ ] 피어리뷰 제출/결과 API, 시스템 점수 크로스체크, 교수 가중치 조정 UI

---

## 📌 추가 구현 사항 (계획 외 완료)

### 백엔드
- [x] WeightConfig 엔티티 — 프로젝트별 가중치 커스터마이징
- [x] ActivityLogService, TamperDetectionLog, ScoreController, HealthController
- [x] ClaudeService (WebClient) — AI 요약/액션아이템 추출 엔드포인트
- [x] ReportService / ReportController — SHA-256 무결성 PDF
- [x] OpenPDF 의존성 추가

### 프론트엔드
- [x] Sidebar (활성 left-border 스타일, 다크모드, 테마 토글)
- [x] DatePicker (자동탭 + react-day-picker 달력 팝오버)
- [x] PageProgress (상단 로딩바)
- [x] Kanban 컴포넌트 분리 (KanbanBoard / KanbanColumn / TaskCard / TaskModal)
- [x] 글로벌 디자인 토큰 (bb-* CSS 변수, Tailwind 확장, 다크모드)

---

## 우선순위 범례

| 레벨 | 의미 |
|------|------|
| 🔴 P0 | 지금 바로 해야 함 |
| 🟡 P1 | 핵심 차별화 — 빠른 시일 내 |
| 🟢 P2 | 시간 허용 시 추가 |

---

## 현재 작업 순서

```
① Docker 프로덕션 프로필 + SSL  ✅ 완료
② 회의록 → Notion 자동 정리    ✅ 완료
③ 역할 불균형 감지 UI 강화      ✅ 완료
④ 마감 지연 위험도 예측          ✅ 완료
⑤ 칸반 → Notion 동기화          ✅ 완료
⑥ Docker rebuild + deploy       ← Docker Desktop 재시작 후
```
