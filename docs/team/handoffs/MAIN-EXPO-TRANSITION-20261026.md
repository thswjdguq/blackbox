# main 전환 · 엑스포 기능 + 기존 main migration 이력

## 현재 결정
- 기준: main 960934ad, shinguexpo e1ed24d7. 기존 main 원격 백업: backup/main-before-expo-20261026.
- 초기 d25282f1은 전체 expo 스냅샷이었다. 사용자 요청에 따라 앱 기능은 expo, DB migration은 main 호환 방식으로 수정한다. 현재 PR을 expo와 전체 파일이 동일하다고 설명하면 안 된다.
- main의 V1~V25 migration 24개를 파일명·SQL 내용 그대로 유지한다. V23은 추가하지 않는다.
- 기존 expo 브랜치나 적용된 expo DB의 migration을 수정하지 않는다. 전환 브랜치에서 main에 새로 추가할 SQL만 번호를 배치한다.

## 번호 매핑 (A 검토 요청)
| expo 원본 | main 통합본 | 내용 |
| --- | --- | --- |
| V29 agent_proposals | 기존 main V25 | SQL 동일 확인, 중복 생성 제외 |
| V25 meeting_attendance_window | V26 | 회의 출석 |
| V26 task_file_links | V27 | 업무 파일·프로젝트 복합 키 |
| V27 task_completion_approval | V28 | 업무 완료 확인, V27 뒤 적용 |
| V28 task_messages | V29 | 업무 대화, V27 뒤 적용 |
| V30 user_notifications | V30 | 개인 알림 |
| V31 task_assigned_notification | V31 | 배정 알림 |
| V31_1 feedback_notifications | V31.1 | 피드백 알림 |
| V31_2 user_university_affiliation | V31.2 | 학교·학과 |
| V31_3 deliverable_optional_due_time | V31.3 | 마감 시간 |
| 신규 | V31.4 | 기존 main DONE 업무 수정 호환 |
| #133 | V32 예약 유지 | 확정 취소는 본 PR에 포함하지 않음 |

2026-10-26 최신 main 및 열린 PR의 migration 경로 대조: #133 V32 예약 유지. 이 번호안은 A 검토와 병합 전 재확인이 필요하다. V26 이상인 다른 PR이 먼저 main에 병합되면 재배치·재검증한다.

## 기존 완료 업무 호환
- 기존 main의 승인 없는 DONE 업무는 그대로 보존한다. expo의 NOT VALID CHECK는 해당 행의 제목 수정까지 막을 수 있다.
- 신규 V31.4에서 전환 검사 trigger로 바꾼다. 기존 DONE이며 승인 기록이 없던 행의 일반 수정은 허용한다. 승인자를 임의로 채우지 않는다.
- 새 DONE 삽입, 진행 상태에서 승인 없이 DONE 전환, 승인된 DONE의 승인 기록 제거는 23514로 거절한다.
- 기존 업무를 재개했다면 다시 DONE이 될 때 승인이 필요하다. 서비스의 권한·독립 확인자 검사는 바꾸지 않는다.

## 검증
- verify-main-migration-history.cjs: 기존 main SQL 24개 보존, 번호 중복 없음, 중복 AI 생성 SQL 제외 확인. CI에도 연결한다.
- MainMigrationCompatibilityTest: 별도 PostgreSQL schema에서 빈 DB 적용·재실행, main V25 기존 사용자·프로젝트·업무·파일·AI 기록 보존과 legacy DONE 수정을 검증한다.
- verify-db.sh: 소수 버전 정렬 지원, 업그레이드 기준 main V25. 허용된 검사 DB만 재생성한다.
- 실제 실행 결과는 후속 결과 기록에 추가한다. 이전 expo 검사 기록을 이번 검사 결과로 대체하지 않는다.

## 배포·데이터 조건
1. 기존 main V25 DB는 새 migration을 순차 적용하는 대상이다.
2. 기존 expo DB는 이력이 달라 그대로 연결할 수 없다. 원본 DB·업로드 파일을 백업·보존하고 별도 main 호환 DB로 데이터를 이관하는 경로가 필요하다. 운영 데이터 이관은 이번 작업에서 실행하지 않는다.
3. Flyway repair/clean, 체크섬 강제 수정, 이력 행 교체로 맞추지 않는다. expo 데이터 이관과 파일 다운로드 검증 전 서버를 전환하지 않는다.
4. #141은 이번 기준으로 다시 맞춘다. 현재 전환 스냅샷에는 main의 images.yml·ec2 스크립트·systemd 파일이 없고, expo docker-compose.prod.yml은 옛 Docker Hub latest 설정이다. 필요한 배포 파일 복구와 실제 이미지·환경변수·볼륨 검증이 남는다.
5. AI 공급자 경로는 expo 기준이다. 실제 AI·SSE·제안 채택 검증은 별도다.

## 상태·다음 작업
- #143 수정만 수행하며 원격 main/expo 직접 변경·서버 배포는 하지 않는다. UX 1~3번과 홈 경보 로컬 수정은 별도 폴더에 보존했다.
- A migration 번호·legacy DONE 정책 검토, CI, #141 배포 정합성과 expo 데이터 이관 검증 전까지 Draft다.
- C가 올린 #140/#142/#144 및 다운로드 갱신·작업서 번호 복원은 별도 PR로 처리한다.
