# 🤝 Team Blackbox — Agent Handover Log

> **목적:** 이 문서는 Claude 에이전트 간 컨텍스트 공유를 위한 핸드오버 기록이다.
> 새로운 Claude 세션이 시작될 때, **이 파일을 가장 먼저 읽어라.**
> 프로젝트 루트: `<repo>`

---

## 📌 Meta

| 항목 | 내용 |
|------|------|
| 마지막 업데이트 | 2026-05-04 (KST) |
| 작업자 | Antigravity Agent (Claude Sonnet 4.6 Thinking) |
| 저장소 위치 | `<repo>` |
| 전체 todo | `md/todo.md` |

---

## 1. Current Status

### 1.1 전체 MVP 진행률: **약 85%**

| 영역 | 상태 |
|------|------|
| 인프라 (Docker/Nginx/Flyway) | ✅ ~87% (실 기동 검증 미완) |
| 백엔드 전체 (Auth/Project/Task/Meeting/Vault/Score/Alert) | ✅ **100%** |
| 프론트 인증 + 프로젝트 대시보드 | ✅ 완료 |
| 프론트 칸반 보드 | ✅ 완료 |
| **프론트 회의록 UI** | ✅ **방금 완료** |
| **프론트 Hash Vault UI** | ✅ **방금 완료** |
| **프론트 성과 대시보드** | ✅ **방금 완료** |
| PDF 무결성 리포트 (백엔드 + 프론트) | ❌ 미구현 |
| 칸반 보드 필터 | ❌ 미구현 |
| Docker 배포 안정화 | ❌ 미구현 |

### 1.2 방금 완료한 작업 (이번 세션)

1. **타입 파일 추가**: `types/meeting.ts`, `types/vault.ts` (Alert, Score 포함)
2. **회의록 목록** (`/projects/[id]/meetings/page.tsx`) — 월별 그룹, 생성 모달
3. **회의 상세** (`/projects/[id]/meetings/[meetingId]/page.tsx`) — 체크인 코드, 참석자, 회의록 편집, 액션아이템
4. **Hash Vault** (`/projects/[id]/vault/page.tsx`) — 드래그 업로드, SHA-256 해시, 버전 이력 아코디언, 변조 감지
5. **성과 대시보드** (`/projects/[id]/analytics/page.tsx`) — 순위, 막대 차트, 레이더 차트, 경보
6. **리다이렉트 shim** — `/meetings`, `/vault`, `/analytics` → 첫 프로젝트로 자동 이동


---

## 2. Key Changes

### 2.1 새로 생성된 파일

```
frontend/src/
├── types/
│   └── task.ts                         ← 백엔드 DTO 1:1 TypeScript 타입 정의
├── components/kanban/
│   ├── TaskCard.tsx                    ← 개별 태스크 카드
│   ├── KanbanColumn.tsx                ← 컬럼 (드롭존 포함)
│   ├── KanbanBoard.tsx                 ← DnD 오케스트레이터 (핵심)
│   └── TaskModal.tsx                   ← 생성/수정/삭제 모달
└── app/
    ├── board/page.tsx                  ← 사이드바 /board 링크 → 첫 프로젝트 자동 리다이렉트
    └── projects/[projectId]/board/
        └── page.tsx                    ← 실제 칸반 보드 페이지
```

### 2.2 수정된 파일

| 파일 | 변경 내용 |
|------|------|
| `frontend/src/app/dashboard/page.tsx` | 프로젝트 카드 클릭 → `/projects/:id/board`로 라우팅 변경 |
| `md/todo.md` | Phase 3 칸반 4개 항목 [x], Phase 5 전체 교체 |

### 2.3 핵심 로직 — KanbanBoard.tsx

**낙관적 업데이트 + API 퍼시스트 패턴:**
```
dragStart  →  activeTask 상태 세팅 (ghost 표시)
dragOver   →  setTasks() 즉시 호출 (UI 즉각 반응, status 변경)
dragEnd    →  PATCH /api/projects/:id/tasks/:taskId/status 호출
             ↳ 실패 시 initialTasks로 롤백 (일관성 보장)
```

**ScoreMap 패턴:**
```
{ [userId: string]: number }  형태로 점수 캐시
→ TaskCard에 props로 전달
→ 첫 번째 담당자의 totalScore를 점수 칩으로 표시
→ 게이지: Teal(≥120) / Indigo(≥80) / Slate(≥50) / Rose(<50)
```

