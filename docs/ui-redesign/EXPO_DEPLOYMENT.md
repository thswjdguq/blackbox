# 엑스포 배포 인수인계

2026-10-13 · 손정협 · 사용자 요청: 최신 개선 UI와 OpenAI 도우미를 `shinguexpo`에 push해 송승준이 서버에 배포한다.

## 배포 대상

- 저장소: https://github.com/thswjdguq/blackbox
- 브랜치: `shinguexpo`. 시작 커밋: `91d6bbba`.
- 소스 기준: `C:/blackbox/teamplay-ui`. `teamplay-integration`의 main 통합 변경은 포함하지 않는다.
- 변경: AI 상태·질문·계획·업무 제안 UI와 서버, 사람의 제안 수정·채택, 출처 표시, SSE 프록시 설정.
- 서버/API → 화면 → 프록시 설정 → 인수인계 순으로 커밋을 분리한다.
- 이번 요청은 엑스포 브랜치 push까지다. main 병합과 실제 서버 배포는 수행하지 않는다. 공통 계약과 main 반영은 A/C 검토가 별도로 필요하다.

## DB 기준 — main DB와 혼용 금지

엑스포 브랜치는 아래 이력을 사용한다. 기존 SQL은 수정하지 않는다.

| 번호 | 엑스포 마이그레이션 |
| --- | --- |
| V25 | meeting_attendance_window |
| V26 | task_file_links |
| V27 | task_completion_approval |
| V28 | task_messages |
| V29 | agent_proposals |

main의 V25는 agent_proposals이므로 같은 DB에 엑스포 코드를 실행하면 이력이 충돌한다. 새 서버는 엑스포 전용 새 DB·업로드 볼륨을 사용하거나, 동일한 엑스포 이력을 가진 DB의 백업본으로 시작한다. 기존 main/시연 DB 삭제, `flyway repair`, 기존 migration 번호 변경으로 우회하지 않는다. 서버 담당자는 시작 전 `flyway_schema_history`의 version/script/success를 대조한다.

## 송승준 배포 설정

1. `git fetch origin` 후 `shinguexpo`의 전달받은 커밋으로 배포한다. 다른 작업이 있는 폴더는 덮어쓰지 않는다.
2. 서버에서 `.env.example`을 참고해 DB 접속 정보와 충분히 긴 `JWT_SECRET`, `OPENAI_API_KEY`를 설정한다. 실제 `.env`, 키, 인증서는 Git에 넣지 않는다.
3. 이 브랜치는 기존 OpenAiService와 `gpt-4o-mini`를 사용한다. main 통합용 `AGENT_PROVIDER`/Spring AI 설정은 필요하지 않다. 키가 없으면 AI는 사용 불가 상태를 표시한다.
4. 프론트 Docker 빌드 인자는 `NEXT_PUBLIC_API_URL=/api`를 사용한다. 공개 도메인과 API가 같은 origin인 구성을 전제로 한다. 빌드 후 런타임 변수만 바꾸면 클라이언트 번들 주소가 바뀌지 않는다.
5. 기본 compose는 로컬 개발용이다. 서버에서는 `SPRING_PROFILES_ACTIVE=prod`, `APP_CORS_ORIGINS=https://실제도메인`, `FRONTEND_BASE_URL=https://실제도메인`을 override한다. `local` 프로파일은 CORS를 `*`로 덮어쓴다.
6. 실제 도메인의 TLS 인증서와 nginx server_name을 적용한다. `.local-run`의 127.0.0.2:9443 주소·인증서는 서버에 복사하지 않는다.
7. nginx의 AI `/agent/runs` 경로는 buffering off, read/send timeout 150초를 유지한다. 앞단 로드밸런서가 있으면 동일하게 스트리밍과 대기 시간을 허용한다.
8. 기존 nginx에는 `/uploads/` 정적 공개 경로가 남아 있다. 인터넷 공개 전에 A가 파일 접근 정책과 인증 다운로드 경로를 함께 점검한다. 이 브랜치에는 main의 후속 배포 보안 PR 전체를 병합하지 않았다.

컨테이너/DB/볼륨은 기존 서비스와 분리한다. 기존 컨테이너 이름과 80/443 포트를 사용하는 서비스가 있다면 서버용 compose에서 충돌을 조정한다. 이 문서는 도메인 서버에서의 실행 성공을 의미하지 않는다.

## 배포 후 확인

- 로그인 → 프로젝트 → 업무 결과 등록 → 다른 팀원 확인/보완 요청.
- AI 질문의 스트리밍 응답, 계획 제안 수정·채택, 생성 업무/제출물 연결.
- 관찰자 쓰기 거절, 비회원 프로젝트 접근 거절.
- 파일 업로드/다운로드와 서버 재시작 후 데이터 유지.
- 도메인에서 두 계정으로 실제 클릭 시연. 브라우저·모바일 최종 검증은 별도로 필요하다.

## 이번 push 전 검증

- 프론트 운영 빌드 및 TypeScript 검사 통과.
- `verify-agent-ui.cjs`, `verify-completion-view.cjs` 통과. fixture 기반 검사이며 실제 브라우저 검사를 대신하지 않는다.
- 배포 nginx 설정을 임시 컨테이너에서 `nginx -t`로 검사하여 통과.
- 변경 파일의 비밀키 패턴 검사에서 의심 항목 없음. `.env`는 커밋하지 않음.
- Java 17·별도 PostgreSQL 16에서 `./gradlew build --no-daemon --console=plain` 최종 통과(5분 17초). JUnit XML 합계 234개, 실패 0, 오류 0, 건너뜀 0. 기존 서비스 DB는 사용하지 않았다.
- 첫 실행에서 예전 완료 처리 방식을 쓰던 테스트 3건이 실패했다. 피드백 저장소 fixture에 승인 정보를 넣고, 비동기 점수 테스트를 완료 요청 → 독립된 팀원 확인으로 변경했다. 운영 제약을 완화하지 않고 전체 재검사했다.
- 검증 DB의 Flyway 이력에서 V25~V29 모두 success=true를 확인했다. 이 결과는 새 검증 DB에 대한 것이며 main DB 업그레이드 호환성을 의미하지 않는다.
- 로그: `outputs/merge-review-20261013/expo-backend-recheck.log`(로컬 산출물, Git 미포함). 과거 실제 OpenAI/API 검증은 `AI_ASSISTANT_UI.md`에 별도로 기록되어 있다. 이번 push 전 재검사에서 실제 모델을 다시 호출하지 않았다.
