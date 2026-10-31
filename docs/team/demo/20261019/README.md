# 엑스포 공유 자료 · 2026-10-19

기준: Team Blackbox / shinguexpo / **cdae9636**. 실제 코드와 기존 검증 기록을 바탕으로 작성했다.

| 파일 | 쓰는 곳 |
| --- | --- |
| [시연 가이드.html](시연%20가이드.html) | 브라우저로 열어 화면별 스토리보드·벤치마킹·촬영 준비를 확인. 단독 공유 가능. 상단 인쇄 버튼으로 PDF 저장 가능 |
| [Flow 벤치마킹.md](Flow%20벤치마킹.md) | 기획 배경과 공식 출처, 실제 적용·미적용 범위 설명 |
| [시연 스토리보드.md](시연%20스토리보드.md) | 4인 역할, 약 6분 20초 촬영 대본, 실제 조작 순서와 예상 결과 |

**추천 전달:** 팀원에게 HTML 한 파일을 보내고, 발표 문장을 수정할 사람에게 Markdown 두 파일도 보낸다.

HTML의 화면 그림은 **현재 기능을 설명하는 개략도**이며 실제 화면 캡처가 아니다. 영상은 현재 실행 화면을 촬영한다. 10/14 team-workflow.html 시안과 C-50의 과거 제안은 이 자료의 현재 구현 설명과 구별한다.

## 현재 실행 기준

- 로컬 시연: http://127.0.0.3:13000/login
- 공개 전시 예정 주소: https://team-blackbox.duckdns.org/ — 최신 이미지 배포·HTTPS·실제 AI·휴대폰 접속 검증은 별도다. 주소 존재만으로 배포 완료라고 설명하지 않는다.
- 이 자료를 만들면서 프로젝트·계정·DB를 새로 만들거나 시연 서버를 재시작하지 않았다. 원래 데모 계정 비밀번호·실제 키는 포함하지 않았다.
- 촬영 준비는 안내서이며 아직 실행한 리허설 결과가 아니다.

## 근거

- Flow 공식 도움말 공개 페이지를 2026-10-19 재확인. 출처는 벤치마킹 문서에 기재했다.
- 로컬 현재 코드: taskView, KanbanBoard, TaskDiscussion, TaskWorkflowService, ReviewFeedbackNotifications, ReviewRoundCard, FinalSubmissionPanel, SidebarNavigation.
- 로컬 실제 AI 검증: 저장소 `docs/team/handoffs/EXPO-01-20261018.md`.
- 피드백 연결과 cdae9636 기준 관련 36/36 검사: 저장소 `docs/team/handoffs/EXPO-06-verified-20261019.md`.
