# Team Blackbox — 팀 프로젝트 기여도 자동 증빙 플랫폼

## 종합 기획서 v3.0 (Functional Specification — Revised)

**팀명:** 손에 송잡고  
**프로젝트명:** Team Blackbox  
**과목:** Capstone Design 2026  
**작성일:** 2026. 03. 23.  
**팀원:** 송병철 · 송승준 · 손정협 · 손정효

---

## 변경 이력

| 버전 | 날짜 | 주요 변경 |
|------|------|-----------|
| v1.0 | 2026.03.23 | 초안 작성 |
| v2.0 | 2026.03.23 | MVP 범위 재정의, GitHub 권한 모델 수정, 데이터 무결성 범위 명확화, AI 분석 개인정보 정합성 보완, 정규화 공정성 개선, 오프라인 작업 보완, Anti-Gaming 로직 추가, 동기화 전략 개선 |
| v3.0 | 2026.03.23 | **인프라 전면 변경** — Supabase 제거 → 로컬 PostgreSQL, Supabase Storage → 로컬 파일 시스템, Vercel/Render → Docker Compose 배포, DB 마이그레이션 Flyway 도입, Nginx 리버스 프록시 추가 |

---

## 1. 프로젝트 개요

### 1.1 문제 정의

대학교 팀 프로젝트에서 반복되는 3가지 구조적 문제가 있다.

1. **무임승차 판별 불가** — "누가 얼마나 했는지"를 증명할 객관적 근거가 없다. 기여도 분쟁이 발생해도 카카오톡 대화방 캡처 수준의 근거만 존재한다.
2. **기록 분산 및 조작 가능성** — 회의록은 카카오톡, 파일은 Google Drive, 일정은 Notion 등으로 흩어져 있으며, 이 기록들은 누구나 수정·삭제 가능하다.
3. **교수자의 평가 사각지대** — 교수·조교는 최종 결과물만 볼 수 있을 뿐, 팀 내부의 실제 기여 구조를 파악할 방법이 없다.

### 1.2 솔루션 요약

Team Blackbox는 GitHub, Google Drive 등 **외부 도구에 이미 기록된 활동 로그**를 자동 수집하여 팀원별 기여도를 객관적으로 산출하는 플랫폼이다. 플랫폼 내 업로드 파일은 해시 검증으로 위변조를 방지하고, 교수 전용 대시보드로 평가 근거를 제공한다.

### 1.3 설계 철학

> **"우리가 데이터를 만들지 않는다. 외부 시스템이 이미 기록한 데이터를 읽어올 뿐이다."**

이 프레임은 두 가지를 동시에 해결한다.
- **개인정보 방어:** 감시가 아닌 읽기(Read-only)
- **논리적 방어:** 학생이 시스템에서 조작할 수 있는 것은 아무것도 없다

### 1.4 데이터 무결성 범위 명확화 ⚡ NEW

> **기존 문제:** "위변조 방지"라는 표현이 모든 데이터를 우리 플랫폼이 보증하는 것처럼 들릴 수 있다.

Team Blackbox의 데이터 무결성은 **두 가지 계층**으로 구분된다.

| 계층 | 대상 | 보증 수준 | 메커니즘 |
|------|------|-----------|----------|
| **Level 1: 플랫폼 내부 증빙** | 플랫폼에 직접 업로드된 파일, 태스크 로그, 회의 체크인 | **강한 보증** — SHA-256 해시 고정 + DB 트리거로 수정 원천 차단 | Hash Vault |
| **Level 2: 외부 시스템 로그** | GitHub 커밋, Google Drive revision 등 | **참조 수준** — 외부 API가 제공하는 데이터를 읽기 전용으로 수집. 외부 시스템 자체의 무결성에 의존 | Data Collector (Read-only) |

**발표 시 표현 가이드:**
- ✅ "플랫폼 내 업로드 파일은 SHA-256 해시로 위변조를 방지합니다"
- ✅ "외부 로그는 GitHub/Google이 보증하는 데이터를 읽기 전용으로 가져옵니다"
- ❌ ~~"모든 데이터의 위변조를 방지합니다"~~ (과장)

> **교수님 방어:** "플랫폼 내 파일은 저희가 해시로 보증합니다. 외부 로그는 GitHub과 Google이라는 신뢰할 수 있는 제3자가 이미 보증하고 있고, 저희는 그 데이터를 읽기 전용으로 가져와 조합합니다. 즉, 학생이 양쪽 어디서도 조작할 수 없는 구조입니다."

### 1.5 목표 산업군

IT SaaS / 에듀테크 (EdTech)

### 1.6 경쟁 제품 비교

| 구분 | Jira / Asana | GitHub | Notion | **Team Blackbox** |
|------|-------------|--------|--------|-------------------|
| 주 대상 | 기업 | 개발자 | 전체 | **대학생 팀플** |
| 교수 대시보드 | ✗ | ✗ | ✗ | **✓** |
| 자동 기여도 수집 | 부분적 | 코드만 | ✗ (수동) | **✓ 멀티소스** |
| 비개발자 추적 | ✗ | ✗ | ✗ | **✓ (Drive 메타)** |
| 무임승차 탐지 | ✗ | ✗ | ✗ | **✓** |
| 업로드 파일 위변조 방지 | ✗ | 부분 | ✗ | **✓ (SHA-256)** |
| 한국 학사 환경 특화 | ✗ | ✗ | ✗ | **✓** |
| 비용 | 유료 | 무료 | 무료 | **무료** |

---

## 2. MVP 범위 정의 ⚡ NEW — 15주 현실성 확보

### 2.1 3단계 개발 전략

기존 기획서의 가장 큰 리스크는 **15주 안에 모든 기능을 구현하기 어렵다**는 점이다. 이를 해결하기 위해 기능을 3단계로 나누고, **MVP(8주차 중간발표)에서 반드시 동작해야 하는 범위를 명확히 한다.**

```
┌─────────────────────────────────────────────────────────┐
│  MVP (8주차 중간발표 — 반드시 완성)                       │
│                                                          │
│  ① 플랫폼 내부 기여도 추적 (칸반 + 회의록 + 활동 로그)    │
│  ② Hash Vault (파일 업로드 해시 고정)                     │
│  ③ 기본 Score Engine (플랫폼 내부 데이터 기반)            │
│  ④ 교수 대시보드 (기여도 차트 + 프로젝트 진행률)          │
│  ⑤ 사용자 인증 (JWT) + 프로젝트/팀 관리                  │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  확장 1 (9~12주 — 핵심 차별화 기능)                       │
│                                                          │
│  ⑥ GitHub OAuth 연동 + 커밋 데이터 자동 수집              │
│  ⑦ Google Drive OAuth 연동 + revision 수집               │
│  ⑧ Score Engine 확장 (외부 데이터 통합)                   │
│  ⑨ 기여 불균형 경보 (규칙 기반, AI 없이)                  │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  확장 2 (13~15주 — 고도화, 시간 허용 시)                  │
│                                                          │
│  ⑩ AI Analyzer (Claude API 커밋 품질 분석)               │
│  ⑪ 피어리뷰 시스템                                       │
│  ⑫ PDF 리포트 자동 생성                                  │
│  ⑬ 교수 가중치 조정 고도화                                │
└─────────────────────────────────────────────────────────┘
```

### 2.2 MVP가 그 자체로 가치 있는 이유

MVP만으로도 **"플랫폼 내부 활동 로그 기반 기여도 자동 산출 + 해시 증빙 + 교수 대시보드"**가 동작한다. 이것만으로 Notion/Jira에 없는 기능이며, Gemini 분석에서 "최소 버전은 서비스 안에서의 행동 로그만"이라고 선을 그은 범위와 정확히 일치한다.

외부 API 연동(GitHub/Drive)은 MVP 이후 확장이므로, OAuth 구현이 지연되어도 핵심 데모는 가능하다.

### 2.3 중간발표 데모 시나리오

```
1. 팀장이 프로젝트 생성 → 팀원 초대 (초대 코드)
2. 팀원들이 칸반 보드에서 태스크 생성·완료
3. 회의록 작성 + 체크인
4. 파일 업로드 → Hash Vault에 해시 고정되는 과정 시연
5. 교수 대시보드에서 팀원별 기여도 차트 확인
6. (보너스) 해시 변조 감지 데모: 같은 파일 수정 후 재업로드 → 경고 발생
```

