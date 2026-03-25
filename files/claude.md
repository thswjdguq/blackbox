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
| Frontend | Next.js 14+ (App Router, TypeScript) | Vercel 배포 |
| Backend | Java 17+ (Spring Boot 3.x) | Railway/Render 배포 |
| Database | MySQL 8.0+ | 로컬 설치, Spring Data JPA 연동 |
| Storage | 로컬 파일 서버 or AWS S3 | 파일 업로드 (Hash Vault) |
| Auth | JWT (Access 30min + Refresh 7day) | Spring Security |
| External API | GitHub App API, Google Drive API | 확장 1 단계 |
| AI | Claude API (Anthropic) | 확장 2 단계 |
| CSS | Tailwind CSS 3.x | shadcn/ui 컴포넌트 |
| Chart | Recharts | 대시보드 차트 |
| State | Zustand | 클라이언트 상태 관리 |
| DnD | @dnd-kit | 칸반 드래그앤드롭 |

---

## 개발 단계 (3-Phase)

### MVP (1~8주차 — 중간발표)
플랫폼 내부 데이터만으로 동작하는 독립 시스템.

| 기능 | 상세 |
|------|------|
| 인증 | JWT 로그인/회원가입, 역할(STUDENT/PROFESSOR/TA) |
| 프로젝트 관리 | CRUD, 초대코드, 멤버 관리 |
| 칸반 보드 | 태스크 CRUD, 드래그앤드롭, 담당자/마감/태그 |
| 회의록 | 생성, 체크인(QR/링크), 결정사항 → 태스크 생성 |
| Hash Vault | 파일 업로드 시 SHA-256 해시 고정, 변조 감지 |
| Score Engine | 플랫폼 내부 활동 기반 기여도 산출 |
| 교수 대시보드 | 팀 목록, 기여도 차트, 진행률, 경보 |
| 경보 | 규칙 기반 (불균형/벼락치기/이탈) |

### 확장 1 (9~12주차)
외부 API 연동으로 데이터 소스 확장.

| 기능 | 상세 |
|------|------|
| GitHub App 연동 | Webhook 기반 커밋/PR/Issue 수집 |
| Google Drive 연동 | Push Notification 기반 revision 수집 |
| Score Engine 확장 | 외부 데이터 통합 점수 산출 |
| 타임라인 뷰 | 전체 활동 통합 타임라인 |

### 확장 2 (13~15주차, 시간 허용 시)
AI 분석 및 고도화.

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
├── docs/
│   ├── architecture.md      ← 시스템 아키텍처 전체 그림
│   ├── database.md          ← DB 스키마, ERD, 마이그레이션
│   └── api-design.md        ← REST API 설계 규칙 & 엔드포인트 전체
├── backend/
│   ├── claude.md            ← 백엔드 루트 컨텍스트
│   ├── modules/
│   │   ├── auth.md          ← 인증/인가 (JWT, OAuth)
│   │   ├── project.md       ← 프로젝트 & 멤버 관리
│   │   ├── task.md          ← 태스크 (칸반)
│   │   ├── meeting.md       ← 회의록 & 체크인
│   │   ├── vault.md         ← Hash Vault (위변조 방지)
│   │   ├── score.md         ← Score Engine (기여도 산출)
│   │   ├── collector.md     ← Data Collector (외부 API 동기화)
│   │   ├── analyzer.md      ← AI Analyzer (확장 2)
│   │   └── alert.md         ← 경보 시스템
│   └── api/
│       └── endpoints.md     ← 백엔드 API 구현 상세
├── frontend/
│   ├── claude.md            ← 프론트엔드 루트 컨텍스트
│   ├── pages/
│   │   ├── auth.md          ← 로그인/회원가입 페이지
│   │   ├── dashboard.md     ← 프로젝트 대시보드 (메인)
│   │   ├── board.md         ← 칸반 보드
│   │   ├── meeting.md       ← 회의록 페이지
│   │   ├── analytics.md     ← 기여도 분석 (팀원 뷰)
│   │   ├── professor.md     ← 교수 전용 페이지
│   │   └── vault.md         ← 파일 이력 뷰
│   ├── components/
│   │   └── shared.md        ← 공통 컴포넌트 설계
│   └── hooks/
│       └── hooks.md         ← 커스텀 훅 & API 연동 패턴
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

## 사용자 역할 & 권한

| 역할 | 코드 | 권한 |
|------|------|------|
| 학생 (팀장) | `STUDENT` + `LEADER` | 프로젝트 설정, 멤버 관리, 전체 CRUD |
| 학생 (팀원) | `STUDENT` + `MEMBER` | 태스크/회의록 CRUD, 파일 업로드 |
| 교수/조교 | `PROFESSOR` / `TA` + `OBSERVER` | 읽기 전용 + 가중치 조정 |

---

## 주요 규칙 (개발 시 항상 준수)

1. **활동 로그 우선**: 모든 사용자 행동은 `activity_logs`에 기록. 점수는 이 로그에서 파생.
2. **Hash Vault 불변성**: `file_vault` 테이블은 INSERT만 가능. BEFORE UPDATE/DELETE 트리거로 차단.
3. **읽기 전용 외부 연동**: GitHub/Drive에 쓰기 권한 절대 요청하지 않음.
4. **점수는 참고 지표**: "판단은 사람이, 근거는 시스템이" — 성적 산출 도구가 아님.
5. **동의 기반 수집**: 모든 데이터 수집은 명시적 동의 후. AI 분석은 별도 동의.
6. **MVP 독립성**: 외부 API 없이도 MVP가 완전히 동작해야 함.

---

## DB 연결 설정 (MySQL)

```yaml
# backend/src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/blackbox?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: true
```

```bash
# 로컬 MySQL DB 생성
CREATE DATABASE blackbox CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'blackbox_user'@'localhost' IDENTIFIED BY '비밀번호';
GRANT ALL PRIVILEGES ON blackbox.* TO 'blackbox_user'@'localhost';
FLUSH PRIVILEGES;
```
