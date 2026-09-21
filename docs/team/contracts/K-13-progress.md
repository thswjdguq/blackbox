# K-13 · 제출물 진척 조회

- 버전: 0.1 / 상태: DRAFT (리뷰 전, API 미구현)
- 작성: 손정협(B) / 서버 검토: 송승준(A) / 화면 검토: 손정효(C)
- 작성일: 2026-09-21
- 근거: 로컬 `a40a931d8fe5ba3a73e878b0f60862dda6f9df36` + 기존 미커밋 1단계 코드. 공통 출발 SHA는 아직 확인되지 않았다.

## 1. 요청과 범위

`GET /api/projects/{projectId}/deliverables/{deliverableId}/progress`

요청 본문·query 없음. 두 ID는 UUID. 기존 인증을 사용하며 LEADER/MEMBER/OBSERVER 모두 조회 가능하다. 먼저 프로젝트 참여 권한을 확인하고 그 프로젝트의 제출물을 조회한다. 같은 계정이 두 프로젝트에 속해 있어도 다른 프로젝트의 제출물 ID 조합은 404다.

제출물 하나의 요약이므로 정렬·페이지 처리는 없다. 읽기 전용이며 요구사항 체크, 업무 상태, 활동 기록을 변경하지 않는다. 날짜/시간 필드는 이번 응답에 없다.

## 2. 응답 제안

200 JSON:

```json
{
  "deliverableId": "11111111-1111-4111-8111-111111111111",
  "tasks": { "total": 3, "completed": 1, "percent": 33.33 },
  "requiredRequirements": {
    "total": 2,
    "met": null,
    "percent": null,
    "assessmentAvailable": false
  }
}
```

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| deliverableId | UUID 문자열, 필수 | 권한 확인한 제출물 |
| tasks.total | 정수 ≥0 | 해당 프로젝트·제출물에 연결된 업무 수 |
| tasks.completed | 정수 0~total | 그중 상태가 DONE인 업무 수 |
| tasks.percent | 숫자 0~100 | completed / total ×100, 소수 둘째 자리 HALF_UP |
| requiredRequirements.total | 정수 ≥0 | 해당 제출물에서 required=true인 요구사항 수 |
| requiredRequirements.met | 정수 또는 null | 사람이 충족 확인한 필수 요구사항 수. 아직 확인 기능이 없어 알 수 없으면 null |
| requiredRequirements.percent | 숫자 또는 null | 충족률. met가 null이면 null |
| assessmentAvailable | boolean | 요구사항 충족 확인 데이터 계약이 지원되는지 여부 |

응답 필드는 생략하지 않는다. null과 0을 구분한다. 프론트가 null을 0으로 강제 변환하면 ‘미지원’이 ‘모두 미충족’으로 잘못 표시된다.

### 1차 구현: 현재 데이터만 사용

- 업무 수·완료 수·완료율과 필수 요구사항 전체 수는 실제 데이터로 계산한다.
- 필수 요구사항이 1개 이상이면 `assessmentAvailable=false`, `met=null`, `percent=null`. 화면은 ‘충족 확인 기능 준비 중’으로 표시한다.
- 필수 요구사항이 0개면 `assessmentAvailable=false`, `met=0`, `percent=0`. 이는 존재하지 않는 항목의 개수이며 승인 완료를 뜻하지 않는다. 화면은 ‘필수 요구사항 없음’이다.
- 연결 업무가 0개면 `completed=0`, `percent=0`이고 ‘연결 업무 없음’으로 표시한다.
- 미연결 업무, 다른 제출물 업무, 다른 프로젝트 업무는 분모·분자에서 제외한다. 여러 담당자가 붙은 업무도 1개로 센다.

항목이 없는 제출물 예시:

```json
{
  "deliverableId": "11111111-1111-4111-8111-111111111111",
  "tasks": { "total": 0, "completed": 0, "percent": 0 },
  "requiredRequirements": {
    "total": 0, "met": 0, "percent": 0, "assessmentAvailable": false
  }
}
```

### 2차 확장: A의 충족 확인 기능 이후

아직 구현하지 않는다. 아래는 미래 예시다.

```json
{
  "deliverableId": "11111111-1111-4111-8111-111111111111",
  "tasks": { "total": 2, "completed": 2, "percent": 100 },
  "requiredRequirements": {
    "total": 3, "met": 1, "percent": 33.33, "assessmentAvailable": true
  }
}
```

업무 100%와 요구사항 33.33%가 공존할 수 있다. 이 응답에는 `readyToSubmit`, `isConfirmed` 같은 승인 의미의 필드를 넣지 않는다. 최종 확정은 별도 서버 명령에서 조건을 다시 검증한다.

## 3. 오류 약속