---

## 3. 사용자 정의 및 시나리오

### 3.1 사용자 유형

| 유형 | 설명 | 주요 권한 |
|------|------|-----------|
| **팀장** | 프로젝트 생성, 팀원 관리, 설정 변경 | 모든 권한 |
| **팀원** | 태스크/회의록 작성, 상태 변경 | 태스크 CRUD, 회의록 작성 |
| **교수/조교 (관찰자)** | 팀별 진행률·기여도 확인 | 읽기 전용 + 가중치 조정 |

### 3.2 핵심 사용 시나리오

**시나리오 A — 프로젝트 시작**
1. 팀장이 새 프로젝트(예: 캡스톤 디자인) 생성
2. 팀원 초대 (링크/코드/이메일)
3. 데이터 수집 동의 온보딩 진행
4. (확장 1 이후) GitHub 리포지토리 + Google Drive 폴더 OAuth 연동

**시나리오 B — 일상 작업 추적**
1. 팀원이 칸반 보드에서 태스크 생성, 담당자·마감일 지정
2. 작업 후 태스크 상태 이동 (To Do → In Progress → Done)
3. (확장 1 이후) 시스템이 GitHub 커밋, Drive 편집 이력을 자동 동기화
4. 회의 시 QR/링크 체크인으로 참석 기록

**시나리오 C — 교수 평가**
1. 교수가 관찰자 계정으로 대시보드 접속
2. 팀별 진행률, 기여도 분포 확인
3. 불균형 경보 발생 시 해당 팀 상세 조회
4. 가중치 직접 조정 → 프로젝트 특성에 맞는 평가 적용
5. (확장 2 이후) PDF 리포트 다운로드하여 성적 반영

**시나리오 D — 무임승차 탐지**
1. 규칙 기반 엔진이 팀원 간 점수 편차 > 40% 감지
2. 마감 직전 벼락치기 패턴 감지 (마감 24h 내 70% 이상 작업)
3. 교수 대시보드에 자동 경보 표시
4. (확장 2 이후) 시스템 점수 vs 피어리뷰 점수 교차 검증

---

## 4. 시스템 아키텍처

### 4.1 전체 구조

```
[외부 데이터 소스]            [Blackbox 코어]              [사용자 인터페이스]
                               
 GitHub App      ──Webhook──→ ┌──────────────────┐
 (확장 1)                      │  Data Collector   │
 Google Drive API ─Webhook──→ │  (동기화 엔진)     │──→  Score Engine  ──→  교수 대시보드
 (확장 1)                      └──────────────────┘         ↓                 팀원 뷰
                                                        AI Analyzer   ──→  PDF 리포트
 플랫폼 내부 로그  ──────→    ┌──────────────────┐     (확장 2)             알림 시스템
  (체크인, 태스크)             │   Hash Vault      │
                               │  (위변조 방지)     │
                               └──────────────────┘
```

### 4.2 기술 스택

| 분류 | 기술 | 선정 사유 |
|------|------|-----------|
| **프론트엔드** | Next.js (TypeScript) | SSR 지원, React 생태계, 대시보드 최적화 |
| **백엔드** | Java (Spring Boot) | 엔터프라이즈급 안정성, OAuth 라이브러리 풍부 |
| **데이터베이스** | PostgreSQL 16 (로컬) | Docker 컨테이너, 직접 관리, 외부 종속 없음 |
| **파일 저장소** | 로컬 파일 시스템 | Docker 볼륨 마운트, 자체 관리 |
| **DB 마이그레이션** | Flyway | Spring Boot 연동, SQL 기반 버전 관리 |
| **외부 API** | GitHub App API (확장 1) | 커밋, PR, Issue 데이터 수집 |
| | Google Drive API (확장 1) | 파일 revision history, 댓글, 편집 이력 |
| | Claude API (확장 2) | 커밋 품질 분석, 패턴 탐지 |
| **개발 도구** | VS Code, Cursor, GitHub | 협업 및 버전 관리 |
| **배포** | Docker Compose | 전체 스택(DB+백+프론트+Nginx) 단일 관리 |
| **리버스 프록시** | Nginx | Docker 내 프론트/백/정적파일 라우팅 |

### 4.3 프론트엔드 아키텍처

```
Next.js App Router
├── (auth)/          ← 로그인/회원가입
├── (dashboard)/     ← 공통 레이아웃
│   ├── projects/    ← 프로젝트 목록/생성
│   ├── board/       ← 칸반 보드
│   ├── meetings/    ← 회의록
│   ├── analytics/   ← 기여도 대시보드 (팀원용)
│   └── settings/    ← 프로젝트 설정, OAuth 연동
├── (professor)/     ← 교수 전용
│   ├── overview/    ← 전체 팀 현황
│   ├── team/[id]/   ← 개별 팀 상세
│   ├── weights/     ← 가중치 조정 패널
│   └── reports/     ← PDF 리포트 생성 (확장 2)
└── api/             ← API 라우트 (BFF 패턴)
```

### 4.4 백엔드 아키텍처

```
Spring Boot Application
├── auth/            ← JWT 인증 + OAuth2 Client
├── project/         ← 프로젝트 CRUD
├── task/            ← 태스크(칸반) 관리
├── meeting/         ← 회의록 관리
├── collector/       ← 외부 API 동기화 (확장 1)
│   ├── github/      ← GitHub 데이터 수집
│   └── drive/       ← Google Drive 데이터 수집
├── vault/           ← Hash Vault (위변조 방지)
├── score/           ← Score Engine (기여도 산출)
├── analyzer/        ← AI Analyzer (확장 2)
├── report/          ← PDF 리포트 생성 (확장 2)
└── notification/    ← 알림 시스템
```

---

## 5. 핵심 모듈 상세 설계

### 5.1 모듈 1 — Data Collector (자동 수집 엔진)

**담당:** 송병철 (OAuth & API 연동)

**설계 원칙:** 플랫폼이 직접 감시하는 것이 아니라 OAuth로 권한 위임받아 읽기만 수행

#### 5.1.1 수집 데이터 소스

**플랫폼 내부 (MVP — 직접 수집)**
| 데이터 | 설명 |
|--------|------|
| 회의 체크인 | QR 코드 or 링크 클릭 기반 참석 체크 |
| 태스크 이력 | 칸반 보드 생성/상태변경/완료 이벤트 |
| 파일 업로드 | 업로드 타임스탬프 + SHA-256 해시 |
| 회의록 작성 | 작성자, 작성 시점, 편집 이력 |
| 댓글/코멘트 | 태스크·회의록에 남긴 코멘트 |

**GitHub (확장 1 — 커밋 + 협업 지표)**
| 데이터 | 설명 | API 엔드포인트 |
|--------|------|----------------|
| 커밋 목록 | 커밋 횟수, 시간, 메시지, diff 크기 | `GET /repos/{owner}/{repo}/commits` |
| PR 이력 | PR 생성/리뷰 횟수, 머지 여부 | `GET /repos/{owner}/{repo}/pulls` |
| Issue 이력 | Issue 오픈/클로즈, 라벨 | `GET /repos/{owner}/{repo}/issues` |
| 코드 리뷰 | 리뷰 코멘트 수, 승인/거절 | `GET /repos/{owner}/{repo}/pulls/{id}/reviews` |

**Google Drive (확장 1 — 문서 작업 지표)**
| 데이터 | 설명 | API 엔드포인트 |
|--------|------|----------------|
| Revision History | 수정 횟수, 수정 시간대 | `GET /files/{fileId}/revisions` |
| 댓글 | 댓글 작성/답글 횟수 | `GET /files/{fileId}/comments` |
| 파일 생성 이력 | 파일 최초 생성 시점 | `GET /files/{fileId}` (createdTime) |

#### 5.1.2 동기화 전략 — Webhook 우선 설계 ⚡ IMPROVED

> **기존 문제:** 15분/30분 폴링은 API 호출 제한(Rate Limit)에 취약하고, "실시간"이라는 표현과 불일치했다.

