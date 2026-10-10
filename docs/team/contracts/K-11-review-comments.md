# K-11 피드백 코멘트 계약

- 버전: 0.3 / 상태: AGREED (PR #59에서 A·C 승인 완료, 운영 구현 병합 전)
- 생산자: 손정협(B) / 소비자: 송승준(A, K-10·K-20), 손정효(C)
- 작성: 2026-09-30 / 기준: main `9ab0069`, CONTRACTS 3장
- 의존: K-10 회차 식별·수정 가능 여부·잠금 인터페이스, A의 DB 마이그레이션
- 관련 작업: B-20. K-10 초안 [#69](https://github.com/thswjdguq/blackbox/pull/69)을 기준으로 정렬했다. K-10·본 계약의 소비자 합의와 A-11 병합 전에는 운영 API를 구현하지 않는다.

## 1. 경로와 권한

기본 경로: `/api/projects/{projectId}/deliverables/{deliverableId}/reviews/{reviewId}/comments`
모든 ID는 UUID이며 기존 인증 필터를 사용한다. K-10과 같은 인증 경로를 사용하고 별도의 인증 방식을 추가하지 않는다.

| 명령 | 요청 | 성공 | 역할 |
| --- | --- | --- | --- |
| 목록 | GET 기본 경로 | 200 배열, 없으면 `[]` | 팀장·팀원·관찰자 |
| 작성 | POST 기본 경로, `{ "content": "출처를 추가해주세요." }` | 201 코멘트 | 팀장·팀원 |
| 해결 | PUT `/{commentId}/resolution`, `{ "reason": "인용과 출처를 추가했습니다." }` | 200 코멘트 | 팀장·팀원 |
| 다시 열기 | DELETE `/{commentId}/resolution` | 200 코멘트 | 팀장·팀원 |

작성·해결·다시 열기는 `requireContributor`, 조회는 `requireMember`로 서버에서도 검증한다.
프로젝트 참여 확인 뒤 제출물 → 회차 → 코멘트 소속을 확인한다. 타 프로젝트·타 제출물·타 회차 ID와 존재하지 않는 ID는 404이며 제목 등 추가 정보를 노출하지 않는다.
승인된 회차(`decision == APPROVED`), CONFIRMED·SUBMITTED 제출물은 쓰기를 409로 거절한다. 신규 코멘트는 최신 회차에만 작성할 수 있고 과거 회차 작성은 409다. 승인되지 않은 과거 회차의 해결·다시 열기는 허용한다. 이 검사에는 K-10의 회차 컨텍스트를 사용한다.

## 2. 필드와 응답

content는 trim 후 1~2000자 필수이며 null·생략·빈 문자열은 400이다. reason은 선택 입력이며 trim 후 최대 1000자다. null·생략·빈 문자열은 null로 저장한다. 해결 요청 본문은 JSON 객체(`{}` 허용)이며 본문 자체가 없으면 400이다. 작성자·시각·상태는 클라이언트가 지정하지 않는다.

```json
{
  "id": "33333333-3333-4333-8333-333333333333",
  "reviewId": "22222222-2222-4222-8222-222222222222",
  "content": "출처를 추가해주세요.",
  "status": "RESOLVED",
  "author": { "userId": "44444444-4444-4444-8444-444444444444", "name": "팀원" },
  "createdAt": "2026-09-30T10:00:00+09:00",
  "linkedTaskId": null,
  "resolution": {
    "resolvedBy": { "userId": "55555555-5555-4555-8555-555555555555", "name": "팀장" },
    "resolvedAt": "2026-09-30T11:00:00+09:00",
    "reason": "인용과 출처를 추가했습니다."
  }
}
```

linkedTaskId는 미연결이면 null. resolution은 미해결이면 null이며 필드는 생략하지 않는다.
시각은 OffsetDateTime 기반 시간대 포함 ISO-8601. 목록은 createdAt ASC, id ASC로 정렬한다. 페이지 처리는 1차 범위에서 생략한다.
작성 내용 수정·삭제 API는 1차 범위 밖으로 두고 기록을 보존한다.

## 3. 상태 전환과 감사 기록

| 이벤트 | 결과 |
| --- | --- |
| 작성 | OPEN |
| 연결 업무 DONE, 아직 미해결 | REFLECTION_PENDING (자동 해결 아님) |
| 연결 업무 DONE에서 다른 상태로 복구, 아직 미해결 | OPEN |
| 연결 업무 없는 OPEN에 해결 요청 | RESOLVED |
| 연결 업무가 DONE인 REFLECTION_PENDING에 해결 요청 | RESOLVED |
| 연결 업무가 DONE이 아닌 피드백 해결 요청 | RESOLVED (업무 상태를 바꾸지 않음) |
| RESOLVED 다시 열기 | 업무 DONE이면 REFLECTION_PENDING, 그 외 OPEN |

status는 DB에 저장하지 않고 조회 시 계산한다. 해결 정보가 있으면 RESOLVED, 해결 정보가 없고 연결 업무가 DONE이면 REFLECTION_PENDING, 나머지는 OPEN이다. 업무 상태 변경은 코멘트 행을 수정하지 않는다. 해결은 반영의 승인 판정이 아니므로 업무 완료 여부와 별개로 허용한다. 반영 판정은 검토 회차 승인으로 한다.
이미 해결된 항목에 해결 요청, 이미 미해결인 항목에 다시 열기 요청은 현재 응답을 반환하는 멱등 동작으로 제안한다. 단, 권한·회차 잠금 검사는 매번 수행한다.
다시 열 때 현재 resolution은 null로 바꾸고, 이전 해결자·해결 시각·사유는 활동 기록에 보존한다.
상태 전환 기록은 기존 `ActivityLogService.record(project, user, actionType, metadata)`를 사용한다. 신규 actionType의 기여도 영향은 별도 합의 전 점수 계산에 추가하지 않는다.

## 4. A에게 제공할 조회와 필요한 인터페이스

```java
// 제출물 전체의 모든 회차를 포함한다. RESOLVED만 제외한다.
long countUnresolvedComments(UUID projectId, UUID deliverableId);
```

K-10 승인과 K-20 확정은 같은 트랜잭션에서 이 읽기 전용 조회를 호출한다. 사용자 인자가 없는 내부 호출이므로 HTTP 엔드포인트로 노출하지 않는다. 호출 명령이 먼저 사용자 권한을 확인하고, 조회는 제출물의 프로젝트 소속을 확인한다.

필요한 A 인터페이스는 K-10 #69의 `ReviewRoundReader.get(projectId, deliverableId, reviewId)`와 `lockForWrite(projectId, deliverableId, reviewId)`다. 반환 `ReviewRoundView`의 projectId·deliverableId·reviewId·latest·decision·deliverableStatus로 경계와 쓰기 가능 여부를 확인한다. 사용자 권한은 B가 먼저 확인한다. 서로의 서비스를 직접 주입하지 않는다. 미해결 개수 인터페이스는 A가 선언하고 B가 실제 구현을 등록한다.

승인·확정·새 회차·코멘트 작성·해결·다시 열기·업무 연결은 같은 트랜잭션에서 동일 제출물 행의 PESSIMISTIC_WRITE 잠금을 먼저 획득한다. 회차·코멘트·업무에 별도 잠금을 추가하지 않는다. A의 승인·확정 검사도 잠금 후 미해결 수를 조회한다. 미해결 수는 해결 정보가 없는 코멘트 수이므로 연결 업무 상태 변경과 무관하다. 기본 0 구현은 코멘트 기능 미제공 기간에만 허용하며 B-20 적용 후 실제 조회 누락을 0으로 숨겨서는 안 된다.

## 5. DB 제안과 병합 순서

테이블 이름 `review_comments` 제안: id, project_id, deliverable_id, review_id, author_id, content, created_at, resolved_by, resolved_at, resolution_reason, linked_task_id. status 열은 만들지 않는다. #60 A 리뷰에 따라 업무 연결은 별도 테이블 대신 nullable UNIQUE linked_task_id로 두며 소속 제약·삭제 정책은 K-12 v0.3을 따른다. 연결 업무 삭제는 해결 기록을 초기화하지 않는다.
회차·제출물·프로젝트 소속을 복합 외래키로 보장한다. 해결자·해결 시각은 함께 존재하거나 함께 null이며, 미해결이면 사유도 null인 CHECK 제약을 제안한다. 해결 사유만 null인 해결 상태는 허용한다. 사용자 외래키는 연쇄 삭제 없이 유지한다.
K-10 #69에서 A가 V22를 K-11·K-12용으로 예약했다. 계약 합의 후 A가 작성하며 B가 migration을 생성하거나 기존 파일을 변경하지 않는다.

순서: K-10·K-11 소비자 합의 → A DB·최소 회차 조회/잠금 제공 → B-20 API·테스트 → C 실제 연동. 기존 #54의 boolean resolved·PATCH resolve 경로와 다르므로 C는 계약 합의 후 fixture·타입·화면을 함께 변경한다. 운영 API는 아직 없어 하위호환 운영은 필요하지 않다.

## 6. 오류와 완료 검증

기존 ProblemDetail 사용: 400 입력/UUID, 401 미인증, 403 역할/비회원, 404 소속 불일치/없음, 409 잠금된 상태/전환 불가, 500 내부 실패(가짜 성공 금지).
실패 예: `{ "type": "about:blank", "title": "Conflict", "status": 409, "detail": "승인된 검토 회차는 변경할 수 없습니다" }`.
필수 검사: 역할별 조회·쓰기, 변조 ID, 빈 목록, 상태 전환·다시 열기 이력, 중복 요청, 승인과 코멘트 작성 경쟁, 실제 PostgreSQL 저장·재조회·롤백, 인증 HTTP 요청. C fixture 경로는 합의 후 C가 관리한다.
현재 계약은 A·C의 승인을 마쳤다. 위 검사의 구현·실행 증거는 B-20 구현 작업에서 별도로 기록하며 계약 합의와 운영 구현 완료를 구별한다. A-11·V22(#91)의 병합은 아직 필요하다. #59의 A·C 의견에 따라 선택 사유, 조회 시 상태 계산, 미완료 업무의 해결 허용, 최신 회차 신규 작성, 제출물 단일 잠금을 반영했다.

K-10 v0.2 #69의 기본 미해결 수 0 구현은 일반 빈이다. B-20 실제 구현을 등록하는 PR에서 기본 구현 파일도 제거하며 A가 해당 A 소유 변경을 확인한다. 두 구현이 함께 등록되거나 둘 다 없는 상태는 서버 시작 단계에서 실패해야 한다. 정확한 파일명·인터페이스 패키지는 A-11 제공 후 확인하고 추측해 만들지 않는다.

## 7. 소비자 확인 기록 (2026-10-06)

C가 PR #59에서 v0.3의 네 경로, 응답·null, 선택 사유, 입력 길이, 미완료 업무 해결, 최신/과거 회차 쓰기 범위, 관찰자 읽기 전용을 #66과 대조하고 승인했다. B도 #66 최신 타입을 다시 대조했다. 이는 계약·화면 코드 확인이며 실제 서버 연동 검사가 아니다.

A도 PR #59에서 최종 승인했으며 계약은 2026-10-07 병합됐다. 2026-10-08 AGREED로 정리했다. A-11의 실제 인터페이스와 V22는 #91로 제공됐으나 병합은 대기 중이다. B-20 운영 구현 완료를 뜻하지 않는다. 이번 기록 추가는 규격 변경이 아니므로 계약 버전은 0.3을 유지한다.