**TaskModal 삭제 UX:**
```
"삭제" 클릭 → confirmDelete=true → "정말 삭제?" 표시
→ 확인 클릭 → DELETE /api/projects/:id/tasks/:taskId
(단순 실수 삭제 방지)
```

### 2.4 백엔드 API 전체 목록 (프론트와 연동된 것)

> 모든 API는 `baseURL: /api`로 프리픽스됨 (Nginx가 `/api` → `:8080` 프록시)
> 인증: `Authorization: Bearer {accessToken}` 헤더 필수 (api.ts 인터셉터에서 자동 주입)

#### Auth
| Method | Path | 설명 |
|--------|------|------|
| POST | `/auth/signup` | 회원가입 → `{accessToken, refreshToken, expiresIn}` |
| POST | `/auth/login` | 로그인 |
| POST | `/auth/refresh` | 토큰 갱신 |

#### Projects
| Method | Path | 설명 |
|--------|------|------|
| GET | `/projects` | 내 프로젝트 목록 |
| POST | `/projects` | 프로젝트 생성 |
| GET | `/projects/:id` | 프로젝트 상세 |
| PATCH | `/projects/:id` | 프로젝트 수정 |
| DELETE | `/projects/:id` | 프로젝트 삭제 |
| POST | `/projects/:id/invite-code/regenerate` | 초대 코드 재생성 |
| POST | `/projects/join` | 초대 코드로 참여 `{inviteCode}` |
| GET | `/projects/:id/members` | 멤버 목록 → `MemberResponse[]` |
| PATCH | `/projects/:id/members/:memberId/role` | 멤버 역할 변경 |
| DELETE | `/projects/:id/members/:memberId` | 멤버 추방 |
| DELETE | `/projects/:id/members/me` | 본인 탈퇴 |
| POST | `/projects/:id/consent` | 데이터 수집 동의 기록 |

#### Tasks (칸반 보드와 직접 연동)
| Method | Path | 설명 |
|--------|------|------|
| POST | `/projects/:id/tasks` | 태스크 생성 `{title, description?, priority?, tag?, dueDate?, assigneeIds?}` |
| GET | `/projects/:id/tasks` | 태스크 목록 `?status=TODO\|IN_PROGRESS\|DONE` |
| GET | `/projects/:id/tasks/:taskId` | 태스크 상세 |
| PATCH | `/projects/:id/tasks/:taskId` | 태스크 수정 (부분) |
| DELETE | `/projects/:id/tasks/:taskId` | 태스크 삭제 |
| PATCH | `/projects/:id/tasks/:taskId/status` | **상태 전환** `{status}` ← 드래그 드롭 시 호출 |
| PUT | `/projects/:id/tasks/:taskId/assignees` | 담당자 교체 `{assigneeIds}` |

#### Meetings
| Method | Path | 설명 |
|--------|------|------|
| POST | `/projects/:id/meetings` | 회의 생성 `{title, scheduledAt, location?, agenda?}` |
| GET | `/projects/:id/meetings` | 회의 목록 |
| GET | `/projects/:id/meetings/:meetingId` | 회의 상세 |
| PATCH | `/projects/:id/meetings/:meetingId` | 회의 수정 `{title?, content?, decisions?, scheduledAt?, location?, agenda?}` |
| DELETE | `/projects/:id/meetings/:meetingId` | 회의 삭제 |
| POST | `/projects/:id/meetings/:meetingId/checkin-code/regenerate` | 체크인 코드 재생성 |
| GET | `/projects/:id/meetings/:meetingId/attendees` | 참석자 목록 |
| POST | `/projects/:id/meetings/:meetingId/action-items` | 회의 → 태스크 생성 |
| POST | `/meetings/checkin` | 체크인 코드로 체크인 `{checkinCode}` |

#### Hash Vault (파일)
| Method | Path | 설명 |
|--------|------|------|
| POST | `/projects/:id/files` | 파일 업로드 (multipart: `file`) |
| GET | `/projects/:id/files` | 파일 목록 (최신순) |
| GET | `/projects/:id/files/history?fileName=` | 파일 버전 이력 |
| GET | `/files/:fileId/download` | 파일 다운로드 (스트리밍) |