```
동기화 전략 (3단계):

1단계 — 플랫폼 내부 로그 (MVP):
   즉시 반영 (이벤트 기반, 별도 동기화 불필요)

2단계 — GitHub (확장 1):
   ✅ 우선: GitHub App Webhook (push, pull_request, issues 이벤트)
       → 이벤트 발생 즉시 서버로 POST → 거의 실시간 반영
   ⚠️ 폴백: Webhook 실패 시 30분 간격 보정 폴링 (누락 방지)

3단계 — Google Drive (확장 1):
   ✅ 우선: Drive Push Notification (Changes: watch API)
       → 파일 변경 시 서버로 알림 → 준실시간 반영
   ⚠️ 폴백: 채널 만료 시 1시간 간격 보정 폴링

용어 정리:
   - "준실시간" = Webhook/Push 기반, 수 초~수 분 내 반영
   - "정기 동기화" = 폴링 기반, 30분~1시간 간격
   → 발표 시 "실시간"이 아닌 "준실시간 자동 수집"으로 표현
```

**API Rate Limit 대응:**

| API | 제한 | 대응 |
|-----|------|------|
| GitHub App | 5,000 req/hour/installation | Webhook 우선 → 폴링 최소화 |
| Google Drive | 12,000 req/60sec (프로젝트) | Push Notification → 폴링 최소화 |
| Claude API (확장 2) | 요청당 과금 | 배치 처리 (10커밋 단위 묶어서 분석) |

#### 5.1.3 GitHub 권한 모델 — GitHub App 사용 ⚡ CRITICAL FIX

> **기존 오류:** 기획서에 GitHub OAuth scope를 `repo (read-only)`로 적었으나, GitHub 공식 문서에서 `repo` scope는 public/private 저장소에 대한 **full access**이다. 이는 개인정보 방어 논리를 심각하게 약화시킨다.

**수정: OAuth App → GitHub App으로 전환**

| 항목 | ❌ 기존 (OAuth App) | ✅ 수정 (GitHub App) |
|------|---------------------|----------------------|
| 권한 단위 | scope 단위 (거침) | permission 단위 (세밀) |
| `repo` scope | read + write 모두 포함 | **사용하지 않음** |
| 읽기 전용 설정 | 불가능 | `contents: read`, `pull_requests: read` 등 개별 설정 |
| Webhook | 별도 설정 필요 | 내장 지원 |
| GitHub 권장 여부 | 권장하지 않음 (deprecated 방향) | **공식 권장** |

**GitHub App 필요 권한 (Repository permissions):**

```
contents:       read    ← 커밋, 파일 목록
pull_requests:  read    ← PR, 리뷰
issues:         read    ← Issue
metadata:       read    ← 기본 리포지토리 정보 (자동 부여)
```

**Google Drive는 기존 방식 유지:**
- `drive.readonly` scope — 이미 읽기 전용으로 명확히 분리됨

> **교수님 방어:** "GitHub은 OAuth App이 아닌 GitHub App을 사용합니다. GitHub App은 `contents: read`처럼 권한을 개별 설정할 수 있어서, 코드 읽기만 허용하고 쓰기는 원천 차단됩니다. 이는 GitHub이 공식 권장하는 최신 인증 방식입니다."

---

### 5.2 모듈 2 — Hash Vault (위변조 방지 금고)

**담당:** 송승준 (Hash Vault & DB 아키텍처)

**핵심 원리:** 플랫폼 내 파일 업로드 즉시 SHA-256 해시값을 생성하여 DB에 영구 고정. 이후 수정 불가 처리.

> **범위 명확화:** Hash Vault는 **플랫폼에 직접 업로드된 파일**에 대해 강한 위변조 방지를 제공한다. 외부 시스템(GitHub, Google Drive)의 데이터는 해당 시스템의 무결성에 의존하며, Hash Vault 대상이 아니다. (→ 1.4절 참조)

#### 5.2.1 동작 흐름

```
파일 업로드 시:
  1. 파일 바이너리 → SHA-256 해시값 생성
  2. 해시값 + 타임스탬프 → immutable 테이블에 저장
  3. 원본 파일 → 로컬 디스크 저장 (/data/uploads/{projectId}/)

파일 재업로드 시 (같은 파일명):
  1. 새 파일 해시값 생성
  2. 기존 해시값과 비교
  3. 불일치 → "파일 수정됨" 기록 (새 버전으로 저장)
  4. 일치 → "동일 파일 확인" 기록

핵심: 모든 버전의 해시가 남으므로, "언제 어떤 파일이 있었는지"의
      시간순 이력을 사후 조작할 수 없다.
```

#### 5.2.2 DB 스키마

```sql
-- 위변조 방지 금고 테이블 (수정 불가)
CREATE TABLE file_vault (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id    UUID NOT NULL REFERENCES projects(id),
  uploader_id   UUID NOT NULL REFERENCES users(id),
  file_name     VARCHAR(255) NOT NULL,
  file_hash     VARCHAR(64) NOT NULL,          -- SHA-256 (64자)
  file_size     BIGINT NOT NULL,
  storage_path  TEXT NOT NULL,                  -- 로컬 파일 경로 (/data/uploads/...)
  uploaded_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  is_immutable  BOOLEAN DEFAULT TRUE,          -- 수정 불가 플래그
  version       INTEGER NOT NULL DEFAULT 1,    -- 파일 버전 번호
  
  CONSTRAINT no_update_hash CHECK (is_immutable = TRUE)
);

-- file_vault에 대한 UPDATE/DELETE 방지 트리거
CREATE OR REPLACE FUNCTION prevent_vault_modification()
RETURNS TRIGGER AS $$
BEGIN
  RAISE EXCEPTION 'file_vault 레코드는 수정/삭제할 수 없습니다';
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER vault_immutable
  BEFORE UPDATE OR DELETE ON file_vault
  FOR EACH ROW EXECUTE FUNCTION prevent_vault_modification();

-- 변조 감지 이력 테이블
CREATE TABLE tamper_detection_log (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  vault_id        UUID NOT NULL REFERENCES file_vault(id),
  detected_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  original_hash   VARCHAR(64) NOT NULL,
  new_hash        VARCHAR(64) NOT NULL,
  detector_type   VARCHAR(20) NOT NULL,         -- 'REUPLOAD' | 'SCHEDULED_CHECK'
  status          VARCHAR(20) DEFAULT 'FLAGGED' -- 'FLAGGED' | 'REVIEWED' | 'DISMISSED'
);
```

---

### 5.3 모듈 3 — Score Engine (기여도 산출 엔진)

**담당:** 손정협 (AI Analyzer & Score Engine)

#### 5.3.1 기여도 점수 공식

```
총합 점수 = Σ (항목별 점수 × 가중치) × AI Quality Multiplier (확장 2)
```

**기본 가중치 (교수님이 직접 조정 가능):**

| 가중치 | 항목 | 기본값 | MVP 데이터 소스 | 확장 1 추가 소스 |
|--------|------|--------|-----------------|-----------------|
| w1 | 코드/개발 기여 | 0.30 | 태스크(개발 태그) 완료 | + GitHub 커밋, PR |
| w2 | 문서/기획 기여 | 0.25 | 태스크(문서 태그) 완료 + 파일 업로드 | + Drive revision |
| w3 | 회의/협업 참여 | 0.20 | 체크인 + 회의록 작성 + 코멘트 | (동일) |
| w4 | 태스크 실행 | 0.25 | 칸반 완료 + 마감 준수율 | (동일) |

#### 5.3.2 정규화 방식 개선 ⚡ IMPROVED

> **기존 문제:** `개인값 / 팀 최대값 × 100`은 한 명의 과도한 기여가 나머지를 과소평가하게 만든다. 예: A가 50커밋, B가 25커밋, C가 5커밋이면 C는 10점밖에 안 된다.

**수정: 팀 평균 기준 정규화 + 상한/하한 클리핑**

```
기존:  normalized = (개인값 / 팀 최대값) × 100
       → 문제: 1명이 극단적으로 많으면 나머지 전부 과소평가

수정:  normalized = min(150, (개인값 / 팀 평균값) × 100)
       → 팀 평균 = 100점 기준
       → 상한 150 (과도한 기여 1명이 독점하는 것 방지)
       → 하한 0 (활동 없으면 0점)

예시:
  팀 평균 커밋 = 20회
  A: 50회 → min(150, 50/20 × 100) = 150 (상한 적용)
  B: 25회 → 25/20 × 100 = 125
  C:  5회 →  5/20 × 100 = 25
  D:  0회 →  0/20 × 100 = 0

→ C도 기여한 만큼 점수를 받고, A의 과잉 기여가 B·C를 억제하지 않음
```

