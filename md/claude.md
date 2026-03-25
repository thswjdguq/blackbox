# Team Blackbox — 프로젝트 컨텍스트

## 이 파일의 목적
이 파일은 AI 개발 어시스턴트가 프로젝트를 이해하기 위해 **가장 먼저 읽는 루트 컨텍스트**이다.
세부 구현은 하위 `.md` 파일을 참조한다.

---

## 프로젝트 한 줄 요약
> 대학생 팀 프로젝트에서 "누가 뭘 얼마나 했는지"를 외부 API 로그 + 플랫폼 내부 활동 기반으로 자동 산출하고, 교수 전용 대시보드로 제공하는 EdTech SaaS.

## 설계 철학
> "우리가 데이터를 만들지 않는다. 외부 시스템이 이미 기록한 데이터를 읽어올 뿐이다."

---

## 기술 스택

| 분류 | 기술 | 비고 |
|------|------|------|
| Frontend | Next.js 14+ (App Router, TypeScript) | Docker 컨테이너 (standalone 빌드) |
| Backend | Java 17+ (Spring Boot 3.x) | Docker 컨테이너 |
| Database | **PostgreSQL 16** | Docker 컨테이너, 직접 관리 |
| File Storage | **로컬 파일 시스템** | Docker 볼륨 `/data/uploads/` |
| Auth | JWT (Access 30min + Refresh 7day) | Spring Security |
| External API | GitHub App API, Google Drive API | 확장 1 단계 |
| AI | Claude API (Anthropic) | 확장 2 단계 |
| CSS | Tailwind CSS 3.x | shadcn/ui 컴포넌트 |
| Chart | Recharts | 대시보드 차트 |
| State | Zustand | 클라이언트 상태 관리 |
| DnD | @dnd-kit | 칸반 드래그앤드롭 |
| **배포** | **Docker Compose** | 전체 스택 단일 명령 기동 |
| **리버스 프록시** | **Nginx** | Docker 내 프론트/백/정적파일 라우팅 |
| DB 마이그레이션 | **Flyway** | Spring Boot 연동, SQL 기반 버전 관리 |

---

## 인프라 아키텍처 (Docker Compose)

```
외부 요청 (:80)
    │
    ▼
┌─ Nginx (리버스 프록시) ─────────────────────┐
│   /            → frontend:3000               │
│   /api/*       → backend:8080                │
│   /uploads/*   → 정적 파일 서빙 (볼륨 직접)   │
└──────────────────────────────────────────────┘
         │              │              │
         ▼              ▼              ▼
    [frontend]     [backend]        [db]
     Next.js      Spring Boot    PostgreSQL 16
                      │              │
                      ├── JDBC ──────┘
                      └── /data/uploads/ (Docker 볼륨)
```

### docker-compose.yml 서비스 구성
| 서비스 | 이미지 | 포트 | 역할 |
|--------|--------|------|------|
| `db` | postgres:16-alpine | 5432 (내부) | 데이터베이스 |
| `backend` | 자체 빌드 (Dockerfile) | 8080 (내부) | REST API 서버 |
| `frontend` | 자체 빌드 (Dockerfile) | 3000 (내부) | SSR + 정적 렌더링 |
| `nginx` | nginx:alpine | **80 (외부 노출)** | 리버스 프록시 |

### Docker 볼륨
| 볼륨 | 용도 | 컨테이너 경로 |
|------|------|--------------|
| `pgdata` | PostgreSQL 데이터 영속 | `/var/lib/postgresql/data` |
| `uploads` | 업로드 파일 저장 (Hash Vault) | `/data/uploads` |

### 개발 환경 기동
```bash
# 전체 스택 기동
docker compose up -d

# 백엔드만 재빌드
docker compose up -d --build backend

# 로그 확인
docker compose logs -f backend

# DB 직접 접속
docker compose exec db psql -U blackbox -d blackbox_db
```

---

## 개발 단계 (3-Phase)

### MVP (1~8주차 — 중간발표)
플랫폼 내부 데이터만으로 동작하는 독립 시스템.

| 기능 | 상세 |
|------|------|
| 인프라 | Docker Compose (DB + Backend + Frontend + Nginx) |
| 인증 | JWT 로그인/회원가입, 역할(STUDENT/PROFESSOR/TA) |
| 프로젝트 관리 | CRUD, 초대코드, 멤버 관리 |
| 칸반 보드 | 태스크 CRUD, 드래그앤드롭, 담당자/마감/태그 |
| 회의록 | 생성, 체크인(QR/링크), 결정사항 → 태스크 생성 |
| Hash Vault | 파일 업로드 시 SHA-256 해시 고정, 변조 감지 |
| Score Engine | 플랫폼 내부 활동 기반 기여도 산출 |
| 교수 대시보드 | 팀 목록, 기여도 차트, 진행률, 경보 |
| 경보 | 규칙 기반 (불균형/벼락치기/이탈) |