#### Score & Alert
| Method | Path | 설명 |
|--------|------|------|
| GET | `/projects/:id/scores` | 멤버별 기여도 점수 `ScoreResponse[]` |
| POST | `/projects/:id/scores/recalculate` | 수동 재계산 트리거 |
| GET | `/projects/:id/alerts` | 경보 목록 `AlertResponse[]` |

#### Misc
| Method | Path | 설명 |
|--------|------|------|
| GET | `/health` | Docker 헬스체크 |

---

## 3. New Dependencies

### 이번 세션에서 추가된 의존성

```json
// frontend/package.json에 추가됨
"@dnd-kit/core": "latest",
"@dnd-kit/sortable": "latest",
"@dnd-kit/utilities": "latest"
```

**선택 이유:** `react-beautiful-dnd`는 React 18 StrictMode에서 이슈가 있음.
`@dnd-kit`는 React 18 + Next.js App Router와 완전 호환, pointer/touch 이벤트 모두 지원, SSR-safe.

### 기존 의존성 (세션 전부터)
```json
"axios": "^1.7.9",
"lucide-react": "^0.577.0",
"next": "^16.2.1",
"react": "^18",
"zustand": "^5.0.3",
"tailwindcss": "^3.4.1"
```

---

## 4. Technical Decisions

### 4.1 교수 대시보드 → PDF 리포트 방식 전환

**결정 내용:** Phase 5에서 교수 전용 실시간 대시보드를 제거하고, 팀원이 생성하는 무결성 PDF 보고서로 대체.

**이유:**
- 교수 전용 ROLE 구현 불필요 → User 엔티티는 현재 `STUDENT/PROFESSOR/TA` 필드는 있으나 백엔드 분기 로직 미구현. 기능 추가 시 SecurityConfig와 모든 API에 역할 검증 추가 필요 — 복잡도가 큼.
- Hash Vault의 핵심 철학(불변성, SHA-256 증거력)과 개념적으로 일관됨.
- 학생 프라이버시 보호 + 실시간 감시 부작용(Gaming 현상) 방지.
- 프론트엔드 라우팅 복잡도 감소 (교수/학생 이중 레이아웃 불필요).

### 4.2 낙관적 업데이트 (Optimistic Update) 패턴

**결정 내용:** 드래그 앤 드롭 시 UI를 즉시 업데이트하고, 백엔드 응답은 비동기로 처리.

**이유:** 네트워크 지연이 있을 경우 카드가 원래 자리로 되돌아가는 UX는 매우 불쾌함.
낙관적으로 업데이트하고, API 실패 시만 `initialTasks`로 롤백하는 방식이 최선.

**주의:** `initialTasks`는 `KanbanBoard` prop으로 전달됨. 드래그 도중 외부에서 tasks가 바뀌면 롤백 데이터가 stale해질 수 있음 → 향후 useRef로 최신 initialTasks를 추적하는 방식으로 개선 가능.

### 4.3 ScoreMap을 별도 상태로 분리

**결정 내용:** Score 데이터를 Task 데이터와 분리하여 `scoreMap: { [userId]: number }` 형태로 관리.

**이유:** 점수는 30분마다 스케줄러로 재계산됨(백엔드). 태스크와 라이프사이클이 다름.
태스크를 새로고침한다고 점수가 바뀌지 않으며, 점수 재계산 버튼 클릭 시에만 갱신하면 됨.
분리하지 않으면 태스크 DnD 시마다 불필요한 score API 호출이 발생함.

### 4.4 `AssigneeSummary` 아바타 색상 결정

**결정 내용:** `hsl(name charCode sum % 360, 55%, 45%)` 공식으로 deterministic 색상 생성.

**이유:** 외부 아바타 이미지 없이 이름만으로 일관된 색상을 부여. 같은 이름은 항상 같은 색. 서버 왕복 없이 클라이언트에서 즉시 계산 가능.

### 4.5 카드 드래그 핸들 분리

**결정 내용:** 카드 전체 `onClick = openEdit`, 드래그 핸들 버튼(`GripVertical`)으로만 드래그 가능.

