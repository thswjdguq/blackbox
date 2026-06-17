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

## SYNC (크로스파일 일관성) — Task 11에서 채움

(placeholder)

## CODE (금지 패턴) — Task 12에서 채움

(placeholder)
