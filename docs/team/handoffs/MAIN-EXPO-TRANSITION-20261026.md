# main 개발 기준을 엑스포 버전으로 전환

## 사용자 결정과 기준

- 사용자는 기능별 이관 대신 현재 검증된 shinguexpo를 main의 코드 기준으로 채택하도록 요청했다.
- 기존 main: 960934adda04de32da4a7570e80a606b146312d8.
- 채택한 shinguexpo: e1ed24d7e68549f85c5f542eeeb62c05e52d51df.
- 기존 main 백업: backup/main-before-expo-20261026. main과 expo 양쪽 이력을 병합 부모로 보존한다. force push를 사용하지 않는다.
- 전환 커밋 d25282f1의 전체 Git tree가 위 expo와 동일함을 git diff로 확인했다. 이 안내 문서만 후속으로 추가했다.
- 현재 개발 중인 업무 흐름 1~3번과 홈 경보 로컬 수정은 이 전환에 포함하지 않았다. 별도 작업 브랜치에 보존했다.

## PR 병합 전에 반드시 정할 배포 조건

1. 이 PR은 코드 기준 전환이다. 기존 main DB에 그대로 적용할 수 있는 업그레이드가 아니다. main V25는 agent_proposals, expo V25는 meeting_attendance_window이며 expo의 agent_proposals는 V29다. 같은 버전 번호의 SQL이 달라서 기존 main DB에 연결하면 Flyway 검증 실패가 예상된다.
2. 현재 엑스포 DB와 uploads를 보존한다. 실제 서버의 IMAGE_TAG, COMPOSE_PROJECT_NAME, DB 볼륨, 업로드 볼륨/경로, Flyway 이력, AI 환경변수를 읽기 확인하고 백업·복구 검증 후 전환한다. 새 DB를 만들거나 compose down -v, Flyway repair/clean으로 맞추지 않는다.
3. PR #141은 새 main DB로 옮기는 절차를 제안하지만 이 사용자 결정은 엑스포 DB 유지가 우선이다. #141의 전환 절차와 적용 순서를 수정해야 한다. 이 PR만 보고 서버에서 새 DB 전환을 실행하지 않는다.
4. expo 스냅샷에는 기존 main의 .github/workflows/images.yml, scripts/ec2-*.sh, scripts/systemd/** 등이 없다. 그대로 병합하면 해당 자동배포 파일도 제거된다. #141의 후속 변경은 이 전환본을 기준으로 다시 맞추고 필요한 배포 파일 전체를 복구·검증해야 한다. #141을 먼저 병합하면 이 PR도 다시 검토해야 한다.
5. expo의 docker-compose.prod.yml은 옛 Docker Hub latest 이미지 설정이다. 이 파일로 현재 서버를 재시작하지 않는다. 기존 실제 배포 override와 고정 이미지 태그를 확인하고 새 코드에서 빌드한 이미지를 사용해야 한다.
6. AI는 expo 공급자 경로를 채택한다. main에만 있는 Spring AI 개선을 이 PR에서 섞지 않았다. 실제 공급자 설정과 SSE 응답·제안 채택까지 별도 검증한다.

## 검증과 상태

- 원격 최신 main/expo SHA를 고정하고 전환 커밋의 전체 파일 상태 일치를 확인했다.
- 두 부모를 보존하여 main의 이전 커밋이 전환 이후에도 이력에 남는다. 백업 원격 브랜치도 생성했다.
- 기존 expo 검증 기록은 해당 handoff를 참조한다. 이번 전환에서 전체 빌드·실제 DB·브라우저·공개 서버 검증을 새로 수행한 것은 아니다.
- 배포·DB 조건이 해결되지 않아 Draft로 제출한다. CI 통과만으로 기존 main DB 호환성이나 서버 반영을 보장하지 않는다.
- PR 승인·main 병합·실제 서버 변경은 별도다. 원격 main과 expo 브랜치는 변경하지 않았다.

## 후속 작업

- A와 함께 #141을 이 전환 기준으로 수정: 엑스포 DB/파일 유지, 필요한 자동배포 파일 복구, 배포 compose와 환경변수 정합성 확인.
- 별도 검증 환경에서 현재 엑스포 DB의 복제본으로 migration validate 및 재시작, 파일 다운로드, AI, 4인 업무·피드백·확정·제출 흐름 검증.
- 본 PR 병합 후 기존 main 대상 열린 PR은 차이를 재검토해 중복 기능을 재적용하지 않는다. 원본 PR 기여 이력은 유지한다.
