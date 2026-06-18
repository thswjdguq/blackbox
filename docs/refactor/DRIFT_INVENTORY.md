# Drift Inventory

> Phase 1 산출물. `md/gc.md` INV/SYNC/CODE 검사 결과 누적.
> 각 행: 등급 분류(Phase 1 §13) → 사용자 결정(§14) → Task 매핑(§15) 순으로 채워짐.

## 표 컬럼 정의
| 컬럼 | 의미 |
|---|---|
| ID | D-INV-NN / D-SYN-NN / D-CODE-NN |
| 출처 | gc.md 검사 항목 (예: INV-02) |
| 문서 진술 | 기대되는 동작/규칙 |
| 코드 실제 | 발견된 위반/패턴 (파일:라인) |
| 등급 | Spec / State / Convention |
| 결정안 | D1(코드 수정) / D2(문서 수정) / D3(의도적 차이로 기록) / D4(사용자 결정) |
| 사용자 결정 | Phase 1 §14에서 확정 |
| Task ID | Phase 1 §15에서 매핑 |

## 실행 로그
| 일시 | 검사 배치 | 실행자 | 결과 행 수 |
|---|---|---|---|
| 2026-06-18 | INV-01~07 | refactor-executor (Sonnet) | 7 |
| 2026-06-18 | SYNC-01~05 | refactor-executor (Sonnet) | 9 |

> 비고: INV-02: 0건(clean) / INV-03: 0건(clean) / INV-05: 0건(clean)

---

## INV (불변 규칙) — Task 10

| ID | 출처 | 문서 진술 | 코드 실제 | 등급 | 결정안 | 사용자 결정 | Task |
|---|---|---|---|---|---|---|---|
| D-INV-01a | INV-01 | 사용자 행동 유발 Service 메서드는 반드시 ActivityLogService.record()를 호출해야 한다 | 후보: `ProjectService.java` — createProject/updateProject/deleteProject/regenerateInviteCode/joinProject/updateMemberRole/kickMember/leaveProject/recordConsent 모두 activityLogService 미주입 (분류 Task 13 유예) | Spec | D1 | | |
| D-INV-01b | INV-01 | 사용자 행동 유발 Service 메서드는 반드시 ActivityLogService.record()를 호출해야 한다 | 후보: `AuthService.java` — signup/updateProfile/changePassword 메서드에 activityLogService 미주입 (분류 Task 13 유예) | Spec | D1 | | |
| D-INV-04 | INV-04 | role=OBSERVER 사용자가 호출 가능한 변경 API는 PUT /weights뿐이어야 한다; ProjectAccessChecker가 OBSERVER 허용 범위를 강제해야 한다 | 후보: `ProjectAccessChecker.java` — requireLeader()/requireMember() 만 존재, OBSERVER 전용 체크 메서드 없음; OBSERVER 역할 검증 로직 부재 (분류 Task 13 유예) | Spec | D1 | | |
| D-INV-06 | INV-06 | 정규화 점수는 0~150 범위를 초과하면 안 되며 Math.min(150, normalized) 클리핑이 모든 항목에 적용되어야 한다 | 후보: `ScoreService.java` — 점수를 boolean participation flags(FULL/PARTIAL/NONE)로만 저장, git_score/doc_score/meeting_score/task_score 컬럼은 존재하나 ScoreService에서 값을 설정하지 않음; Math.min(150,...) 클리핑 코드 전체 코드베이스에 없음 (분류 Task 13 유예) | Spec | D1 | | |
| D-INV-07 | INV-07 | consent_platform=false인 멤버의 활동은 activity_logs에 기록하면 안 된다; ActivityLogService.log() 또는 호출 측에서 동의 여부를 체크해야 한다 | 후보: `ActivityLogService.java:28` — record() 메서드에 consent 체크 로직 없음; TaskService/MeetingService/FileVaultService 호출 측에도 consent 체크 없음; consent 필드는 ProjectMember 엔티티에만 존재 (분류 Task 13 유예) | Spec | D1 | | |

> **INV-02 (file_vault UPDATE/DELETE):** 위반 없음 — grep 결과 0건
> 검사 명령: `grep -rnE 'file_vault' --include='*.java' backend/src/ | grep -iE 'update|delete|remove|\bset\b' | grep -v '//'`

> **INV-03 (외부 API 쓰기 권한):** 위반 없음 — scope/permissions grep 결과 0건
> 검사 명령: `grep -rnE 'scope|Scope' backend/src/main/java/ --include='*.java' | grep -i 'drive\|github'` 및 permissions/contents write/repo 검색 모두 0건

> **INV-05 (Docker 자체완결성):** 위반 없음 — 외부 managed 서비스 URL 0건
> 검사 명령: `grep -rnE 'supabase\.(co|io)|vercel\.app|render\.com|railway\.app|amazonaws\.com|fly\.io' --include='*.java' --include='*.yml' ... backend/ frontend/ docs/ md/` 결과 0건 (docs/_archive/ 제외 기준 충족)

---

## SYNC (크로스파일 일관성) — Task 11