**이유:** 카드 클릭으로 편집 모달을 열고, 드래그는 핸들로만 가능하게 분리. `PointerSensor`의 `activationConstraint: { distance: 5 }`와 함께 사용하여 클릭 vs 드래그 의도를 명확히 구분.

---

## 5. Next Steps for Claude Code

> **다음 에이전트가 바로 이어서 해야 할 작업 (우선순위 순서)**

### 🥇 최우선: 회의록 UI 구현

**작업 위치:** `frontend/src/app/projects/[projectId]/meetings/`

**필요한 페이지:**
1. `page.tsx` — 회의 목록 (날짜별 그룹, 체크인 코드 표시)
2. `[meetingId]/page.tsx` — 회의 상세 (참석자, 안건, 내용, 결정사항, 액션아이템)

**사용할 API:**
```
GET  /projects/:id/meetings          → MeetingResponse[]
POST /projects/:id/meetings          → 새 회의 생성
PATCH /projects/:id/meetings/:mid    → 내용 수정
POST /projects/:id/meetings/:mid/checkin-code/regenerate  → QR용 코드 재발급
GET  /projects/:id/meetings/:mid/attendees                → 참석자 목록
POST /projects/:id/meetings/:mid/action-items             → 태스크 생성
POST /meetings/checkin               → { checkinCode } 로 체크인
```

**MeetingResponse 구조 (백엔드 DTO):**
```java
// MeetingResponse.java
record MeetingResponse(
  UUID id, UUID projectId,
  String title, String content, String decisions, String agenda,
  String location, OffsetDateTime scheduledAt,
  String checkinCode, boolean isOpen,
  UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt
)
```

**디자인 힌트:** 회의을 타임라인 형태로 표시. 체크인 코드는 큰 폰트(`font-mono`)로 표시하여 QR 없이도 입력 가능하게. 액션아이템 → 칸반 태스크 생성 버튼은 mint(`#2DD4BF`) 색상으로.

---

### 🥈 두 번째: Hash Vault UI

**작업 위치:** `frontend/src/app/projects/[projectId]/vault/`

**필요한 페이지:**
1. `page.tsx` — 파일 목록 + 업로드 UI

**사용할 API:**
```
POST /projects/:id/files             → FormData { file }  (multipart)
GET  /projects/:id/files             → FileHistoryResponse[]
GET  /projects/:id/files/history?fileName=  → 버전 이력
GET  /files/:fileId/download         → 파일 다운로드
```

**FileHistoryResponse 구조:**
```java
record FileHistoryResponse(
  UUID id, UUID projectId,
  String fileName, String fileHash, long fileSize,
  int version, boolean immutable,
  UUID uploaderId, String uploaderName,
  OffsetDateTime uploadedAt
)
```

**FileUploadResponse 구조:**
```java
record FileUploadResponse(
  UUID id, String fileName, String fileHash,
  long fileSize, int version,
  boolean tamperDetected,  // ← 중요! 변조 감지 여부
  String storagePath, OffsetDateTime uploadedAt
)
```

**중요 UX 포인트:**
- `tamperDetected: true`이면 업로드 후 즉시 경고 배너 표시 (`TAMPER` alert)
- 파일 이력 타임라인: 버전별로 해시값(`font-mono text-xs`) + 업로드 시각 표시
- 업로드는 `multipart/form-data` → `axios`의 일반 `api.post`가 아닌 `Content-Type: multipart/form-data` 헤더 별도 설정 필요:
  ```typescript
  const formData = new FormData();
  formData.append("file", file);
  await api.post(`/projects/${projectId}/files`, formData, {
    headers: { "Content-Type": "multipart/form-data" }
  });
  ```

---

### 🥉 세 번째: 성과 대시보드 + PDF 리포트 UI

**작업 위치:** `frontend/src/app/projects/[projectId]/analytics/`

**사용할 API:**
```
GET  /projects/:id/scores            → ScoreResponse[]
POST /projects/:id/scores/recalculate → 수동 재계산
GET  /projects/:id/alerts            → AlertResponse[]
```

**ScoreResponse 구조:**
```java
record ScoreResponse(
  UUID userId, String name, String email,
  BigDecimal gitScore, BigDecimal docScore,
  BigDecimal meetingScore, BigDecimal taskScore,
  BigDecimal totalScore,
  BigDecimal weightGit, BigDecimal weightDoc,
  BigDecimal weightMeeting, BigDecimal weightTask,
  OffsetDateTime calculatedAt
)
```