**추가 공정성 장치:**

| 장치 | 설명 |
|------|------|
| **상한 클리핑 (150)** | 한 명이 점수를 독점하는 것 방지 |
| **최소 활동 기준** | 각 항목에서 최소 1건 이상 활동해야 점수 부여 |
| **이상치 감지** | 팀 평균의 3배 이상 활동 시 자동 플래그 (도배 의심) |
| **교수 가중치 조정** | 프로젝트 성격에 따라 항목 비중 변경 가능 |

#### 5.3.3 오프라인 작업 보완 메커니즘 ⚡ NEW

> **기존 사각지대:** 팀원이 HWP/Word를 로컬에서 작업한 뒤 완성본만 업로드하면, 문서 기여도가 극단적으로 낮게 나온다. Google Drive revision이 추적되지 않기 때문이다.

**보완: "수동 작업 신고" 기능**

```
[수동 작업 신고 UI]

팀원이 플랫폼 외부에서 작업한 내용을 자발적으로 기록:

  작업 내용: [발표자료 PPT 제작 (HWP에서 작업 후 변환)]
  작업 시간: [약 3시간]
  증빙 파일: [최종발표.pptx] ← Hash Vault에 자동 등록
  카테고리:  [문서/기획]
  
  → 이 기록은 activity_logs에 source='MANUAL'로 저장
  → Score Engine에서 반영되되, 자동 수집 데이터보다 낮은 신뢰도 가중치 적용
  → 교수 대시보드에서 "수동 신고" 아이콘으로 구분 표시
```

**신뢰도 가중치:**
| 데이터 소스 | 신뢰도 가중치 | 사유 |
|-------------|-------------|------|
| 플랫폼 자동 로그 (태스크, 체크인) | ×1.0 | 시스템이 직접 기록 |
| 외부 API 자동 수집 (GitHub, Drive) | ×1.0 | 제3자 시스템이 기록 |
| 수동 작업 신고 | ×0.7 | 본인 신고이므로 검증 어려움, 단 파일 업로드 시 해시 증빙 |

> **교수님 방어:** "오프라인 작업도 수동 신고로 반영할 수 있습니다. 다만 자동 수집보다 신뢰도를 70%로 낮추고, 교수님 대시보드에서 수동/자동을 구분해 보여드립니다. 교수님이 직접 판단하실 수 있습니다."

#### 5.3.4 점수 엔진의 한계 고지 ⚡ NEW

기여도 점수는 **"설명 가능한 참고 지표"**이지, **"공정성이 입증된 절대 평가"**가 아니다. 이 점을 기획서와 발표에서 명시한다.

```
기여도 점수의 성격:
  ✅ "팀원별 활동량 차이를 수치로 시각화한 참고 자료"
  ✅ "교수님의 정성 평가를 보조하는 데이터"
  ❌ "이 점수가 곧 성적" (우리 시스템은 성적 산출 도구가 아님)

→ 교수님이 가중치를 조정하고, 피어리뷰와 교차 검증한 뒤,
  최종 판단은 교수님이 내리는 구조
→ "판단은 사람이, 근거는 시스템이"
```

#### 5.3.5 교수 가중치 조정 UI

```
교수 대시보드 → 가중치 설정 패널

[슬라이더 UI]
  코드/개발 작업  ████████░░  0.30
  문서/기획 작업  ██████░░░░  0.25
  협업/소통       █████░░░░░  0.20
  태스크 실행     ██████░░░░  0.25
                              ────
                        합계: 1.00

[프리셋]
  개발 중심 팀플: w1=0.40, w2=0.15, w3=0.20, w4=0.25
  기획 중심 팀플: w1=0.15, w2=0.35, w3=0.25, w4=0.25
  균형형:        w1=0.25, w2=0.25, w3=0.25, w4=0.25

[가중치 변경 이력]  ← 일관성 보장
  2026-05-01  교수 김OO  균형형 → 개발 중심 변경
  2026-04-15  교수 김OO  기본값 유지
  → 언제 누가 어떤 가중치로 바꿨는지 기록
```

> **핵심 전략:** 교수님이 가중치를 직접 조정 → 교수님이 공동 설계자가 됨 → 방어력 극대화. 단, 가중치 변경 이력을 기록하여 평가 일관성 문제도 추적 가능하게 한다.

---

### 5.4 모듈 4 — AI Analyzer (조작 탐지 + 인사이트) — 확장 2

**담당:** 손정협 (AI Analyzer & Score Engine)

**구현 시점:** 확장 2 (13~15주, 시간 허용 시)

> **MVP에서는 규칙 기반 탐지만 구현한다.** AI Analyzer는 고도화 기능이며, 규칙 기반으로도 핵심 탐지(불균형, 벼락치기)는 충분히 가능하다.

#### 5.4.1 규칙 기반 탐지 (MVP — AI 없이)

**① 기여 불균형 감지**
```
규칙:
  - 팀원 간 종합 점수 편차 > 40%    → "무임승차 의심 경보" 🔴
  - 특정 팀원 2주 연속 활동 없음     → "이탈 위험 경보" 🟠
  - 한 팀원이 전체 작업의 60% 이상   → "과부하 경보" 🟡
```

**② 시간 패턴 이상 감지**
```
규칙:
  - 마감 24시간 전 태스크 완료가 전체의 70% 이상 → "벼락치기 경보" 🟡
  - 특정 팀원의 활동이 특정 시간대에만 집중       → "패턴 분석 기록"
```

#### 5.4.2 AI 커밋 품질 분석 (확장 2)

**데이터 처리 투명성 ⚡ CRITICAL FIX**

> **기존 모순:** 개인정보 안내에서 "코드 내용은 저장하지 않으며, 메타데이터만 활용한다"고 적으면서, AI 분석 예시에서는 `diffContent`를 Claude API로 전송하고 있었다. 저장하지 않더라도, 제3자 AI 서비스로 코드 변경내용을 전송하는 것 자체가 별도 고지 대상이다.

**수정된 데이터 처리 정책:**

```
데이터 분류:

(1) 메타데이터만 활용 (기본):
    커밋 메시지, diff 크기(줄 수), 파일 변경 목록, 커밋 시간
    → 저장 O, 외부 전송 X

(2) 코드 변경 내용 분석 (AI 품질 분석 옵션 활성화 시에만):
    diff 내용의 일부(최대 500자)를 Claude API로 전송
    → 저장 X (일회성 분석)
    → 전송 O (Anthropic Claude API)
    → 별도 동의 필요: "AI 품질 분석을 위해 코드 변경 내용의
       일부가 Anthropic의 Claude API로 전송됩니다.
       Anthropic은 API 입력 데이터를 학습에 사용하지 않습니다."
```

**수정된 동의 플로우 (기존 3단계 → 4단계):**

```
[Step 1] GitHub 읽기 권한 동의
[Step 2] Google Drive 읽기 권한 동의
[Step 3] 수집 데이터 범위 고지 및 동의
[Step 4] (선택) AI 품질 분석 동의  ← NEW
  "커밋 품질 분석을 활성화하면, 코드 변경 내용의 일부(최대 500자)가
   Anthropic Claude API로 전송되어 품질 점수를 산출합니다.
   전송된 데이터는 저장되지 않으며, AI 모델 학습에도 사용되지 않습니다."
   
  ☐ 동의합니다 (AI 품질 분석 활성화)
  ☐ 동의하지 않습니다 (메타데이터 기반 분석만 사용)
```

**AI 분석 비동의 시 대안:**
```
AI 품질 분석 미동의 시:
  → 커밋 메시지 길이, diff 크기, 변경 파일 수 등
    메타데이터만으로 quality_score 산출 (규칙 기반)
  
  규칙 예시:
    메시지 10자 미만 + diff 5줄 미만 → 0.3
    메시지 30자 이상 + diff 적절     → 0.8
    1분 내 연속 커밋 10회 이상       → 0.1
```

#### 5.4.3 Anti-Gaming 로직 ⚡ NEW

> **기존 취약점:** 학생이 AI가 높은 점수를 줄 만한 커밋 메시지를 의도적으로 작성하거나, 불필요한 수정을 반복하여 기여도를 부풀릴 수 있다.

