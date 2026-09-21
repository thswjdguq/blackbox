# 2026-09-21 · main 충돌 해결 및 제출물 구현 통합

## 범위

사용자 요청에 따라 현재 작업 브랜치에 최신 main(69274a5)을 통합하고 미커밋 1단계 구현과 B-12 테스트를 포함한다. main으로 병합하는 작업은 아니다.

- GlobalExceptionHandler: main의 Logger, AccessDeniedException 및 예상하지 못한 오류 처리를 유지한다.
- 제출물의 ResponseStatusException·DB 무결성 충돌 처리 두 메서드를 함께 보존한다.
- 제출물/요구사항 API·Entity·Repository·V19와 업무 연결 및 프론트 화면을 포함해 테스트의 누락 의존성을 해결한다.
- 기존 main의 AI 리팩토링과 SmartDateInput 변경을 보존한다.
- 생성 파일·로컬 환경 설정·발표 산출물·무관한 문서 변경을 제외한다.

## 검증

최신 main을 병합한 별도 작업 공간에서 검사한다. 원래 작업 공간은 그대로 두고 해결된 GlobalExceptionHandler만 동기화했다.

- Java 컴파일 및 관련 단위 테스트 40개 통과: 기존 회귀 35개 + 예외 처리 보존 테스트 5개. 실패·오류 0.
- 테스트 명령: Gradle wrapper test, TaskAssignmentRegressionTest / TaskDeliverableTest / DeliverableServiceTest / GlobalExceptionHandlerTest 선택 실행.
- 프론트 타입 검사 통과.
- 프론트 `npm run build -- --webpack` 통과(종료 코드 0). 제출물 경로 생성 확인. 복수 lockfile·Browserslist 경고는 남아 있다.
- DB 실제 마이그레이션·브라우저 저장/재조회·전체 Spring 컨텍스트 검사는 이번 실행에 포함하지 않았다.

로컬 검증 JDK는 23이며 Java 17 대상 설정을 유지했다. 프론트는 기존 의존성을 junction으로 재사용하고 별도 작업 공간에서 webpack 빌드를 사용했다. junction은 커밋하지 않는다.

## 주의

원래 C:/blackbox는 미커밋 변경 보존을 위해 브랜치 포인터를 자동으로 이동하지 않는다. 원격 push 후에는 원격 브랜치와 원래 로컬 브랜치의 HEAD가 다를 수 있다. 기존 변경을 검토 없이 reset하거나 강제 push하지 않는다.
