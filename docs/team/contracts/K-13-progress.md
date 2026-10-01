# K-13 · 제출물 진척 조회

- 버전: 0.2 / 상태: DRAFT (A 검토 완료, C 화면 검토 대기)
- 작성: 손정협(B) / 서버 검토: 송승준(A) / 화면 검토: 손정효(C)
- 작성일: 2026-09-21 / 갱신일: 2026-09-28
- 근거: 공통 기준 `fbbb3cb`, A 검토 문서 `A-K13-review-20260921.md`, 요구사항 충족 확인 PR [#48](https://github.com/thswjdguq/blackbox/pull/48)(병합됨)

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
    "met": 1,
    "percent": 50.00,
    "assessmentAvailable": true
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
| requiredRequirements.met | 정수 | 사람이 충족 확인한 필수 요구사항 수 |
| requiredRequirements.percent | 숫자 | met / total ×100, 소수 둘째 자리 HALF_UP. total=0이면 0 |
| assessmentAvailable | boolean | 요구사항 충족 확인 데이터 계약이 지원되는지 여부 |

응답 필드는 생략하지 않는다. 화면은 `percent`를 다시 계산하지 않고 서버 값을 그대로 사용한다.

### 현재 구현: K-14 충족 확인 데이터 사용

- 업무 수·완료 수·완료율과 필수 요구사항 전체·충족 수를 실제 데이터로 계산한다.
- K-14가 제공하는 사람이 확인한 충족 상태를 사용하므로 `assessmentAvailable=true`다.
- 필수 요구사항이 0개면 `met=0`, `percent=0`. 이는 승인 완료를 뜻하지 않는다.
- 연결 업무가 0개면 `completed=0`, `percent=0`이고 ‘연결 업무 없음’으로 표시한다.
- 미연결 업무, 다른 제출물 업무, 다른 프로젝트 업무는 분모·분자에서 제외한다. 여러 담당자가 붙은 업무도 1개로 센다.

항목이 없는 제출물 예시:

```json
{
  "deliverableId": "11111111-1111-4111-8111-111111111111",
  "tasks": { "total": 0, "completed": 0, "percent": 0 },
  "requiredRequirements": {
    "total": 0, "met": 0, "percent": 0, "assessmentAvailable": true
  }
}
```

업무 완료율과 요구사항 충족률은 서로 다른 값이다. 예를 들면 다음과 같다.

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
- 필수 요구사항 전체·충족 수는 A가 `DeliverableService.countRequiredRequirements`와 `countMetRequiredRequirements`로 제공한다. B는 A 저장소를 직접 호출하지 않는다.
- 읽기 전용 서비스 트랜잭션. 진척 조회는 참고용이며 확정의 잠금 근거가 아니다. 여러 조회를 단일 스냅샷으로 보장해야 한다면 별도 합의한다. 조회 중 다른 사용자의 변경으로 응답 시점 이후 수치는 바뀔 수 있다.
- 1차에는 DB 변경·migration·A DTO 수정·프론트 수정이 필요 없다.

## 5. 검토 상태

### 송승준(A) 검토 결과

1. 기존 조회 메서드를 읽기 전용으로 재사용하는 방향을 확인했다.
2. K-14와 A-04에서 충족 확인 데이터와 집계 메서드를 제공했다.
3. 화면은 서버의 `percent` 값을 그대로 사용하기로 했다.

### 손정효(C) 검토 결과 (2026-10-01)

1. 업무 진척과 요구사항 충족을 별도로 표시할 수 있는가 → **가능.** 제출물 상세의 진척 현황에서 두 값을 다른 줄로 표시하고, `percent`는 다시 계산하지 않고 서버 값을 쓴다.
2. 응답 예시를 개발 fixture로 쓸 수 있는가 → 쓸 수 있지만 **쓰지 않는다.** B-10이 병합되어 화면은 실제 API만 호출한다. fixture와 mock 전환 플래그는 #53에서 지웠다.
3. 조회 실패와 0건을 다른 UI로 표시하는가 → **예.** 실패는 가짜 0% 대신 오류 안내와 다시 시도, 0건은 "연결 업무 없음"·"필수 요구사항 없음".

화면 타입은 `met`·`percent`를 null 없는 숫자로 둔다. 로컬 PostgreSQL에서 충족 체크·해제 후 `met`이 1/1 → 0/1로 바뀌는 것을 확인했다(`handoffs/C-10-20261001.md`). C는 이 계약을 AGREED로 바꾸는 데 동의한다.

A 검토: 2026-09-21 `A-K13-review-20260921.md`. C 검토: 위. 상태 변경은 생산자(B)가 한다.

## 6. 수용 테스트

| 입력/조건 | 기대 |
| --- | --- |
| 연결 3개 중 DONE 1개 | 3 / 1 / 33.33 |
| 연결 3개 중 DONE 2개 | 3 / 2 / 66.67 |
| 업무 0개 | 0 / 0 / 0, 100% 아님 |
| 다른 제출물·미연결 DONE 추가 | 현재 제출물 진척 변화 없음 |
| 한 업무에 담당자 2명 | 업무 수 1 |
| DONE을 IN_PROGRESS로 변경 | 완료 수 감소 |
| 필수 2개·선택 1개, 1개 충족 확인 | total=2, met=1, percent=50.00 |
| 필수 0개·선택 2개 | total/met/percent=0, assessmentAvailable=true |
| 관찰자 | 조회 허용 |
| 비회원 / 다른 프로젝트 제출물 | 각각 403 / 404 |
| DB 예외 | 정상 0값 응답으로 숨기지 않음 |

## 7. 병합 순서

A-04(K-14) → B-10 서버/테스트 → C 실제 API 연결 순서다. B-10 브랜치는 A-04 위에서 작성했으므로 A-04를 `main`에 먼저 병합해야 한다.