**AlertResponse 구조:**
```java
record AlertResponse(
  UUID id, UUID projectId, UUID userId,  // userId null이면 프로젝트 전체 경보
  String alertType,  // FREE_RIDE | OVERLOAD | DROPOUT | TAMPER
  String severity,   // LOW | MEDIUM | HIGH
  String message,
  boolean isRead,
  OffsetDateTime createdAt
)
```

**차트:** Recharts를 사용할 것. `npm install recharts @types/recharts` 필요.

**PDF 리포트 (백엔드 미구현):** 백엔드 `ReportService`를 먼저 구현해야 함.
라이브러리 선택: **iText7 (Java)** or **Puppeteer (Node 사이드카)** 중 선택.
팀 컨텍스트상 Java 백엔드 내에서 iText7로 구현하는 게 아키텍처 일관성에 좋음.
→ `build.gradle`에 `implementation 'com.itextpdf:itext7-core:8.0.3'` 추가.

---

### ⚠️ 그 외 알아야 할 사항

#### Sidebar 내비게이션 현황
```typescript
const NAV_ITEMS = [
  { href: "/dashboard",  label: "내 프로젝트" },  // ✅ 구현됨
  { href: "/board",      label: "칸반 보드" },     // ✅ /board→첫프로젝트 리다이렉트
  { href: "/meetings",   label: "회의록" },        // ❌ 페이지 없음
  { href: "/vault",      label: "Hash Vault" },    // ❌ 페이지 없음
  { href: "/analytics",  label: "기여도" },        // ❌ 페이지 없음
];
```
> `/meetings`, `/vault`, `/analytics`도 `board/page.tsx`처럼 첫 번째 프로젝트로 리다이렉트하는 shim 페이지 먼저 만들거나, Sidebar를 project-scoped URL로 변경해야 함.

#### 인증 패턴
```typescript
// 모든 보호된 페이지 상단에 필요
useEffect(() => {
  const token = localStorage.getItem("accessToken");
  if (!token) { router.replace("/login"); return; }
  // ...
}, []);
```
`api.ts`의 응답 인터셉터가 401 시 자동으로 refresh → 실패 시 `/login` 리다이렉트.

#### 브랜딩 & 디자인 시스템
| 컬러 | 용도 |
|------|------|
| `#6366F1` (indigo-500) | 메인 액션, 드래그 하이라이트, CTA 버튼 |
| `#2DD4BF` (teal-400) | 완료 상태, mint 포인트, Hash Vault 아이콘 |
| `slate-950` | 페이지 배경 |
| `slate-800/700` | 카드/패널 배경 |
| `slate-200` | 기본 텍스트 |

모든 신규 페이지는 반드시 `<Sidebar />`를 포함하고 `ml-64` 마진을 적용할 것.

#### Docker 미검증 사항
현재 `docker compose up -d`로 전체 스택을 실제 기동해 본 적이 없음. MVP 완성 전 반드시 테스트 필요:
```bash
# 프로젝트 루트에서
cp .env.example .env  # DB_PASSWORD, JWT_SECRET 설정 필수
docker compose up -d
# 확인: http://localhost/ 접속 → 로그인 페이지
```

---

## 6. Architecture Overview (현재 시점)

