# 달력 하단 잘림 수정

- 요청: 프로젝트 생성 중 달력이 화면 아래에서 잘리고 스크롤되지 않는 현상 수정.
- 범위 초안: C 프론트의 DatePicker.tsx와 dashboard/page.tsx 두 파일. API·DB·의존성·배포 변경 없음.
- 기준: teamplay-ui HEAD 3060aee48fc348bc781e80bf4565144d49d7a907. 기존 frontend/next-env.d.ts 수정 및 docs/team/demo/20261019.zip은 사용자 변경으로 보존.
- 원인: 날짜 팝업이 absolute top-full로 아래에만 배치되고 프로젝트 모달에도 높이 제한과 스크롤이 없음.
- 변경: 팝업을 document.body의 fixed 레이어에 렌더링. 입력 위치와 실제 달력 높이에 따라 위/아래 선택, 화면 가장자리 보정, 부족한 공간은 내부 스크롤. 리사이즈·스크롤·월 변경 시 위치 갱신. 팝업 외부 클릭과 Escape 닫기. 프로젝트 모달은 동적 화면 높이 안에서 스크롤.
- 검사: npm run type-check 통과. git diff --check 통과. 배포용 빌드 결과는 아래 추가.
- 미검증: 실제 브라우저의 확대·모바일·6주 달력·키보드 표시 상태와 배포 서버 화면. 이전 브라우저 확인은 도구가 현재 URL을 검증하지 못해 중단됨.
- 배포용 빌드: npm run build 실행 후 Turbopack의 Creating an optimized production build 단계에서 장시간 추가 출력이 없어 중단 입력을 보냈으며 최종 종료 코드 1. 빌드 통과로 처리하지 않음. 별도 환경에서 재확인 필요.
- 의존 PR: 생성하지 않음. 원격 push·merge·배포 수행하지 않음.
- 다음: 빌드 결과 확인 후 C 화면 검토. 실제 서버에는 승인된 배포 절차로 반영하고 사용자 캡처와 같은 크기에서 시작일·종료일 선택 및 스크롤 확인.