| ID | 출처 | 문서 진술 | 코드 실제 | 등급 | 결정안 | 사용자 결정 | Task |
|---|---|---|---|---|---|---|---|
| D-SYN-01-A | SYNC-01 | DB 스키마 설계 문서(docs/database.md)가 존재해야 Flyway SQL·Entity와 3원 비교가 가능하다 | `docs/database.md` 파일 없음 — ls 결과 none; Flyway 파일 18개, Entity 클래스 17개 (카운트 불일치 +1) | State | D2 | | |
| D-SYN-01-B | SYNC-01 | file_vault의 UPDATE/DELETE 방지 트리거(vault_immutable)가 Flyway SQL에 존재해야 한다 | `V6__init_file_vault.sql`에 `vault_immutable` 트리거 포함 확인 — 일치 (grep 히트) | — | none (clean) | | |
| D-SYN-02-A | SYNC-02 | shared/types.md가 TS 인터페이스·Java DTO의 정의 원본 역할을 해야 한다 | `shared/types.md` 파일 없음 — ls 결과 none; TS types 파일 5개(33 export), Java DTO 45개 — 대응 문서 부재 | State | D2 | | |
| D-SYN-02-B | SYNC-02 | TS 타입(frontend/src/types/*.ts)과 Java DTO(dto/*.java) 카운트가 근사해야 한다 | TS export interface/type 합계 33개(5파일), Java DTO 45개 — 차이 12개; 커버리지 확인 문서 없어 정합성 불명 | Convention | D4 | | |
| D-SYN-03 | SYNC-03 | Controller 엔드포인트(백엔드)·Frontend API 호출·handover_log API 행 수가 근사해야 한다 | Controller @Mapping 68개, Frontend api.* 호출 88개(+20), handover_log API 행 39개(-29); handover_log가 구현 대비 stale할 가능성 높음 | State | D2 | | |
| D-SYN-04 | SYNC-04 | application.yml 참조 환경변수 전부가 docker-compose.yml과 .env.example에 기재되어야 한다 | application.yml 참조 21개 중 `FRONTEND_BASE_URL`, `GOOGLE_REDIRECT_URI`가 docker-compose.yml과 .env.example 양쪽 모두 누락; `DISCORD_WEBHOOK_URL`은 .env.example에만 있고 docker-compose.yml에 없음 | Spec | D1 | | |
| D-SYN-05-A | SYNC-05 | 기획서 §5.3.1~5.3.2: 기여도 수식 = Σ(항목별점수×가중치), 정규화 = min(150, (개인/팀평균)×100) | `ScoreService.java`: boolean participation flags(FULL/PARTIAL/NONE)만 저장; git_score/doc_score 등 항목별 점수 미설정; 0~150 정규화 로직 없음 — D-INV-06 교차 확인 | Spec | D1 | | |
| D-SYN-05-B | SYNC-05 | 기획서 §5.3.1: 가중치 기본값 w1=0.30, w2=0.25, w3=0.20, w4=0.25 | `ContributionScore.java:49-59`: weightGit=0.30, weightDoc=0.25, weightMeeting=0.20, weightTask=0.25 — 기본값 일치. 단 ScoreService에서 해당 가중치를 실제 점수 계산에 사용하지 않음 (D-INV-06 연계) | D4 | D4 | | |
| D-SYN-05-C | SYNC-05 | 기획서 §5.4.1: 경보 임계값 — 편차>40% = FREE_RIDE, 2주 활동 없음 = DROPOUT, 60% 이상 작업 = OVERLOAD | `AlertService.java`: FREE_RIDE = NONE level(boolean 미참여) — 40% 편차 수식 없음; DROPOUT = 14일(`minusDays(14)`) — 일치; OVERLOAD = FULL 1명+nonFull 2명이상 — 60% 기준 없음; 기준 상이 | Spec | D1 | | |
| D-SYN-05-D | SYNC-05 | 기획서 §9.1: 동의 4단계(플랫폼/GitHub/Drive/AI분석) 온보딩 UI가 프론트엔드에 구현되어야 한다 | `ConsentRequest.java`: 4필드(consentPlatform/Github/Drive/AiAnalysis) 존재, `POST /{projectId}/consent` API 존재 — 백엔드 4단계 구조 일치; 프론트엔드 TSX 29개 파일 전체 검색 결과 consent/onboard 구현 UI 없음 | Spec | D1 | | |
| D-SYN-05-E | SYNC-05 | 기획서 §4.2 기술 스택: Next.js, Spring Boot, PostgreSQL, Flyway, GitHub App API, Google Drive API, Claude API, Docker Compose, Nginx — Notion은 미기재 | Notion API 통합 구현됨 — `NotionSettingController.java`, `NotionSyncResponse.java` 등 다수 파일 존재; `NOTION_API_KEY`, `NOTION_PARENT_PAGE_ID`, `NOTION_CALENDAR_DB_ID`가 docker-compose.yml·application.yml에 존재; 기획서 기술 스택에 Notion 없음 | State | D2 | | |

## CODE (금지 패턴) — Task 12에서 채움

(placeholder)
