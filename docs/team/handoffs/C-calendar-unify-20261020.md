# 프로젝트·제출물 달력 통일

- 요청 및 범위: 두 생성 화면의 날짜 선택 UI 통일. C 프론트 DatePicker.tsx와 DeliverableFormModal.tsx만 수정하며 API·DB·의존성은 유지한다.
- 기준: shinguexpo / 506782be. 기존 next-env.d.ts 및 demo zip 변경은 보존한다.
- 변경: 제출물의 브라우저 기본 date 입력을 공통 DatePicker로 교체. 프로젝트와 같은 모양·월 이동·직접 입력·화면 경계 보정을 사용한다. 제출물 생성과 수정 모두 적용한다.
- 필수 날짜: DatePicker에 required를 추가하고 연·월·일 누락, 잘못된 날짜 및 0년을 브라우저 폼 검증으로 차단한다. 프로젝트의 선택 날짜는 비워 둘 수 있다. 기존 제출물 날짜 저장 형식 YYYY-MM-DD 유지. 실제 제출 시각의 datetime-local은 이번 범위 밖이다.
- 검사: npm run type-check 및 git diff --check 통과. npm run build -- --webpack 최초 실행은 .next 폴더 생성 EPERM으로 실패했으나 권한 조정 후 재실행은 종료 코드 0으로 운영 빌드 통과. 기본 Turbopack 빌드는 이번에 재검증하지 않음. 복수 lockfile·Browserslist 오래됨 경고는 남음.
- 미검증: 실제 브라우저 클릭·작은 화면·서버 화면. 이전 컴퓨터 사용은 URL 확인 제한으로 중단됐다.
- 의존 PR: 없음. 다음 작업은 실제 화면 확인 및 통합 브랜치 반영 후 배포 검증.
