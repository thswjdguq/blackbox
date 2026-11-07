# Claude Code 전달용 · K-54 알림 서버

아래 내용을 Claude Code에 전달한다. Codex가 Claude를 실행하거나 서버를 변경한 상태는 아니다.

```text
사용자 한 명이 엑스포 개발을 위해 Codex와 Claude Code를 병렬 운영한다.
Codex는 C:\blackbox\teamplay-ui의 frontend를 작업 중이다. 이 폴더를 수정하지 마라.
너는 별도 작업 폴더에서 K-54 개인 알림 서버와 테스트를 담당한다.
작업 폴더의 git 상태·기준 커밋을 먼저 확인하고, 기존 변경을 보존하라.
teamplay-ui 기준 SHA는 db847808b22368e956c5c4b9089165d613f8c93f이다.
다른 기준이면 서버 API 차이를 확인하고 작업서에 기록하라.

AGENTS.md, DEVELOPMENT.md, docs/team/{README,OWNERSHIP,WORKFLOW,CONTRACTS}.md를 읽어라.
다음 최신 로컬 문서는 teamplay-ui에서 읽기만 하라:
- docs/team/tasks/EXPO-54.md
- docs/team/contracts/K-54-personal-notifications.md
- docs/team/handoffs/EXPO-53-20261014.md

K-54는 아직 DRAFT다. 실제 코드와 대조해 미확정 항목을 해결하고
네 작업 폴더에 검토된 계약 및 변경 사유를 기록하라. 원본 계약을 동시에 편집하지 마라.
사용자의 엑스포 임시 배정으로 알림에 필요한 업무·파일 서버 연결도 네 범위다.
기존 A/B의 별도 개발자 승인을 기다리지 않아도 된다.
단, frontend·인증 구조·라이브러리 버전·배포 설정·무관한 서버 기능은 변경하지 마라.
기존 파일 버전과 데이터, 권한·프로젝트 경계를 유지하라.
새 migration은 실제 최신 번호와 다른 로컬 작업을 먼저 조사하고 번호를 기록하라.

업무 파일 업로드, 완료 확인 요청, 보완 요청, 의견 요청·답글 알림을 구현하라.
기존 위험 알림이나 피드백함과 중복되는 새 업무 처리 기능은 만들지 마라.
사용자별 읽음, 본인 제외, 서버 권한 판정, 안정적 이벤트 중복 방지,
트랜잭션 롤백, 재요청 시 새 알림, cursor 페이지를 검증하라.
학교 자동 제출·이메일·녹음·채팅·자동 감점은 제외한다.

단위 테스트와 실제 DB migration/통합 검증은 따로 보고하라.
실제 DB를 지우거나 볼륨을 초기화하지 마라. 비밀값을 출력하지 마라.
새 브랜치 생성·커밋·push·merge·배포 없이 로컬에서 진행하라.
완료 시 변경 파일 목록, 확정 계약, 테스트 명령/결과, migration 번호,
미검증 사항을 별도 handoff로 남겨 Codex가 프론트를 연결할 수 있게 하라.
```
