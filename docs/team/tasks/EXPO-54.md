# EXPO-54 · 엑스포 업무 동선 및 알림 연결 준비

- 사용자 승인: 2026-10-14 '진행하자'. 1인 개발자가 Codex/Claude를 운영하는 엑스포 임시 배정.
- 기준: teamplay-ui, db847808b22368e956c5c4b9089165d613f8c93f. 기존 미커밋 변경 보존.
- Codex: frontend 업무 상세 동선·입력 보호, K-54 계약 초안, 서버 인수인계.
- Claude: 별도 폴더에서 알림 서버·관련 파일/업무 이벤트 연결·테스트. A/B 구분의 별도 사람 승인을 기다리는 작업이 아님. 아직 실행되었다고 간주하지 않음.
- 허용: TaskModal/TaskCompletionPanel/TaskResources 및 관련 프론트 검사, EXPO-54/K-54 문서.
- 금지: 원격 작업, 커밋, 브랜치 변경, 의존성 변경, 기존 migration 변경, 실제 데이터 삭제.
- 계약: K-54 v0.1 DRAFT. 서버 확인 전 운영 호출 추가하지 않음.
- 완료 기준: 업무 내 결과·피드백·확인 영역으로 바로 이동, 보완 사유 닫기 보호, 실패 입력 유지, 알림 서버 작업을 독립적으로 시작할 수 있는 계약 제공.
- 검사: TypeScript, 관련 mock 회귀 검사, production webpack build, diff check. 실제 4인 DB/브라우저 검증 별도.