### 확장 1 (9~12주차)
| 기능 | 상세 |
|------|------|
| GitHub App 연동 | Webhook 기반 커밋/PR/Issue 수집 |
| Google Drive 연동 | Push Notification 기반 revision 수집 |
| Score Engine 확장 | 외부 데이터 통합 점수 산출 |
| 타임라인 뷰 | 전체 활동 통합 타임라인 |

### 확장 2 (13~15주차, 시간 허용 시)
| 기능 | 상세 |
|------|------|
| AI Analyzer | Claude API 커밋 품질 분석 |
| 피어리뷰 | 익명 상호평가 + 크로스체크 |
| PDF 리포트 | 자동 생성 |
| 가중치 고도화 | 교수 프리셋, 변경 이력 |

---

## 디렉토리 구조 (컨텍스트 파일)

```
team-blackbox/
├── claude.md                ← 📌 이 파일 (루트 컨텍스트)
├── todo.md                  ← 우선순위 & 개발 흐름
├── gc.md                    ← 🧹 가비지컬렉션 (불일치·위반·드리프트 방지)
├── docs/
│   ├── architecture.md      ← 시스템 아키텍처 전체 그림
│   ├── database.md          ← DB 스키마, ERD, 마이그레이션 (Flyway)
│   ├── api-design.md        ← REST API 설계 규칙 & 엔드포인트 전체
│   └── docker.md            ← Docker Compose 설정 & 배포 가이드
├── backend/
│   ├── claude.md            ← 백엔드 루트 컨텍스트
│   ├── modules/             ← 모듈별 상세 (auth, project, task, ...)
│   └── api/
│       └── endpoints.md     ← 백엔드 API 구현 상세
├── frontend/
│   ├── claude.md            ← 프론트엔드 루트 컨텍스트
│   ├── pages/               ← 페이지별 상세
│   ├── components/          ← 공통 컴포넌트
│   └── hooks/               ← 커스텀 훅
└── shared/
    ├── types.md             ← TypeScript 타입 & Java DTO 매핑
    └── conventions.md       ← 코딩 컨벤션, 네이밍, Git 규칙
```

---

## 핵심 데이터 흐름

```
사용자 행동 (태스크 완료, 체크인, 파일 업로드)
    ↓
activity_logs 테이블에 이벤트 기록 (source: PLATFORM)
    ↓
[확장 1] GitHub Webhook / Drive Push → activity_logs (source: GITHUB / GOOGLE_DRIVE)
    ↓
Score Engine: activity_logs 기반 팀원별 점수 계산
    ↓
contribution_scores 테이블 업데이트
    ↓
경보 엔진: 불균형/벼락치기/이탈 감지 → alerts 테이블
    ↓
교수 대시보드에서 조회
```

---

## 파일 업로드 흐름 (로컬 파일 시스템)

```
1. 클라이언트 → multipart/form-data 파일 전송
2. Spring Boot FileStorageService:
   a. SHA-256 해시 생성
   b. 저장 경로 결정: /data/uploads/{projectId}/{hash}_{filename}
   c. 파일 디스크 기록
   d. file_vault 테이블 INSERT (해시 + 경로)
3. 다운로드: GET /api/files/{vaultId}/download → 파일 스트리밍 응답
4. Nginx: /uploads/* → 정적 파일 직접 서빙 (선택적 최적화)
```

---

## 사용자 역할 & 권한

| 역할 | 코드 | 권한 |
|------|------|------|
| 학생 (팀장) | `STUDENT` + `LEADER` | 프로젝트 설정, 멤버 관리, 전체 CRUD |
| 학생 (팀원) | `STUDENT` + `MEMBER` | 태스크/회의록 CRUD, 파일 업로드 |
| 교수/조교 | `PROFESSOR` / `TA` + `OBSERVER` | 읽기 전용 + 가중치 조정 |

접근 제어: Supabase RLS 대신 **Spring Security + 서비스 레이어 `ProjectAccessChecker`**로 처리.

---

## 주요 규칙 (개발 시 항상 준수)

1. **활동 로그 우선**: 모든 사용자 행동은 `activity_logs`에 기록. 점수는 이 로그에서 파생.
2. **Hash Vault 불변성**: `file_vault` 테이블은 INSERT만 가능. UPDATE/DELETE 트리거로 차단.
3. **읽기 전용 외부 연동**: GitHub/Drive에 쓰기 권한 절대 요청하지 않음.
4. **점수는 참고 지표**: "판단은 사람이, 근거는 시스템이" — 성적 산출 도구가 아님.
5. **동의 기반 수집**: 모든 데이터 수집은 명시적 동의 후. AI 분석은 별도 동의.
6. **MVP 독립성**: 외부 API 없이도 MVP가 완전히 동작해야 함.
7. **Docker 기본**: 로컬 개발도 `docker compose up`으로 실행. 팀원 환경 차이 없음.
8. **외부 서비스 종속 없음**: 모든 인프라를 Docker 내에서 자체 관리. Supabase/Vercel/Render 등 사용하지 않음.
9. **주기적 GC**: PR 머지 전·스프린트 종료 시 `gc.md` 체크리스트 실행. 불변 규칙 위반은 즉시 수정.