| 코드 | 조건 | 처리 |
| --- | --- | --- |
| 400 | UUID 형식 오류 | 입력 오류. 자동 재시도하지 않음 |
| 401 | 미인증·만료 인증 | 기존 인증 흐름. 실제 필터 반환은 A-01 통합 확인 필요 |
| 403 | 존재하는 프로젝트의 비회원 | 조회 거절 |
| 404 | 프로젝트/해당 프로젝트의 제출물 없음 | 다른 프로젝트의 제목·존재 정보 추가 노출 금지 |
| 500 | 예기치 않은 저장소 오류 | 가짜 0% 대신 실패 표시·재시도 |

현재 전역 예외 처리 형태를 유지한다. 도메인 오류 예시:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "제출물을 찾을 수 없습니다"
}
```

401/400은 기존 Security·프레임워크 경로이므로 동일한 detail 문자열을 보장한다고 가정하지 않는다. 화면은 detail이 없을 때 기본 안내를 쓴다. 조회는 409를 정상 응답으로 사용하지 않는다.

## 4. 구현 경계와 트랜잭션

- B 신규 파일: `controller/DeliverableProgressController.java`, `service/DeliverableProgressService.java`, `dto/DeliverableProgressDtos.java`와 대응 테스트.
- B의 `TaskRepository.java`에 프로젝트·제출물로 제한한 전체/DONE 수 집계 메서드를 추가할 계획. 가능하면 단일 조건부 집계로 두 수를 같은 조회에서 얻는다. 담당자 테이블 join으로 수가 늘지 않게 한다.
- 프로젝트 조회·권한은 기존 `ProjectAccessChecker.getProject/requireMember`, 제출물 범위 조회는 기존 `DeliverableService.find(project,id)` 사용 가능.
- 필수 요구사항 목록은 현재 `DeliverableRequirementRepository.findByDeliverableOrderByCreatedAtAsc`를 읽어 필터할 수 있다. A 파일 변경 없이 기존 메서드를 호출한다. 범위를 벗어난 메서드 추가는 별도 요청한다.
- 읽기 전용 서비스 트랜잭션. 진척 조회는 참고용이며 확정의 잠금 근거가 아니다. 여러 조회를 단일 스냅샷으로 보장해야 한다면 별도 합의한다. 조회 중 다른 사용자의 변경으로 응답 시점 이후 수치는 바뀔 수 있다.
- 1차에는 DB 변경·migration·A DTO 수정·프론트 수정이 필요 없다.

## 5. 검토 요청 — 아직 보내거나 승인받지 않음

### 송승준에게

1. 기존 조회 메서드를 읽기 전용으로 재사용하는 방향 확인.
2. 이후 충족 확인에 `met` 의미·확인자·확인 시각·내용 변경 시 확인 초기화 정책을 별도 계약으로 제공 요청. B가 엔티티/SQL을 먼저 만들지 않음.
3. 초기 응답의 `assessmentAvailable=false`와 null 정책 확인.

### 손정효에게

1. 업무 진척과 요구사항 충족을 별도로 표시할 수 있는지 확인.
2. 위 세 예시(현행/빈 상태/미래)를 개발 fixture로 사용할 수 있는지 확인. 미래 예시를 실제 기능처럼 노출하지 않음.
3. 응답 조회 실패와 0건을 다른 UI로 표시하는지 확인.

승인자·날짜·계약 PR: 미정. A/B/C 확인 후 AGREED로 바꾸고 B-10을 시작한다. 문서 검토가 완료되었다고 추정하지 않는다.

## 6. 수용 테스트

| 입력/조건 | 기대 |
| --- | --- |
| 연결 3개 중 DONE 1개 | 3 / 1 / 33.33 |
| 연결 3개 중 DONE 2개 | 3 / 2 / 66.67 |
| 업무 0개 | 0 / 0 / 0, 100% 아님 |
| 다른 제출물·미연결 DONE 추가 | 현재 제출물 진척 변화 없음 |
| 한 업무에 담당자 2명 | 업무 수 1 |
| DONE을 IN_PROGRESS로 변경 | 완료 수 감소 |
| 필수 2개·선택 1개, 확인 기능 없음 | total=2, met/percent=null |
| 필수 0개·선택 2개 | total/met/percent=0, assessmentAvailable=false |
| 관찰자 | 조회 허용 |
| 비회원 / 다른 프로젝트 제출물 | 각각 403 / 404 |
| DB 예외 | 정상 0값 응답으로 숨기지 않음 |

## 7. 병합 순서

A-00 공통 기준선 → K-13 계약 리뷰·병합 → B-10 서버/테스트 → C 실제 API 연결. C의 fixture 기반 설계는 계약 DRAFT 표시와 함께 먼저 가능하다. 2차 확장은 A 확인 데이터 계약 및 기능 병합 후 별도 B Task로 수행한다.
