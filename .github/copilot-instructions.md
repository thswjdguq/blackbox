# GitHub Copilot · Team Blackbox

모든 코딩 AI의 현행 공통 지침은 루트 `AGENTS.md`다. 먼저 아래 문서와 배정된 Task를 읽는다.

- `DEVELOPMENT.md`: 제품·데이터 원칙
- `docs/team/README.md`: 일정·담당·협업 문서 색인
- `docs/team/OWNERSHIP.md`: 실제 파일 소유권
- `docs/team/WORKFLOW.md`: 계약·검사·PR·병합 규칙
- 해당 `docs/team/tasks/<ID>.md`, `docs/team/contracts/<계약ID>.md`

## 반드시 유지할 것

- 작성자 A/B/C의 허용 영역만 수정하고 기존 사용자 변경은 보존한다.
- 기존 Flyway 파일 수정·삭제, file_vault 기존 버전 덮어쓰기·삭제, 비밀값 커밋을 금지한다.
- Controller → Service → Repository 구조, DTO 반환, ProjectAccessChecker 권한 검사를 유지한다.
- 실제 메서드는 `ActivityLogService.record`, `requireMember/requireContributor/requireLeader`다. 호출 시 선언과 인수를 확인한다.
- 프론트는 `import api from "@/lib/api"`를 사용한다. 응답 타입과 오류/로딩/빈 상태를 처리하고 `any`로 계약 불일치를 숨기지 않는다.
- 외부 연동 실패를 빈 성공 데이터로 숨기지 않는다. 명시적 오류 또는 문서화한 대체 결과를 반환한다.
- 스택 변경과 외부 인프라 추가는 별도 Task다. 현재 Docker 범위를 임의로 AWS 등으로 변경하지 않는다.
- Task 범위의 구현은 진행하되 타인 파일·계약 변경이 필요하면 조정한다. 무관한 전체 포맷팅·이름 변경을 섞지 않는다.
- 수행한 검사와 미수행 검사를 구별하고 diff·변경 이유·검증·남은 작업을 보고한다.
- 원격 push/merge/배포는 사용자가 요청한 범위에서만 한다.

## 현행 브랜치 안내

3인 고도화 계획: `codex/<Task-ID>-<주제>` → `main` PR. 기준선 공유·보호 설정 적용은 A-00/A-01에서 확인한다. 이전 `refactor/main` 전용 지침과 특정 모델의 계획/실행 역할 분담은 이번 고도화 작업에 적용하지 않는다. 과거 기술 문서는 현재 소스와 대조해 읽는다.

이 파일은 AGENTS.md의 진입 안내다. 모델별 중복 규칙을 늘리지 말고 공통 문서를 갱신한다.