**다층 방어 구조:**

```
Layer 1 — 통계적 이상치 탐지 (MVP 규칙 기반):
  - 팀 평균의 3배 이상 커밋 → 자동 플래그
  - 1분 내 연속 10회 이상 커밋 → "도배 의심"
  - diff 크기가 0~2줄인 커밋이 50% 이상 → "미세 수정 도배"
  - 파일 이름만 반복 변경 → "가짜 활동"

Layer 2 — 패턴 분석 (확장 1):
  - 커밋 시간 분포의 엔트로피 분석
    (정상: 고르게 분포 / 비정상: 특정 시간에 몰림)
  - 마감 전 24시간 활동 비율 자동 계산
  - 갑자기 활동량이 급증한 주간 자동 플래그

Layer 3 — AI 의미 분석 (확장 2):
  - Claude API로 커밋 메시지 + diff 분석
  - 단, "AI에게 잘 보이는 메시지"로 게이밍하는 것을 방지하기 위해
    메시지 점수와 실제 코드 변경의 일관성 교차 검증
  - 예: 메시지가 "대규모 리팩토링"인데 diff가 3줄 → 불일치 플래그

Layer 4 — 피어리뷰 크로스체크 (확장 2):
  - 시스템 점수 상위인데 피어리뷰 하위 → "시스템 게이밍 의심"
  - 가장 강력한 방어: 동료의 주관 평가와 시스템의 정량 평가가
    동시에 높아야 진짜 기여로 인정
```

#### 5.4.4 프로젝트 건강도 지표

```
프로젝트 헬스 스코어 (0 ~ 100):

  진행률 (40%):  완료 태스크 / 전체 태스크
  균형도 (30%):  팀원 간 기여도 표준편차의 역수
  일정 준수 (20%): 마감 내 완료율
  활동성 (10%):  최근 7일 활동 빈도

  🟢 80~100: 건강
  🟡 60~79:  주의 필요
  🟠 40~59:  위험
  🔴 0~39:   긴급 개입 필요
```

---

## 6. 데이터베이스 설계

### 6.1 ERD 개요 (주요 테이블)

```
Users ─────────────┐
  │                 │
  ├─ ProjectMembers ┼─ Projects
  │                 │     │
  ├─ TaskAssignees  ┼─ Tasks
  │                 │
  ├─ MeetingAttendees ┼─ Meetings
  │                     │
  ├─ ActivityLogs       │
  │                     │
  ├─ FileVault          │
  │                     │
  ├─ PeerReviews (확장 2)│
  │                     │
  └─ ContributionScores─┘
```

### 6.2 테이블 상세

```sql
-- ==========================================
-- 1. 사용자
-- ==========================================
CREATE TABLE users (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email       VARCHAR(255) UNIQUE NOT NULL,
  name        VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role        VARCHAR(20) NOT NULL DEFAULT 'STUDENT',  -- STUDENT | PROFESSOR | TA
  avatar_url  TEXT,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ==========================================
-- 2. 프로젝트
-- ==========================================
CREATE TABLE projects (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        VARCHAR(255) NOT NULL,
  description TEXT,
  course_name VARCHAR(255),                -- 과목명
  semester    VARCHAR(20),                  -- 예: "2026-1학기"
  start_date  DATE,
  end_date    DATE,
  invite_code VARCHAR(8) UNIQUE,           -- 초대 코드
  created_by  UUID NOT NULL REFERENCES users(id),
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ==========================================
-- 3. 프로젝트 멤버
-- ==========================================
CREATE TABLE project_members (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  user_id     UUID NOT NULL REFERENCES users(id),
  role        VARCHAR(20) NOT NULL DEFAULT 'MEMBER',  -- LEADER | MEMBER | OBSERVER
  joined_at   TIMESTAMPTZ DEFAULT NOW(),
  
  -- 데이터 수집 동의 기록
  consent_platform  BOOLEAN DEFAULT FALSE,     -- 플랫폼 내 데이터 수집
  consent_github    BOOLEAN DEFAULT FALSE,     -- GitHub 연동 (확장 1)
  consent_drive     BOOLEAN DEFAULT FALSE,     -- Google Drive 연동 (확장 1)
  consent_ai_analysis BOOLEAN DEFAULT FALSE,   -- AI 코드 분석 동의 (확장 2) ⚡ NEW
  consented_at      TIMESTAMPTZ,
  
  UNIQUE(project_id, user_id)
);

-- ==========================================
-- 4. OAuth 토큰 (확장 1 — 암호화 저장)
-- ==========================================
CREATE TABLE oauth_tokens (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES users(id),
  provider      VARCHAR(20) NOT NULL,        -- GITHUB_APP | GOOGLE
  access_token  TEXT NOT NULL,               -- AES-256 암호화
  refresh_token TEXT,                        -- AES-256 암호화
  installation_id BIGINT,                    -- GitHub App installation ID ⚡ NEW
  scope         TEXT,
  expires_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ DEFAULT NOW(),
  updated_at    TIMESTAMPTZ DEFAULT NOW(),
  
  UNIQUE(user_id, provider)
);

-- ==========================================
-- 5. 연동 리포지토리/드라이브 (확장 1)
-- ==========================================
CREATE TABLE integrations (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id    UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  provider      VARCHAR(20) NOT NULL,        -- GITHUB_APP | GOOGLE_DRIVE
  external_id   VARCHAR(255) NOT NULL,       -- repo full name | drive folder ID
  external_name VARCHAR(255),                -- 표시용 이름
  webhook_id    VARCHAR(255),                -- Webhook/Push Channel ID ⚡ NEW
  webhook_expiry TIMESTAMPTZ,                -- Push Channel 만료 시점 ⚡ NEW
  last_synced   TIMESTAMPTZ,
  sync_status   VARCHAR(20) DEFAULT 'ACTIVE', -- ACTIVE | PAUSED | ERROR
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- ==========================================
-- 6. 태스크 (칸반 보드) — MVP
-- ==========================================
CREATE TABLE tasks (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  title       VARCHAR(255) NOT NULL,
  description TEXT,
  status      VARCHAR(20) NOT NULL DEFAULT 'TODO',  -- TODO | IN_PROGRESS | DONE
  priority    VARCHAR(10) DEFAULT 'MEDIUM',          -- LOW | MEDIUM | HIGH | URGENT
  tag         VARCHAR(30),                            -- 기능 | 문서 | 디자인 | 버그 | 조사
  due_date    DATE,
  completed_at TIMESTAMPTZ,
  created_by  UUID NOT NULL REFERENCES users(id),
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  updated_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE task_assignees (
  task_id     UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  user_id     UUID NOT NULL REFERENCES users(id),
  assigned_at TIMESTAMPTZ DEFAULT NOW(),
  PRIMARY KEY(task_id, user_id)
);

-- ==========================================
-- 7. 회의 — MVP
-- ==========================================
CREATE TABLE meetings (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  title       VARCHAR(255),
  meeting_date TIMESTAMPTZ NOT NULL,
  purpose     TEXT,
  notes       TEXT,                          -- 논의 내용
  decisions   TEXT,                          -- 결정 사항
  checkin_code VARCHAR(8) UNIQUE,           -- 체크인용 코드
  created_by  UUID NOT NULL REFERENCES users(id),
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE meeting_attendees (
  meeting_id  UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
  user_id     UUID NOT NULL REFERENCES users(id),
  checked_in  BOOLEAN DEFAULT FALSE,
  checked_at  TIMESTAMPTZ,
  PRIMARY KEY(meeting_id, user_id)
);

-- ==========================================
-- 8. 활동 로그 (모든 활동의 통합 기록) — MVP
-- ==========================================
CREATE TABLE activity_logs (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id  UUID NOT NULL REFERENCES projects(id),
  user_id     UUID NOT NULL REFERENCES users(id),
  source      VARCHAR(20) NOT NULL,          -- PLATFORM | GITHUB | GOOGLE_DRIVE | MANUAL
  action_type VARCHAR(50) NOT NULL,          -- COMMIT | PR_CREATE | PR_REVIEW |
                                              -- FILE_EDIT | FILE_CREATE | COMMENT |
                                              -- TASK_CREATE | TASK_COMPLETE |
                                              -- MEETING_ATTEND | CHECKIN | MANUAL_REPORT
  metadata    JSONB,                         -- 상세 데이터 (커밋 메시지, diff 크기 등)
  external_id VARCHAR(255),                  -- 외부 시스템 ID (commit SHA 등)
  trust_level DECIMAL(3,2) DEFAULT 1.00,     -- 신뢰도 (1.0=자동, 0.7=수동) ⚡ NEW
  occurred_at TIMESTAMPTZ NOT NULL,          -- 실제 발생 시점
  synced_at   TIMESTAMPTZ DEFAULT NOW(),     -- 시스템 동기화 시점
  
  -- AI 분석 결과 (확장 2)
  quality_score DECIMAL(3,2),                -- 0.00 ~ 1.00
  quality_reason TEXT,
  analysis_method VARCHAR(20)                -- 'RULE_BASED' | 'AI_CLAUDE' ⚡ NEW
);

-- 인덱스
CREATE INDEX idx_activity_project_user ON activity_logs(project_id, user_id);
CREATE INDEX idx_activity_occurred ON activity_logs(occurred_at DESC);
CREATE INDEX idx_activity_source ON activity_logs(source, action_type);

-- ==========================================
-- 9. 기여도 점수 (주기적 재계산) — MVP
-- ==========================================
CREATE TABLE contribution_scores (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id  UUID NOT NULL REFERENCES projects(id),
  user_id     UUID NOT NULL REFERENCES users(id),
  
  -- 항목별 점수 (0~150, 팀 평균=100 기준)  ⚡ CHANGED
  git_score       DECIMAL(5,2) DEFAULT 0,
  doc_score       DECIMAL(5,2) DEFAULT 0,
  meeting_score   DECIMAL(5,2) DEFAULT 0,
  task_score      DECIMAL(5,2) DEFAULT 0,
  
  -- 종합 점수
  total_score     DECIMAL(5,2) DEFAULT 0,
  
  -- 적용된 가중치 스냅샷
  weight_git      DECIMAL(3,2) DEFAULT 0.30,
  weight_doc      DECIMAL(3,2) DEFAULT 0.25,
  weight_meeting  DECIMAL(3,2) DEFAULT 0.20,
  weight_task     DECIMAL(3,2) DEFAULT 0.25,
  
  calculated_at   TIMESTAMPTZ DEFAULT NOW(),
  
  UNIQUE(project_id, user_id)
);

-- ==========================================
-- 10. 경보 — MVP
-- ==========================================
CREATE TABLE alerts (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id  UUID NOT NULL REFERENCES projects(id),
  user_id     UUID REFERENCES users(id),    -- NULL이면 팀 전체 경보
  alert_type  VARCHAR(30) NOT NULL,          -- CRUNCH_TIME | FREE_RIDE | DROPOUT
                                              -- OVERLOAD | TAMPER | GAMING_SUSPECT
  severity    VARCHAR(10) NOT NULL,           -- LOW | MEDIUM | HIGH | CRITICAL
  message     TEXT NOT NULL,
  is_read     BOOLEAN DEFAULT FALSE,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ==========================================
-- 11. 가중치 설정 + 변경 이력 (교수용) ⚡ IMPROVED
-- ==========================================
CREATE TABLE weight_configs (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id  UUID NOT NULL REFERENCES projects(id),
  professor_id UUID NOT NULL REFERENCES users(id),
  weight_git      DECIMAL(3,2) DEFAULT 0.30,
  weight_doc      DECIMAL(3,2) DEFAULT 0.25,
  weight_meeting  DECIMAL(3,2) DEFAULT 0.20,
  weight_task     DECIMAL(3,2) DEFAULT 0.25,
  updated_at  TIMESTAMPTZ DEFAULT NOW(),
  
  CONSTRAINT weights_sum CHECK (
    weight_git + weight_doc + weight_meeting + weight_task = 1.00
  )
);

-- 가중치 변경 이력 (일관성 추적) ⚡ NEW
CREATE TABLE weight_change_log (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id     UUID NOT NULL REFERENCES projects(id),
  changed_by     UUID NOT NULL REFERENCES users(id),
  old_weights    JSONB NOT NULL,
  new_weights    JSONB NOT NULL,
  changed_at     TIMESTAMPTZ DEFAULT NOW()
);

-- ==========================================
-- 12. 피어리뷰 (확장 2 — 익명 상호평가)
-- ==========================================
CREATE TABLE peer_reviews (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id  UUID NOT NULL REFERENCES projects(id),
  reviewer_id UUID NOT NULL REFERENCES users(id),
  reviewee_id UUID NOT NULL REFERENCES users(id),
  score       INTEGER NOT NULL CHECK (score BETWEEN 1 AND 5),
  comment     TEXT,
  review_round INTEGER DEFAULT 1,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  
  CONSTRAINT no_self_review CHECK (reviewer_id != reviewee_id),
  UNIQUE(project_id, reviewer_id, reviewee_id, review_round)
);

-- ==========================================
-- 13. 수동 작업 신고 (오프라인 작업 보완) ⚡ NEW
-- ==========================================
CREATE TABLE manual_work_reports (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id  UUID NOT NULL REFERENCES projects(id),
  user_id     UUID NOT NULL REFERENCES users(id),
  description TEXT NOT NULL,                 -- 작업 내용 설명
  category    VARCHAR(30) NOT NULL,          -- 기능 | 문서 | 디자인 | 조사
  estimated_hours DECIMAL(4,1),              -- 예상 작업 시간
  file_vault_id UUID REFERENCES file_vault(id), -- 증빙 파일 연결
  created_at  TIMESTAMPTZ DEFAULT NOW()
);
```

