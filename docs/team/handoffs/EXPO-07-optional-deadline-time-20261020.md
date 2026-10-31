# EXPO-07 선택 마감 시간 구현·로컬 적용

## 범위와 변경
- 기준 d034c415. 사용자 승인에 따라 제출물 서버·화면·AI 계약과 로컬 DB까지 구현했다. 원격 push·merge·원격 서버 배포는 하지 않았다.
- dueDate 유지, dueTime nullable HH:mm 추가. 한국 시간(Asia/Seoul). 기존 날짜에 23:59를 임의 부여하지 않는다.
- 기본은 날짜만. 마감 시간 추가/삭제, 수정 시 기존 시간 복원. 목록·상세·일정의 기한 상태·AI 제안 카드·AI 편집·조회 도구·PDF 보고서에 반영했다.
- 지정 시각 이상이면 기한 지남, 확정·제출 상태 우선. 시간 미지정은 기존 날짜 기준이다.
- 기존 next-env.d.ts와 demo zip은 수정 범위에서 제외하고 보존했다.

## 검증
- 프론트 npm run type-check 통과.
- verify-deliverable-time.cjs 통과: 마감 직전·정각·직후, 00:00, 확정·제출 상태 우선, 미지정 표시.
- 기존 verify-deadline-notice.cjs 및 verify-agent-ui.cjs 통과. git diff --check 통과(줄바꿈 정규화 경고는 남음).
- Java 17 builder의 bootJar 통과. Java 17 컨테이너 선택 검사 통과: DeliverableServiceTest, DeadlineTimeTest, DeliverableStageTest, BreakDownTasksSkillTest. 검증·저장·해제·AI 제안 시간 보존과 오류 시간 거절. 저장소는 mock이다.
- Docker 프론트 운영 빌드(Turbopack) 통과. 기존 Browserslist 경고 유지.
- 원본 로컬 DB(V29)를 deadline_time_verify_20261020으로 복사한 뒤 V30~V31.3 업그레이드와 JPA 검증·기동 확인. 기존 제출물 6건의 시간은 NULL로 보존.
- 복사 DB의 실제 인증 HTTP 검사 통과: 날짜만 생성, 시간 생성/조회/변경/해제/자정, 빈값·24:00·18:60·9:00·초 포함 거절, 관찰자 쓰기 403/읽기, 비회원 403, 미인증 401, 타 프로젝트 404. 검증 프로젝트 07483696-e2b8-4118-af00-d8ad2c6bfeba. 합성 데이터만 추가했고 실제 AI 모델 호출은 하지 않았다.

## 로컬 반영
- 원본 로컬 DB를 db 컨테이너의 /tmp/blackbox_before_deadline_time_20261020.dump로 백업하고 teamplayui backend/frontend를 갱신했다. db·uploads 볼륨 삭제 없음.
- 기존 환경에 남아 있던 V30·V31·V31.1·V31.2도 순차 적용됐다. 기존 migration은 변경하지 않았다. 최종 V31.3.
- 갱신 후 제출물 6건·사용자 29건 보존, 기존 제출물 due_time은 모두 NULL. 로그인 HTTP 200, /api/health UP, nginx 설정 검사·reload 통과.
- 로컬 주소 https://127.0.0.2:9443/login.

## 미검증·다음
- 실제 브라우저 클릭·모바일 화면은 미검증. 실제 AI의 안내문 시간 추출은 미검증이며 지시문/제안 검증 로직만 변경했다.
- 시간 필드 변경은 전체 PUT이므로 오래된 클라이언트에서 편집하면 시간 미지정으로 바뀐다. 서버와 프론트를 함께 배포해야 한다.
- 의존 PR 없음. 공유 브랜치 반영 전 A/B/C가 계약·V31.3 번호를 검토해야 한다. 공용 main DB는 이 별도 시연 DB와 번호가 다를 수 있다.
- 다음 작업: 사용자 로컬 화면 확인, 계약·번호 검토 후 별도 요청 범위에서 공유 반영.
