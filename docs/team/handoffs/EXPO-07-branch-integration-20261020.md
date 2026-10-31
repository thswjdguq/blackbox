# EXPO-07 브랜치 업로드 준비 및 통합 검사

- 사용자 요청: 마감 시간·피드백 배치·내보내기 색상 변경을 shinguexpo에 올리기.
- 기능 커밋: 72bfb914. 원격 c63a7377에 추가된 AI 출력 8000토큰/대기 상한 수정(88e6f6ce)을 덮어쓰지 않고 045f9f1f로 병합했다. 충돌 없음.
- 통합 후 검사: 프론트 type-check, verify-deliverable-time.cjs, verify-feedback-loop.cjs, verify-agent-ui.cjs 모두 통과. Docker 프론트 운영 빌드 통과.
- 통합 후 Java 17 컨테이너 검사: DeliverableServiceTest, DeadlineTimeTest, OpenAiAgentModelTest 모두 통과(BUILD SUCCESSFUL). 저장소·모델은 mock이며 실제 AI 호출 검증은 아님.
- 재현용 실제 HTTP 검사 스크립트도 frontend/scripts/verify-deliverable-time-http.cjs로 포함했다. 이전 실행 결과는 EXPO-07 구현 인계 참조. 실제 HTTP 검사를 통합 후 반복하지는 않았다.
- 최초 staged diff 검사에서 신규 계약·작업서 마지막 빈 줄이 발견돼 제거했다. 사용자 next-env.d.ts 변경과 demo ZIP은 커밋에서 제외했다.
- 마이그레이션 V31.3은 이 엑스포 분기의 기존 V31.2 다음 번호. 공용 main DB는 별도 번호 검토 필요. 서버 배포 시 backend/frontend 동시 갱신 필요.
- 실제 브라우저·모바일·공개 서버 배포 미수행. 이번 요청은 브랜치 업로드이며 main 병합·원격 서버 배포를 수행하지 않는다.