---

## 7. API 설계 (주요 엔드포인트)

### 7.1 MVP API

| Method | Path | 설명 | Phase |
|--------|------|------|-------|
| POST | `/api/auth/signup` | 회원가입 | MVP |
| POST | `/api/auth/login` | 로그인 (JWT 발급) | MVP |
| POST | `/api/projects` | 프로젝트 생성 | MVP |
| GET | `/api/projects` | 내 프로젝트 목록 | MVP |
| GET | `/api/projects/:id` | 프로젝트 상세 | MVP |
| POST | `/api/projects/:id/join` | 초대코드로 참여 | MVP |
| GET | `/api/projects/:id/tasks` | 태스크 목록 | MVP |
| POST | `/api/projects/:id/tasks` | 태스크 생성 | MVP |
| PATCH | `/api/tasks/:id` | 태스크 수정 (상태 변경) | MVP |
| DELETE | `/api/tasks/:id` | 태스크 삭제 | MVP |
| GET | `/api/projects/:id/meetings` | 회의 목록 | MVP |
| POST | `/api/projects/:id/meetings` | 회의 생성 | MVP |
| POST | `/api/meetings/:id/checkin` | 체크인 | MVP |
| PATCH | `/api/meetings/:id` | 회의록 수정 | MVP |
| POST | `/api/projects/:id/files` | 파일 업로드 (Hash Vault) | MVP |
| GET | `/api/projects/:id/files` | 파일 이력 조회 | MVP |
| GET | `/api/projects/:id/scores` | 팀원별 기여도 점수 | MVP |
| GET | `/api/projects/:id/alerts` | 경보 목록 | MVP |
| GET | `/api/professor/teams` | 관찰 중인 팀 목록 | MVP |
| GET | `/api/professor/teams/:id/summary` | 팀 요약 | MVP |

### 7.2 확장 1 API

| Method | Path | 설명 | Phase |
|--------|------|------|-------|
| GET | `/api/auth/github/install` | GitHub App 설치 시작 | 확장 1 |
| POST | `/api/webhooks/github` | GitHub Webhook 수신 | 확장 1 |
| GET | `/api/auth/oauth/google` | Google OAuth 시작 | 확장 1 |
| GET | `/api/auth/oauth/google/callback` | Google OAuth 콜백 | 확장 1 |
| POST | `/api/projects/:id/integrations` | 외부 서비스 연동 | 확장 1 |
| GET | `/api/projects/:id/analytics` | 프로젝트 분석 데이터 | 확장 1 |
| GET | `/api/projects/:id/timeline` | 활동 타임라인 | 확장 1 |

### 7.3 확장 2 API

