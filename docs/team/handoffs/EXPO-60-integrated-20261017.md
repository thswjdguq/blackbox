# EXPO-60 수정 통합 및 전시 이미지 · 2026-10-17

기준 shinguexpo / db847808 + 기존 미커밋 변경. EXPO-60 Claude handoff/패치/배포 초안을 검토했다. 커밋·push·merge·AWS 적용 없음.

## 반영 내용

- ReportService: 현재 팀장·팀원만 참여표와 참여 알림에 포함. 과거 FREE_RIDE의 실명 문구도 중립 안내로 출력. 무임승차/미참여 표현 제거. 해결된 알림 제외. TAMPER 유지. DB 원본은 보존.
- ScoreService: 조회·재계산 응답에서 관찰자·탈퇴자 제외. 산식·저장·migration 변경 없음.
- AlertService의 공통 안내 상수를 PDF에서도 공유.
- 초안 추가 보완: PDF 표지 팀원 수에서도 관찰자 제외. 생성·계산 시간을 한국 시간으로 표시하고 생성 시각에 KST 명시. '경보 없음=기여 균형' 단정 문구 제거.
- 회귀 검사 3건 추가: 실제 PDF 바이트 생성/텍스트 추출, 과거 알림 보존·무결성 알림 유지, 점수 응답 대상 확인.

## 검증 결과

- 전용 PostgreSQL bb_expo_20261016_test에서 전체 backend **275/275**, 실패·오류 0. Java 로컬 JAR 빌드 통과.
- 수정 JAR로 18083 백엔드만 재시작. 13000 프론트와 시연 DB/업로드 보존. 프론트 소스 변경 없음.
- 실제 frontend proxy HTTP: 월요일 demo 및 C-60 프로젝트 모두 scores=4명, PDF 200, 증거 ZIP 200.
- 두 프로젝트의 PDF 및 ZIP 내부 PDF 모두 무임승차/미참여/관찰자/과거 실명 문구 없음, 팀원 4명 및 KST 확인. PDF 네 페이지를 PNG로 렌더링해 참여표·중립 문구가 잘리지 않음을 확인.
- 증거: outputs/c60-integrated-20261017/{demo,c60}.{pdf,zip}, PNG, verify-exports.cjs, check-pdf.py. 기존 합성 자료를 읽었고 수정하지 않았다.
- 기존 PDF 표시 한계: 하단 총 페이지는 원래 `?` 표기, 일부 표가 다음 페이지로 이어질 때 제목행 반복 없음. 업로드 이력 시간은 기존 포맷 유지. 이번 R3은 생성/계산 시각 범위이며 전체 보고서 날짜 체계 개편은 하지 않았다.
- git diff --check 통과. 이번 변경은 backend만이므로 앞선 프론트 빌드/브라우저 전체 리허설을 재수행한 것으로 보고하지 않는다.

## 새 이미지

**IMAGE_TAG=expo-20261017-db847808-wb99be1ff**

EXPO-60 배포 초안의 build-images.ps1로 현재 통합 소스에서 backend/frontend/nginx 세 이미지를 빌드했다. 기존 `expo-20261017-db847808-w371a6d7d`에는 R1~R3이 없으므로 새 시연 후보로 쓰지 않는다.

이미지 ID는 `outputs/c60-integrated-20261017/images/manifest-expo-20261017-db847808-wb99be1ff.json`. 이후 추가된 handoff 문서는 이미지에 없으며, 태그는 빌드 시점 소스와 대응한다. 동일 태그를 덮어쓰지 않고 이미지 ID로 확인한다. tar/save·push·서버 전송은 하지 않았다.

기존 격리 c60smoke 볼륨 및 18443으로 새 태그 실행 후:
- compose config 통과, health/login 200.
- 외부 Origin 403, 동일 Origin 로그인 200.
- 쿠키 Secure/HttpOnly/SameSite 확인.
- /uploads 직접 접근404, 인증 없는 다운로드401, 인증된 한글 파일 다운로드200·파일명 보존.
- AI available:false. 실제 모델 호출 없음.
- 검사 후 c60smoke 컨테이너 중지(컨테이너·볼륨 삭제 없음). 기존 시연 18083/13000 유지.

이번 스모크는 HTTP 검사이며 Docker 환경의 전체 브라우저 재리허설·재부팅 복구를 새로 완료한 것은 아니다. EXPO-60의 이전 태그 재시작 보존 검증과 구분한다.

## 남은 결정과 다음 작업

공개 HTTPS 주소 및 AI 필수 여부를 사용자에게 질문했다. 비밀값을 대화로 받지 않는다. 실제 공개 인증서/휴대폰 모바일 데이터/AI 키·게이트웨이 검사 전에는 공개 전시 준비 완료로 선언하지 않는다.

배포 초안은 outputs/c60-check-20261017/deploy-draft에 보존. 실제 환경값과 배포 대상이 정해지면 새 태그/ID를 기준으로 전송·적용안을 확정한다. 기존 운영 DB 연결, 기존 compose.prod의 latest 사용, 새 기능 개발은 진행하지 않는다.

Claude 다음 지시서: docs/team/tasks/EXPO-61-claude-release-check.md. 반복 구현 대신 새 이미지의 최종 브라우저 확인과 실제 전시 환경의 남은 점검을 담당한다.
