# K-11 피드백 코멘트 계약

- 버전: 0.1 / 상태: DRAFT (A·C 확인 전, 구현 계약 아님)
- 생산자: 손정협(B) / 소비자: 송승준(A, K-10·K-20), 손정효(C)
- 작성: 2026-09-30 / 기준: main `9ab0069`, CONTRACTS 3장
- 의존: K-10 회차 식별·수정 가능 여부·잠금 인터페이스, A의 DB 마이그레이션
- 관련 작업: B-20. K-10 초안은 main에 없어 아래 호출 서명과 DB 이름은 제안이다.

## 1. 경로와 권한

기본 경로: `/api/projects/{projectId}/deliverables/{deliverableId}/reviews/{reviewId}/comments`
모든 ID는 UUID이며 기존 JWT 인증을 사용한다.

| 명령 | 요청 | 성공 | 역할 |
| --- | --- | --- | --- |
| 목록 | GET 기본 경로 | 200 배열, 없으면 `[]` | 팀장·팀원·관찰자 |
| 작성 | POST 기본 경로, `{ "content": "출처를 추가해주세요." }` | 201 코멘트 | 팀장·팀원 |
| 해결 | PUT `/{commentId}/resolution`, `{ "reason": "인용과 출처를 추가했습니다." }` | 200 코멘트 | 팀장·팀원 |
| 다시 열기 | DELETE `/{commentId}/resolution` | 200 코멘트 | 팀장·팀원 |

작성·해결·다시 열기는 `requireContributor`, 조회는 `requireMember`로 서버에서도 검증한다.
프로젝트 참여 확인 뒤 제출물 → 회차 → 코멘트 소속을 확인한다. 타 프로젝트·타 제출물·타 회차 ID와 존재하지 않는 ID는 404이며 제목 등 추가 정보를 노출하지 않는다.
승인된 회차, CONFIRMED·SUBMITTED 제출물은 쓰기를 409로 거절한다. 과거 수정 요청 회차의 미해결 피드백은 해결할 수 있어야 하므로 단순히 '최신 회차만 쓰기 가능'으로 제한하지 않는다. 과거 회차의 신규 코멘트 작성 가능 여부는 K-10과 합의한다.

## 2. 필드와 응답

content는 trim 후 1~2000자, reason은 trim 후 1~1000자로 제안한다. null·생략·빈 문자열은 400이다. 작성자·시각·상태는 클라이언트가 지정하지 않는다.

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
| 연결 업무가 DONE이 아닌 피드백 해결 요청 | 409 (제안, A·C 합의 필요) |
| RESOLVED 다시 열기 | 업무 DONE이면 REFLECTION_PENDING, 그 외 OPEN |

업무 상태 변경은 RESOLVED를 자동으로 변경하지 않는다. 다시 열기를 명시적으로 요청한다.
이미 해결된 항목에 해결 요청, 이미 미해결인 항목에 다시 열기 요청은 현재 응답을 반환하는 멱등 동작으로 제안한다. 단, 권한·회차 잠금 검사는 매번 수행한다.
다시 열 때 현재 resolution은 null로 바꾸고, 이전 해결자·해결 시각·사유는 활동 기록에 보존한다.
상태 전환 기록은 기존 `ActivityLogService.record(project, user, actionType, metadata)`를 사용한다. 신규 actionType의 기여도 영향은 별도 합의 전 점수 계산에 추가하지 않는다.

## 4. A에게 제공할 조회와 필요한 인터페이스

```java
// 제출물 전체의 모든 회차를 포함한다. RESOLVED만 제외한다.
long countUnresolvedComments(UUID projectId, UUID deliverableId);
```

K-10 승인과 K-20 확정은 같은 트랜잭션에서 이 읽기 전용 조회를 호출한다. 사용자 인자가 없는 내부 호출이므로 HTTP 엔드포인트로 노출하지 않는다. 호출 명령이 먼저 사용자 권한을 확인하고, 조회는 제출물의 프로젝트 소속을 확인한다.

필요한 A 인터페이스(서명 미합의): 프로젝트·제출물·회차 소속을 확인하는 읽기 컨텍스트, 회차 결정과 제출물 상태 조회, 제출물 행 잠금. ReviewCommentService와 ReviewService가 서로 직접 주입되어 순환 참조하지 않도록 읽기 인터페이스를 분리한다.

동시 요청 제안: 승인·확정·새 회차·코멘트 작성·해결·다시 열기·업무 연결은 동일 제출물 행의 PESSIMISTIC_WRITE 잠금을 먼저 획득한다. 잠금 순서는 제출물 → 회차 → 코멘트 → 업무로 통일한다. 단순 count 이후 승인하면 새 코멘트가 끼어들 수 있으므로 조회 자체만으로 안전하다고 간주하지 않는다. A의 K-10·K-20과 공동 확정할 항목이다.

## 5. DB 제안과 병합 순서

테이블 이름 `review_comments` 제안: id, project_id, deliverable_id, review_id, author_id, content, status, created_at, resolved_by, resolved_at, resolution_reason. 업무 연결은 K-12 연결 테이블로 분리한다.
회차·제출물·프로젝트 소속을 복합 외래키로 보장하고 status는 세 값만 허용한다. RESOLVED면 해결자·시각·사유가 존재하고 미해결이면 현재 해결 정보가 null인 CHECK 제약을 제안한다. 사용자 삭제 정책은 A와 결정한다.
DB migration은 A 작성. 열린 #57의 V22 예약은 제안이므로 번호를 B가 확정하거나 기존 migration을 수정하지 않는다.

순서: K-10·K-11 소비자 합의 → A DB·최소 회차 조회/잠금 제공 → B-20 API·테스트 → C 실제 연동. 기존 #54의 boolean resolved·PATCH resolve 경로와 다르므로 C는 계약 합의 후 fixture·타입·화면을 함께 변경한다. 운영 API는 아직 없어 하위호환 운영은 필요하지 않다.

## 6. 오류와 완료 검증

기존 ProblemDetail 사용: 400 입력/UUID, 401 미인증, 403 역할/비회원, 404 소속 불일치/없음, 409 잠금된 상태/전환 불가, 500 내부 실패(가짜 성공 금지).
실패 예: `{ "type": "about:blank", "title": "Conflict", "status": 409, "detail": "승인된 검토 회차는 변경할 수 없습니다" }`.
필수 검사: 역할별 조회·쓰기, 변조 ID, 빈 목록, 상태 전환·다시 열기 이력, 중복 요청, 승인과 코멘트 작성 경쟁, 실제 PostgreSQL 저장·재조회·롤백, 인증 HTTP 요청. C fixture 경로는 합의 후 C가 관리한다.
현재 이 문서는 초안이며 위 검사는 실행하지 않았다. A·C 확인 PR, 정확한 회차 컨텍스트 서명, DB 번호 및 잠금 합의가 미확정이다.