| Method | Path | 설명 | Phase |
|--------|------|------|-------|
| PUT | `/api/professor/teams/:id/weights` | 가중치 조정 | 확장 2 |
| GET | `/api/professor/teams/:id/report` | PDF 리포트 생성 | 확장 2 |
| POST | `/api/projects/:id/peer-review` | 피어리뷰 제출 | 확장 2 |
| GET | `/api/projects/:id/peer-review/results` | 피어리뷰 결과 | 확장 2 |
| POST | `/api/projects/:id/manual-report` | 수동 작업 신고 | 확장 2 |

---

## 8. 화면 설계 (주요 페이지)

### 8.1 MVP 화면 (8주차까지 완성)

**① 프로젝트 대시보드 (메인)**
- 프로젝트 진행률 (Progress Bar)
- 이번 주 마감 태스크 목록
- 최근 활동 피드 (팀원별 최근 행동)
- 나의 기여도 요약 카드

**② 칸반 보드**
- 3단 칼럼: To Do | In Progress | Done
- 드래그 앤 드롭 상태 변경
- 태스크 카드: 제목, 담당자 아바타, 마감일, 우선순위 뱃지, 태그
- 필터: 담당자별, 태그별, 우선순위별

**③ 회의록 페이지**
- 회의 목록 (날짜순)
- 회의 상세: 참석자, 논의 내용, 결정 사항, 액션 아이템
- 액션 아이템 → 태스크 자동 생성 버튼
- QR/링크 체크인 표시

**④ 교수 대시보드 (기본 뷰)**
- 관찰 중인 모든 팀 카드 뷰
- 팀원별 기여도 바 차트
- 프로젝트 진행률 + 경보 뱃지

**⑤ 파일 이력 뷰 (Hash Vault)**
```
[파일 이력 뷰]

📄 최종발표_PPT.pptx
┌────────────────────────────────────────┐
│ v1  2026-05-01 14:34  송병철  ✅ 원본  │ ← 해시: a3f2...
│ v2  2026-05-08 09:12  손정효  ✅ 정상  │ ← 해시: b7c1... (새 버전)
│ v3  2026-05-14 23:58  송병철  ⚠️ 주의 │ ← 마감 직전 수정
└────────────────────────────────────────┘
```

### 8.2 확장 화면 (9~15주)

**⑥ 외부 연동 활동 타임라인 (확장 1)**
- GitHub 커밋 + Drive 편집 + 플랫폼 활동 통합 타임라인
- 소스별 색상 구분 (GitHub=초록, Drive=파랑, 플랫폼=회색)

**⑦ 교수 상세 대시보드 (확장 1~2)**
- 시스템 점수 vs 피어리뷰 점수 비교 차트
- 가중치 조정 슬라이더
- 경보 이력 + Anti-Gaming 플래그

**⑧ PDF 리포트 (확장 2)**
- 팀 요약 정보
- 팀원별 기여도 점수표 + 데이터 소스 구분
- 주요 경보 이력
- 피어리뷰 결과 (익명)

---

## 9. 개인정보 보호 설계 (PIPA 준수) ⚡ IMPROVED

### 9.1 데이터 수집 동의 온보딩 (4단계)

```
[Step 1] 플랫폼 데이터 수집 동의 (MVP)
  "Team Blackbox는 플랫폼 내 활동(태스크 생성/완료, 회의 참석,
   파일 업로드)을 자동 기록하여 기여도를 산출합니다."
   ☐ 동의합니다 (필수)

[Step 2] GitHub 연동 동의 (확장 1 — 선택)
  "GitHub App을 설치하면 연동된 리포지토리에서
   커밋, PR, Issue 메타데이터를 읽기 전용으로 수집합니다.
   코드 내용은 서버에 저장하지 않습니다."
   ☐ 동의합니다  [GitHub App: contents:read, pull_requests:read]

[Step 3] Google Drive 연동 동의 (확장 1 — 선택)
  "연동된 Google Drive 폴더의 파일 수정 이력,
   댓글 수 메타데이터를 읽기 전용으로 수집합니다.
   파일 내용은 저장하지 않습니다."
   ☐ 동의합니다  [scope: drive.readonly]

[Step 4] AI 품질 분석 동의 (확장 2 — 선택)  ⚡ NEW
  "커밋 품질 분석을 위해 코드 변경 내용의 일부(최대 500자)가
   Anthropic Claude API로 전송됩니다.
   전송된 데이터는 저장되지 않으며, AI 학습에도 사용되지 않습니다."
   ☐ 동의합니다 / ☐ 동의하지 않습니다 (메타데이터 기반 분석만 사용)
```

### 9.2 데이터 처리 원칙

| 원칙 | 내용 |
|------|------|
| **최소 수집** | 기여도 산출에 필요한 메타데이터만 수집, 코드/문서 원본 비저장 |
| **읽기 전용** | 외부 시스템에 쓰기 권한 요청하지 않음 |
| **명시적 동의** | 각 단계별 별도 동의, AI 분석은 선택적 |
| **데이터 보존** | 프로젝트 종료 후 6개월 내 삭제 |
| **제3자 전송 고지** | AI 분석 시 Anthropic Claude API 전송 사실 명시 |

---

## 10. 피어리뷰 크로스체크 시스템 (확장 2)

### 10.1 시스템 점수 vs 피어리뷰 교차 검증

```
[교수 대시보드 - 크로스체크 뷰]

팀원     시스템 점수   피어리뷰 평균   차이    상태
송병철      82           4.2/5        정상     ✅
송승준      75           3.8/5        정상     ✅
손정협      68           4.0/5        정상     ✅
손정효      23           1.5/5        일치     ⚠️ 무임승차 의심
```

**자동 경보 조건:**
- 시스템 점수 하위 20% AND 피어리뷰 하위 20% → "무임승차 강력 의심" 🔴
- 시스템 점수 상위 20% AND 피어리뷰 하위 20% → "시스템 게이밍 의심" 🟡
- 시스템 점수 하위 20% AND 피어리뷰 상위 20% → "오프라인 기여 미반영" 🟡

> **"기계도 사람도 같은 결론"** = 압도적 설득력

---

## 11. 팀원 역할 분담 ⚡ IMPROVED (MVP 중심 재배치)

### 11.1 MVP 집중 분담 (1~8주)

| 팀원 | MVP 담당 | 주요 작업 |
|------|----------|-----------|
| **송병철** | 백엔드 API + 인증 | JWT 인증, 프로젝트/태스크/회의 API, 활동 로그 수집 |
| **송승준** | DB + Hash Vault | PostgreSQL 스키마 설계, Hash Vault 구현, 파일 업로드 API |
| **손정협** | Score Engine + 경보 | 기여도 수식 구현, 정규화 로직, 규칙 기반 경보 |
| **손정효** | 프론트엔드 전체 | 칸반 UI, 회의록, 교수 대시보드 기본 뷰, 차트 |

### 11.2 확장 분담 (9~15주)

| 팀원 | 확장 담당 | 주요 작업 |
|------|----------|-----------|
| **송병철** | GitHub App + Drive 연동 | Webhook 수신, OAuth, 동기화 엔진 |
| **송승준** | 데이터 통합 + 성능 | 외부 데이터 정규화, 인덱스 최적화 |
| **손정협** | AI Analyzer + 피어리뷰 | Claude API 연동, Anti-Gaming, 피어리뷰 로직 |
| **손정효** | 확장 UI + PDF | 타임라인, 가중치 조정 UI, PDF 리포트 |

---

## 12. 개발 일정 (15주 타임라인) ⚡ IMPROVED

### Phase 1: 기반 구축 (1~4주)

| 주차 | 작업 내용 | 담당 | 산출물 |
|------|-----------|------|--------|
| 1~2주 | 요구사항 분석, DB 스키마 확정, 와이어프레임 | 전원 | ERD, 화면설계서 |
| 3~4주 | JWT 인증 + 프로젝트/태스크 CRUD API | 송병철 | 기본 API 동작 |
| 3~4주 | DB 구축, Hash Vault 테이블 + 트리거 | 송승준 | Docker + Flyway 초기 설정 |
| 3~4주 | Score Engine 기본 수식 (플랫폼 내부 데이터) | 손정협 | 점수 산출 로직 |
| 3~4주 | Next.js 프로젝트 셋업 + 공통 컴포넌트 | 손정효 | 보일러플레이트 |

