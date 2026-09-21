# 파일 소유권과 변경 경계

원칙: **한 파일의 활성 작성자는 한 명**이다. 읽기·리뷰는 전원 가능하다. 소유자는 영구 독점자가 아니라 변경을 조정하는 담당자다. 아래 경로는 저장소 기준 상대 경로다.

## 1. 담당별 수직 분담

| 담당 | 책임 | 다른 담당에게 제공 |
| --- | --- | --- |
| 송승준 A | 제출물·요구사항·검토 회차·최종 제출, 인증/권한 기반, 파일 저장, DB·CI·실행 환경 | 검토/제출 계약·파일 버전 조회·DB 변경 |
| 손정협 B | 업무·피드백 코멘트·업무 전환·진척 조회·활동 기록·점수·PM 브리핑 로직 | 피드백 처리 API·진척 응답·미해결 항목 조회 |
| 손정효 C | 전체 프론트·화면 상태·사용자 흐름·모바일·클라이언트 타입 | 계약 검토·mock 화면·실제 사용자 흐름 검증 |

각 서버 도메인 담당자가 Controller·Service·DTO·Entity·Repository·테스트를 함께 작성한다. A가 엔티티를 먼저 다 만들어야 B가 개발하는 방식은 사용하지 않는다. 스키마는 A가 SQL 작성하되, B가 필요한 컬럼·제약 초안을 계약 PR에 함께 제시한다.

## 2. 현재 파일의 소유권

Java 루트는 `backend/src/main/java/com/blackbox/`다. 아래 클래스는 해당 이름의 controller/service/dto/entity/repository 파일을 포함한다. 모호한 파일은 추측해 수정하지 말고 Task에서 단일 작성자를 정한다.

| 경로/파일군 | 작성자 | 비고 |
| --- | --- | --- |
| `controller/DeliverableController.java`, `service/DeliverableService.java`, `dto/DeliverableDtos.java` | A | 요구사항 관리도 A. B가 같은 서비스에 진척 계산을 끼워 넣지 않음 |
| `entity/Deliverable*.java`, `repository/Deliverable*.java` | A | 기존 V19 대응. 다른 담당은 조회 계약 이용 |
| `controller/TaskController.java`, `service/TaskService.java`, `entity/Task*.java`, `repository/Task*.java` | B | DTO `CreateTaskRequest`, `UpdateTaskRequest`, `TaskResponse`, `AssignTaskRequest`, `UpdateTaskStatusRequest` 포함 |
| `ActivityLog*`, `Score*`, `ContributionScore*`, `Risk*`, `Alert*`, `WeightConfig*`, `scheduler/ScoreScheduler.java` | B | 점수 재설계는 별도 범위 승인 후 |
| `Project*`, `User*`, `Auth*`, `security/**`, `config/**`, `exception/**` | A | `ProjectAccessChecker` 및 회원 DTO 포함 |
| `FileVault*`, `FileStorage*`, `Hash*`, `Report*`, `EvidencePackage*`, `TamperDetection*` | A | 파일 불변성 유지 |
| `Meeting*`, `Notion*`, `Calendar*`, `Google*`, `Discord*`, `scheduler/DiscordReminderScheduler.java` | B | 기존 외부 연동 유지. 구조 변경은 현재 Task와 분리 |
| `ClaudeService.java`, `OpenAiService.java`, AI 호출 기반 신설 파일 | A | 소비 로직인 회의/PM은 B. 공급자 호출을 중복 구현하지 않음 |
| `frontend/**` | C | 페이지·컴포넌트·상태·타입·API 래퍼·의존성 lockfile 포함 |
| `backend/src/main/resources/db/migration/**` | A | 유일한 마이그레이션 번호 배정자 |
| `backend/build.gradle`, Gradle wrapper, `backend/src/main/resources/application*.yml` | A | JDK/의존성 변경 별도 PR |
| `docker-compose*.yml`, `nginx/**`, 실행 스크립트, `.github/workflows/**` | A | 비밀값을 커밋하지 않음 |
| `backend/src/test/**` | 대응 도메인 담당 | 기존 `TaskDeliverableTest` B, `DeliverableServiceTest` A. `BlackboxApplicationTests` A |

신규 도메인 파일(아직 구현하지 않은 이름)은 다음처럼 예약한다.

| 예정 파일군 | 작성자 | 교차 경계 |
| --- | --- | --- |
| `ReviewController/Service/Dtos/Review/ReviewRepository` | A | 회차와 파일 스냅샷만 |
| `ReviewCommentController/Service/Dtos/ReviewComment/ReviewCommentRepository` | B | 피드백·반영 확인·업무 전환 |
| `SubmissionController/Service/Dtos/Submission/SubmissionRepository` | A | 최종 확정·제출 기록 |
| `DeliverableProgressController/Service/Dtos` | B | 별도 조회 API. A의 DeliverableDtos와 서비스 수정 불필요 |
| `PmBriefing*` | B | A의 모델 호출 계약을 이용하는 비즈니스 로직 |

이는 지금 패키지 전체를 이동하라는 지시가 아니다. 신규 파일 이름·서명은 해당 계약 PR에서 최종 확정한다.

## 3. 공유 파일과 문서

| 경로 | 기본 편집자 | 리뷰 |
| --- | --- | --- |
| `AGENTS.md`, `CLAUDE.md`, `.github/copilot-instructions.md`, `DEVELOPMENT.md` | A | B·C 중 영향받는 담당 |
| `docs/team/DEVELOPMENT_PLAN.md`, `OWNERSHIP.md`, `WORKFLOW.md`, `README.md` | A | 전원 변경 확인 |
| `docs/team/CONTRACTS.md` | A(공통 색인) | 관련 생산자+소비자 |
| `docs/team/contracts/<계약ID>.md` | 해당 API 생산자 | B/A 및 C |
| `docs/team/tasks/A-*.md`, `B-*.md`, `C-*.md` | 해당 담당 | 지정 검토자 |
| `docs/team/handoffs/A-*.md`, `B-*.md`, `C-*.md` | 해당 담당 | 필요 시 참조 |
| `docs/development/PROGRESS.md` | 주간 통합 담당 | 개인 기록 링크만 모아 요약 |

공통 진행 파일을 셋이 매일 수정하지 않는다. 개인 작업서는 각자 관리하고 공동 진행 기록은 월요일 통합 담당이 한 번 갱신한다. 병합 담당은 A→B→C 순으로 매주 순환 가능하며, 파일 소유권이 자동으로 넘어가지는 않는다.

## 4. 타인 소유 파일이 필요할 때

1. Issue에 파일·필요한 인터페이스·호출 예·기한을 적는다.
2. 소유자가 2시간 이내 작업량이면 별도 작은 PR로 먼저 제공한다. 구현 시간이 아니라 본인의 다음 작업 시간 기준이다.
3. 더 크면 임시 작성자와 기간을 합의하고 원래 소유자는 해당 파일 편집을 멈춘다. Task에 `writer / reviewer / expires / blocked PR`을 기록한다.
4. 수신자 승인 전 타인의 브랜치에 push하거나 자기 PR에 몰래 수정하지 않는다. 대기는 자기 도메인 mock·테스트로 우회한다.
5. 같은 파일을 손댄 PR이 있으면 먼저 병합할 PR을 정하고, 두 번째 작성자가 갱신된 기준으로 충돌 해결·재검증한다.

CODEOWNERS는 GitHub ID 확인 후 A가 작성한다. 이름으로 계정을 추정하지 않으며, 설정하기 전까지는 이 표와 PR 체크리스트로 운영한다.
