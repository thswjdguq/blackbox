# 업무 확정·동시성 검사 복원

기준: #143 `0786efd1`. 대상: `codex/expo-main-transition`.

## 변경과 근거
- main의 TaskFinalizationIntegrationTest와 TaskWriteConcurrencyTest를 복원했다.
- 업무 DONE 직접 설정은 현재 HTTP 완료 요청/다른 팀원의 승인으로 교체했다. 기존 확정·권한·피드백·동시성 기대값은 유지했다.
- 실제 DB 첫 정상 실행에서 8건 중 7건 통과, 연결 변경 경쟁 검사 1건 실패를 확인했다. 제출물 잠금 대기 중 SQL로 연결을 옮기는 통제된 경쟁에서 이전 연결을 기준으로 업무 수정이 성공했다.
- 현재 TaskService 경로는 프로젝트 잠금으로 직렬화되지만, 복원 검사는 다른 쓰기 경로가 동일한 프로젝트 잠금을 사용하지 않는 경우까지 방어하던 main의 계약이다. 모든 실제 사용자 요청에서 이 문제가 재현된다고 주장하지 않는다.
- TaskWorkflowService.requireEditable에서 제출물 잠금 후 업무를 다시 잠그고 읽는다. 연결이 달라졌으면 409, 삭제됐으면 404로 끝내며 DONE/요청 중 검사도 최신 상태를 본다. 새 연결에 대한 잠금을 뒤늦게 추가하지 않는다.
- API 구조·migration·라이브러리·프론트 변경 없음. A 복원 PR과 별도로 B 담당 서비스 1개와 테스트 2개만 변경한다.

## 검사
- Java 17, PostgreSQL 16 전용 DB bb_b_restore_clean_20261026 사용. 운영·시연 DB는 사용하지 않았다.
- 첫 시도는 기존 build/resources 안에 남은 과거 V31.x SQL 때문에 V32 중복 컬럼 오류로 기동 실패했다. clean 후 새 전용 DB에서는 V35까지 정상 적용됐다. 원본 migration이나 적용 이력은 고치지 않았다.
- 수정 전 실제 DB 검사 8건: 7 통과, 1 실패. 실패: taskWriteWaitsForDeliverableLockAndRejectsChangedAssociation.
- 수정 후 두 DB 검사 클래스 8/8, TaskWorkflowServiceTest 23/23, TaskDeliverableTest 9/9, TaskAssignmentRegressionTest 22/22: 총 62/62 통과, 실패·오류·건너뜀 0. Java 17 Gradle 실행은 3분 7초로 종료 코드 0이다. DB 통합 8건과 서비스 단위 54건을 구분한다.
- 실행: `sh gradlew test --tests com.blackbox.service.TaskFinalizationIntegrationTest --tests com.blackbox.service.TaskWriteConcurrencyTest --tests com.blackbox.service.TaskWorkflowServiceTest --tests com.blackbox.service.TaskDeliverableTest --tests com.blackbox.service.TaskAssignmentRegressionTest --no-daemon`.
- PR: https://github.com/thswjdguq/blackbox/pull/150 . 통합 브랜치 대상이며 작성자 외 A 검토 및 전체 통합 검증이 남아 있다.

## 남은 검증
- A/C 복원까지 합친 전체 테스트·브라우저·학교 AI·운영 이관/배포 검증은 이번 범위 밖이며 완료하지 않았다.
- 검증용 DB와 로그는 로컬에 남긴다. 합성 테스트 자료만 만들었으며 실데이터를 삭제하지 않았다.