### Phase 2: MVP 완성 (5~8주)

| 주차 | 작업 내용 | 담당 | 산출물 |
|------|-----------|------|--------|
| 5~6주 | 회의록 + 체크인 API, 활동 로그 수집 | 송병철 | 전체 MVP API 완성 |
| 5~6주 | Hash Vault 파일 업로드/검증 구현 | 송승준 | 해시 고정 + 변조 감지 |
| 5~7주 | 규칙 기반 경보 (불균형/벼락치기 감지) | 손정협 | 경보 시스템 동작 |
| 5~7주 | 칸반 보드 + 회의록 + 교수 대시보드 UI | 손정효 | 주요 화면 동작 |
| **8주** | **중간 발표** | **전원** | **✅ MVP 데모 가능** |

### Phase 3: 확장 1 — 외부 연동 (9~12주)

| 주차 | 작업 내용 | 담당 | 산출물 |
|------|-----------|------|--------|
| 9~10주 | GitHub App 연동 + Webhook 수신 | 송병철 | GitHub 데이터 자동 수집 |
| 9~10주 | Drive OAuth + Push Notification | 송병철 | Drive 데이터 자동 수집 |
| 9~10주 | 외부 데이터 정규화 + Score Engine 확장 | 손정협+송승준 | 통합 점수 산출 |
| 11~12주 | 활동 타임라인 + 교수 상세 대시보드 | 손정효 | 확장 UI 완성 |

### Phase 4: 확장 2 + 완성 (13~15주)

| 주차 | 작업 내용 | 담당 | 산출물 |
|------|-----------|------|--------|
| 13주 | AI Analyzer (Claude API) — 시간 허용 시 | 손정협 | 품질 분석 |
| 13주 | 피어리뷰 시스템 — 시간 허용 시 | 손정효 | 크로스체크 |
| 14주 | 통합 테스트 + 실제 팀플 데모 데이터 | 전원 | 안정화 |
| **15주** | **최종 발표 + 시연** | **전원** | **최종 산출물** |

> **핵심:** 8주차 MVP가 독립적으로 가치 있으므로, 확장 기능이 미완성이어도 발표 가능. 확장 2는 "시간 허용 시" 구현하되, 없어도 전체 시스템이 동작한다.

---

## 13. 비기능 요구사항

### 13.1 보안
- JWT 기반 인증 (Access Token 30분 + Refresh Token 7일)
- OAuth 토큰 AES-256 암호화 저장
- 패스워드 bcrypt 해시
- HTTPS 강제 (TLS 1.3)
- 접근 제어: Spring Security + 서비스 레이어 ProjectAccessChecker (역할 기반)

### 13.2 권한
- 3단계 권한 분리: 팀장 / 팀원 / 관찰자
- API 레벨 권한 검증 (Spring Security)
- 교수 계정은 데이터 수정 불가 (읽기 + 가중치 조정만)
- GitHub App은 `read` 권한만 설정 (write 원천 차단)

### 13.3 성능
- 한 프로젝트당 태스크 500개, 팀원 10명 기준 응답시간 < 500ms
- API 동기화: Webhook 기반 준실시간 + 폴백 폴링
- 대시보드 차트 렌더링 < 1초
- Claude API: 배치 처리 (10커밋 단위)로 비용 및 Rate Limit 최적화

### 13.4 사용성
- 모바일 브라우저 반응형 지원
- 칸반 보드 드래그 앤 드롭
- 다크 모드 지원 (선택)

### 13.5 배포 & 인프라 ⚡ NEW (v3)
- **Docker Compose**로 전체 스택 관리 (PostgreSQL + Spring Boot + Next.js + Nginx)
- `docker compose up -d` 한 줄로 전체 환경 기동
- Flyway로 DB 마이그레이션 자동 실행 (서버 시작 시)
- 파일 저장: Docker 볼륨 (`uploads`) — 컨테이너 재시작 시에도 영속
- DB 데이터: Docker 볼륨 (`pgdata`) — 컨테이너 재시작 시에도 영속
- Nginx 리버스 프록시: `/api/*` → 백엔드, `/` → 프론트엔드, `/uploads/*` → 정적 파일
- 외부 managed 서비스(Supabase, Vercel, Render) 종속 없음 — 완전 자체 관리

---

## 14. 교수님 Q&A 방어 시나리오 ⚡ IMPROVED

| 예상 질문 | 방어 답변 |
|-----------|-----------|
| "이거 감시 아니냐?" | "감시가 아닙니다. GitHub과 Google이 이미 기록한 로그를 읽어오는 읽기 전용 구조이며, 모든 수집은 명시적 동의 후 진행됩니다." |
| "가중치는 누가 정하나?" | "교수님이 직접 조정합니다. 변경 이력도 기록되어 일관성을 추적할 수 있습니다." |
| "개인정보 문제는?" | "4단계 동의 플로우를 거치며, AI 분석은 별도 선택입니다. GitHub App으로 읽기 권한만 사용하고, 코드 원본은 저장하지 않습니다." |
| "기여도 조작 가능하지?" | "플랫폼 파일은 SHA-256 해시로 고정, 커밋 도배는 통계 이상치 탐지 + AI 품질 분석 + 피어리뷰 크로스체크로 다층 방어합니다." |
| "외부 로그도 위변조 방지 되나?" | "외부 로그의 무결성은 GitHub·Google이 보증합니다. 저희는 그 데이터를 읽기 전용으로 가져옵니다. 플랫폼 내 파일만 저희가 해시로 직접 보증합니다." |
| "GitHub repo scope가 full access 아닌가?" | "OAuth App이 아닌 GitHub App을 사용합니다. `contents: read` 등 개별 권한을 설정할 수 있어서 읽기만 허용됩니다. GitHub 공식 권장 방식입니다." |
| "점수가 공정한가?" | "참고 지표입니다. 팀 평균 기준 정규화 + 상한 클리핑으로 극단값을 방지하고, 교수님이 가중치를 조정합니다. 최종 판단은 교수님의 몫입니다." |
| "오프라인 작업은?" | "수동 작업 신고 기능이 있습니다. 다만 자동 수집보다 70% 신뢰도를 적용하고, 교수님 대시보드에서 수동/자동을 구분 표시합니다." |
| "15주에 가능해?" | "MVP(8주)로 플랫폼 내 기여도 추적 + 해시 증빙 + 교수 대시보드를 먼저 완성합니다. 외부 API 연동과 AI 분석은 이후 단계적으로 추가합니다." |
| "실시간이라며? 폴링이잖아" | "'준실시간'입니다. GitHub Webhook과 Drive Push Notification 기반으로 수 초~수 분 내 반영되며, 폴링은 누락 방지 보정용입니다." |

---

## 부록 A: 발표 시 주의 표현 가이드

| ❌ 피해야 할 표현 | ✅ 정확한 표현 |
|-------------------|----------------|
| "모든 데이터의 위변조를 방지" | "플랫폼 내 파일의 위변조를 방지하고, 외부 로그는 신뢰할 수 있는 제3자 데이터를 읽어옵니다" |
| "실시간 수집" | "준실시간 자동 수집 (Webhook 기반)" |
| "AI가 공정하게 판단" | "AI가 품질 참고 지표를 산출하고, 최종 판단은 교수님이 내립니다" |
| "GitHub 읽기 전용 (repo scope)" | "GitHub App의 `contents: read` 개별 권한으로 읽기 전용 보장" |
| "코드 내용을 수집하지 않습니다" (AI 분석 있으면서) | "코드 메타데이터를 수집합니다. AI 분석 선택 시 변경 내용 일부가 Claude API로 전송되며, 별도 동의를 받습니다" |

---

## 부록 B: 프로젝트 핵심 요약

> **한 줄 요약:** "판단은 사람이, 근거는 시스템이 — 교수님을 시스템의 공동 설계자로 만드는 순간, 이 프로젝트는 우수작이 된다."

**3줄 엘리베이터 피치:**
1. 팀 프로젝트에서 "누가 뭘 했는지" 매번 싸우잖아요.
2. Team Blackbox는 GitHub/Drive 활동 로그를 자동으로 읽어와서, 기여도를 수치로 보여줍니다.
3. 교수님은 대시보드에서 한눈에 확인하고, 가중치도 직접 조정할 수 있습니다.