```
<repo>/
├── docker-compose.yml          ← PostgreSQL + Spring + Next.js + Nginx
├── nginx/default.conf          ← /api → :8080, / → :3000
├── .env                        ← DB_PASSWORD, JWT_SECRET 등
│
├── backend/                    ← Spring Boot (Java 17, Gradle)
│   └── src/main/java/com/blackbox/
│       ├── config/             ← SecurityConfig (JWT + CORS), SchedulingConfig
│       ├── controller/         ← Auth, Project, Task, Meeting, FileVault, Score, Health
│       ├── dto/                ← Request/Response records (25개)
│       ├── entity/             ← JPA 엔티티 (15개)
│       ├── repository/         ← JPA Repository (13개)
│       ├── security/           ← JwtService, JwtFilter, JwtProperties
│       ├── service/            ← 비즈니스 로직 (11개 서비스)
│       └── scheduler/          ← ScoreScheduler (30분마다 recalculate)
│
├── frontend/                   ← Next.js 16, TypeScript, Tailwind
│   └── src/
│       ├── app/
│       │   ├── login/          ← 로그인 페이지
│       │   ├── signup/         ← 회원가입 페이지
│       │   ├── dashboard/      ← 내 프로젝트 목록 + 생성/참여 모달
│       │   ├── board/          ← /board 리다이렉트 shim
│       │   └── projects/[projectId]/
│       │       └── board/      ← ✅ 칸반 보드 (NEW)
│       ├── components/
│       │   ├── Sidebar.tsx
│       │   └── kanban/         ← ✅ KanbanBoard, KanbanColumn, TaskCard, TaskModal (NEW)
│       ├── lib/
│       │   ├── api.ts          ← Axios + 인터셉터 (토큰 첨부, 401 갱신)
│       │   ├── db.ts
│       │   └── store/
│       │       └── authStore.ts ← Zustand (accessToken, refreshToken)
│       └── types/
│           ├── task.ts          ← ✅ Task, ScoreMap, KANBAN_COLUMNS (NEW)
│           └── database.ts
│
└── md/
    └── todo.md                  ← 항상 최신 진행 상태 유지
```

---

## 7. Known Issues & Gotchas

1. **칸반 필터 미구현**: 담당자/태그/우선순위 필터 버튼이 없음. `KanbanBoard.tsx`에 `filterFn`을 추가하고 상단에 `FilterBar` 컴포넌트를 달면 됨.

2. **Sidebar 링크가 project-scoped가 아님**: `/meetings`, `/vault`, `/analytics`는 아직 전역 링크임. 현재 보고 있는 프로젝트 ID를 기억하는 전역 상태(예: `projectStore`)를 Zustand에 추가하거나, Sidebar에 현재 `projectId`를 prop으로 주입하는 방식으로 해결 가능.

3. **토큰 저장 방식**: 현재 `localStorage`에 저장. XSS에 취약. 보안이 중요하면 `httpOnly cookie`로 변경해야 하지만 MVP 데모 단계에서는 현재 방식 유지.

4. **백엔드 빌드 미검증**: Spring Boot 컴파일은 확인했으나 `docker compose up` 전체 실 기동은 아직 테스트 안 됨. 특히 Flyway 마이그레이션과 파일 볼륨(`/data/uploads`) 마운트 확인 필요.

5. **ScoreResponse의 BigDecimal**: 백엔드에서 `BigDecimal`로 내려오는 값이 JSON에서 문자열처럼 보일 수 있음. `Number(score.totalScore)`로 변환하는 코드가 `board/page.tsx`에 이미 있음. 다른 페이지에서도 동일하게 처리할 것.

---

*이 로그는 매 주요 기능 구현 완료 시 업데이트되어야 한다.*
*마지막 업데이트: 2026-03-30 — Antigravity Agent*

---

## 8. Bug Fix Log — 2026-05-04

### 8.1 회의록 수정 저장 버그 수정

**증상:**
- 회의록 내용 수정 후 저장하면 내용이 날아가거나 저장이 안 됨
- 노션 연동 해제 후 저장하면 됨
- 노션 내보내기 후 재수정해도 Notion 페이지에는 이전 내용이 유지됨

**원인 분석 (3개 버그):**

| # | 파일 | 원인 |
|---|------|------|
| 1 | `[meetingId]/page.tsx` `saveNow()` | 저장 성공 후 `setEditNotes(data.notes ?? "")` 로 서버 응답값이 textarea를 덮어씀 → 서버가 null 반환 시 입력 내용이 초기화됨 |
| 2 | `MeetingService.java` `updateMeeting()` | `""` 빈 문자열이 `!= null` 체크를 통과해 DB에 빈 값으로 저장됨 |
| 3 | Notion 내보내기 UX | Notion API는 기존 페이지 UPDATE가 없고 항상 새 페이지 CREATE → 사용자가 이전 URL을 보면 변경 없어 보임 |

**수정 내용:**

1. **`frontend/.../[meetingId]/page.tsx`** — `saveNow()` 함수:
   - `setEditNotes(data.notes ?? "")` / `setEditDecisions(data.decisions ?? "")` **제거** (서버 응답으로 textarea 덮어쓰기 방지)
   - `notes: editNotes ?? ""` → `notes: editNotes.trim() || undefined` 로 변경 (빈 문자열은 undefined로 전송, 백엔드 skip 처리)

2. **`backend/.../MeetingService.java`** — `updateMeeting()`:
   - `req.notes().isBlank() ? null : req.notes()` 패턴으로 빈 문자열 → null 변환

3. **`frontend/.../[meetingId]/page.tsx`** — `handleNotionExport()`:
   - 이미 내보낸 적 있을 때 (`notionUrl` 존재) `window.confirm()` 으로 "새 페이지가 생성됩니다" 사전 안내

**백엔드 재빌드 필요:** `MeetingService.java` 수정으로 `docker compose up -d --build backend` 실행 필요.

*수정: 2026-05-04 — Antigravity Agent (Claude Sonnet 4.6 Thinking)*

---

## 9. Bug Fix Log — 2026-05-04 (Session 2)

### 9.1 `POST /api/calendar/recommend` 403 Forbidden 원인 분석 및 수정

**증상:**
- 팀원 중 1명만 Google Calendar 연동, 나머지 2명 미연동 상태
- `POST /api/calendar/recommend` 호출 시 403 Forbidden 반환

**원인 분석 — 3단계 체인:**

| 단계 | 위치 | 내용 |
|------|------|------|
| 1 | `CLAUDE_API_KEY=` (빈 값) | ClaudeService.callClaude()에서 `IllegalStateException` throw |
| 2 | `GlobalExceptionHandler` | `IllegalStateException` 핸들러 없음 |
| 3 | Spring Security `ExceptionTranslationFilter` | 미처리 예외를 가로채 403으로 변환 |

**보조 원인 (Google 토큰 만료 시 추가 발생 가능):**
- `refreshAccessToken()` 내 `WebClient.retrieve()`에 에러 핸들러 없음
- Google API가 403 반환 시 `WebClientResponseException` 이 전파되어 전체 API 503/403으로 이어짐

**수정한 파일:**

1. **`GoogleCalendarService.java`** — WebClient 3계층 방어
   - `refreshAccessToken()`: `.onErrorReturn(Map.of())` 추가 → Google 4xx 시 예외 전파 차단
   - `ensureFreshToken()`: `newAccessToken != null` 체크 → 갱신 실패 시 기존 토큰 유지
   - `recommendMeetingTimes()` / `createCalendarEvents()`: `ifPresent` 람다를 try-catch로 감싸 → 토큰 오류 시 해당 멤버만 스킵

2. **`OpenAiService.java`** — `rawCall(String, int)` 메서드 추가 (ClaudeService와 동일 시그니처)

3. **`GoogleCalendarService.java`** — AI 추천 호출을 Claude → OpenAI 폴백 구조로 교체
   ```java
   if (claudeService.isConfigured()) {
       raw = claudeService.rawCall(prompt, 1000);
   } else if (openAiService.isConfigured()) {
       raw = openAiService.rawCall(prompt, 1000);
   } else {
       throw new IllegalStateException("AI API 키 미설정");
   }
   ```

**현재 `.env` 상태:**
- `CLAUDE_API_KEY=` → 비어 있음 (Claude 스킵)
- `OPENAI_API_KEY=sk-proj-...` → 입력됨 ✅ → **gpt-4o-mini** 사용 중

---

### 9.2 Docker Hub DNS 오류 시 빌드 우회법

**증상:** `docker compose up -d --build backend` 실행 시 DNS 오류
```
lookup registry-1.docker.io: no such host
```

**원인:** Docker Desktop의 DNS 설정 이슈 (네트워크 상태에 따라 간헐적 발생)

**해결 — 캐시된 base 이미지 재사용:**
```bash
# docker compose build 대신 buildx 사용
docker buildx build --pull=false --no-cache -t blackbox-backend -f backend/Dockerfile backend/
docker compose up -d backend
```

- `--pull=false`: Docker Hub에서 새 이미지 다운로드 시도 안 함 (로컬 캐시 사용)
- `--no-cache`: 이전 소스 캐시 무시 → 변경된 소스 재컴파일 강제

> ⚠️ PowerShell에서는 `&&` 대신 `;` 로 명령어 연결할 것

*수정: 2026-05-04 — Antigravity Agent (Claude Sonnet 4.6 Thinking)*
