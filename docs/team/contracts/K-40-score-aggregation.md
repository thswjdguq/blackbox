# K-40 · 기여도 집계의 항목 응답과 검토 활동

- 버전: 0.1 / 상태: DRAFT
- 생산자: 손정협(B, 점수·경보 서버) / 초안 작성: 송승준(A) / 소비자: 손정효(C, 기여도 화면·프로젝트 홈·업무 보드), 송승준(A, 교수용 PDF·AI 도우미의 점수 도구)
- 기능: S1 / 작성일: 2026-11-08 / 기준 SHA: `5adea21`
- 근거: `docs/team/handoffs/A-score-criteria-20261019.md` 8장(#134 결정 기록)
- 범위: 점수 응답에 항목 목록(`items`)을 더하고, 검토 활동을 횟수로 보인다. **판정 기준(전체·부분·미참여)은 이 버전에서 바꾸지 않는다.** 판정은 결국 바꾼다. 어떻게 바꿀지는 쌓이는 사용 기록을 보며 검토하고(#134 8.6), 정해지면 이 계약의 다음 버전에서 바꾼다(7장)

## 1. 무엇을 약속하는가

- **판정은 이 버전에서 그대로다.** `participationLevel`은 지금처럼 네 항목(업무 완료, 회의 참석, 파일 업로드, 액션 아이템)으로 정한다
- **검토 활동은 이 버전에서 보이기만 한다.** 회차 결정, 피드백 작성·해결, 요구사항 충족 확인을 횟수로 보이고 판정에는 넣지 않는다. 나중에 판정에 넣을 때는 그 항목의 `countsTowardLevel`과 판정 규칙만 바꾸고 응답의 모양은 그대로 둔다
- **검토만 한 팀원에게 무임승차 경보를 내지 않는다.** 판정을 바꾸기 전까지 쓰는 임시 규칙이다(4.2)
- **사실은 도메인 자료에서 센다.** 검토 회차, 피드백, 요구사항 테이블에 이미 누가 언제 했는지가 있다. 활동 기록(`activity_logs`)으로 옮겨 세지 않으므로 과거 기록이 그대로 들어가고, 기록 호출이 빠져도 횟수가 틀리지 않는다
- **문구는 서버 한 곳에서 정한다.** 항목 이름과 설명을 응답에 담고, 화면·PDF·AI 도구는 받은 것을 그린다
- **세지 않는 것을 밝힌다.** 확정·제출 기록, AI 호출·채택 횟수, 같은 파일의 반복 업로드는 세지 않는다(#134 8.1)

## 2. 점수 응답

경로는 그대로다: GET `/api/projects/{projectId}/scores`, POST `/scores/recalculate`. 기존 칸은 바꾸지 않고 아래를 더한다.

```json
{
  "userId": "…",
  "name": "김민지",
  "taskParticipated": true,
  "meetingParticipated": false,
  "fileParticipated": true,
  "actionParticipated": false,
  "participationLevel": "PARTIAL",
  "calculatedAt": "2026-11-08T10:00:00+09:00",
  "criteriaVersion": "2026-11-v1",
  "period": { "from": "2026-09-08T00:00:00+09:00", "to": "2026-11-08T10:00:00+09:00" },
  "items": [
    { "code": "TASK_DONE", "label": "업무 완료", "description": "담당한 업무 중 완료한 업무 수", "count": 3, "passed": true, "countsTowardLevel": true },
    { "code": "MEETING_ATTENDANCE", "label": "회의 참석", "description": "체크인한 회의 수. 회의 수(total)의 절반 이상이면 충족", "count": 1, "total": 4, "passed": false, "countsTowardLevel": true },
    { "code": "FILE_UPLOAD", "label": "파일 업로드", "description": "올린 파일 수(같은 이름의 새 버전은 한 번으로 셈)", "count": 2, "passed": true, "countsTowardLevel": true },
    { "code": "ACTION_ITEM", "label": "회의 할 일", "description": "회의에서 나온 할 일 중 완료한 수", "count": 0, "passed": false, "countsTowardLevel": true },
    { "code": "REVIEW_ACTIVITY", "label": "검토 참여", "description": "회차 결정, 피드백 작성·해결, 요구사항 충족 확인의 합. 판정에는 넣지 않는다", "count": 5, "passed": true, "countsTowardLevel": false,
      "details": [
        { "code": "REVIEW_DECISION", "label": "회차 결정", "count": 1 },
        { "code": "FEEDBACK_WRITTEN", "label": "피드백 작성", "count": 3 },
        { "code": "FEEDBACK_RESOLVED", "label": "피드백 해결", "count": 1 },
        { "code": "REQUIREMENT_ASSESSED", "label": "요구사항 충족 확인", "count": 0 }
      ] }
  ]
}
```

- `items`의 순서와 `code`는 고정이다. 화면은 `code`로 가리고 `label`·`description`을 그대로 보인다
- `countsTowardLevel`이 `true`인 항목의 `passed`가 지금의 네 불리언과 같다. 둘이 다르면 서버 결함이다
- `total`은 비율로 판정하는 항목(회의 참석)에만 있다
- `criteriaVersion`: 판정 기준이나 항목 정의가 바뀌면 올린다. PDF와 AI 답에 함께 적어 어느 기준으로 센 것인지 남긴다
- `period.from`은 그 멤버의 가입 시각(`project_members.joined_at`), `to`는 `calculatedAt`이다. 회의 참석의 분모(`total`)에만 쓴다(3장)
- 관찰자는 응답에 없다(지금과 같다)

## 3. 항목별 세는 법

판정 항목 넷은 지금의 정의로 시작한다. #134 8.4의 결함 수정(B)이 들어가면 오른쪽 칸으로 바뀐다. **결함 수정이지만 판정 결과가 달라질 수 있다**(예: 액션 아이템만 끝낸 팀원은 업무 완료를 잃는다). 바뀌는 PR에서 `criteriaVersion`을 올리고, 판정이 달라지는 경우를 PR 본문에 적는다.

| `code` | 지금 | 결함 수정 뒤 | 출처 |
| --- | --- | --- | --- |
| `TASK_DONE` | 담당한 업무 중 `DONE`인 업무 수(액션 아이템 포함) | 액션 아이템을 뺀다 | 업무·담당자 |
| `MEETING_ATTENDANCE` | 프로젝트의 전체 회의 중 체크인한 회의 수(`count`)와 전체 회의 수(`total`) | `total`을 가입 뒤 열린 회의로 바꾼다(`period.from`) | 회의·참석자 |
| `FILE_UPLOAD` | 올린 파일의 서로 다른 이름 수. 같은 이름의 새 버전은 한 번이다. `passed`(1개 이상)는 지금과 같다 | 같음 | 파일 금고 |
| `ACTION_ITEM` | 업무 설명에 회의 표시 글자가 있는 `DONE` 업무 수 | 글자 대신 회의와의 연결로 가린다 | 업무 |

`total`이 0이면 회의 참석은 미충족이다(지금과 같다). 검토 활동은 판정에 넣지 않으므로 결함 수정과 상관없다.

| `code` | 세는 것 | 출처 |
| --- | --- | --- |
| `REVIEW_DECISION` | 그 사람이 결정(승인·수정 요청)한 회차 수 | `review_rounds.decided_by` |
| `FEEDBACK_WRITTEN` | 그 사람이 쓴 피드백 수 | `review_comments.author_id` |
| `FEEDBACK_RESOLVED` | 지금 해결 상태인 피드백 중 그 사람이 마지막으로 해결한 수. 다시 열렸다 다른 사람이 해결하면 그 사람의 것이 된다 | `review_comments.resolved_by` |
| `REQUIREMENT_ASSESSED` | 지금 충족 확인 상태인 요구사항 중 그 사람이 확인한 수. 문구가 바뀌어 확인이 풀린 것은 세지 않는다(K-14) | `deliverable_requirements.assessed_by` |

- 가입 시각은 회의 참석의 분모에만 쓴다. 다른 항목은 그 프로젝트에서 그 멤버가 한 것을 모두 센다. 멤버가 아니면 업무 담당·업로드·검토를 할 수 없으므로 가입 전 기록은 생기지 않는다

## 4. 활동 기록과 경보

### 4.1 활동 기록

활동 기록은 점수를 세는 데 쓰지 않고, 이탈 경보의 "최근 활동" 판정(`hasActivityAfter`, 2주 동안 없으면 생성, 최근 7일 안에 있으면 해제)에만 쓴다. 지금은 검토만 맡은 팀원이 기록이 없어 이탈 경보를 받을 수 있으므로(#134 4장), 빠진 경로에 기록을 더한다.

| 더할 `actionType` | 남기는 곳 | 담당 |
| --- | --- | --- |
| `REVIEW_OPEN`, `REVIEW_DECIDE` | 검토 회차 열기, 승인·수정 요청 | A |
| `REQUIREMENT_ASSESS` | 요구사항 충족 확인 | A |
| `TASK_MESSAGE` | 업무 대화 작성·답변 | B |

- 확정·제출 기록은 활동 기록에도 넣지 않는다(K-20 9장. 한 사람에게만 맡겨질 수 있다)
- 새 기록은 앞으로의 것만 남긴다. 과거 기록을 활동 기록으로 옮기지 않는다(점수는 도메인 자료에서 세므로 필요 없다)
- 메타데이터는 대상 id만 담는다(`reviewId`, `requirementId`, `taskId`). 본문은 담지 않는다
- 기록은 지금의 다른 기록처럼 본 동작과 같은 트랜잭션에서 남긴다(`ActivityLogService.record`, `REQUIRED`). 기록에 실패하면 본 동작도 취소된다
- 점수는 도메인 자료에서 세므로, 이 표의 기록이 없는 과거 동작도 횟수에는 들어간다. 이탈 경보의 "최근 활동"에만 앞으로의 기록이 쓰인다

### 4.2 무임승차 경보(판정을 바꾸기 전까지의 임시 규칙)

- 지금: 판정이 `NONE`이면 경보를 만든다. 그래서 검토만 맡은 팀원도 받는다
- 바꾼 뒤: 판정이 `NONE`**이고** `REVIEW_ACTIVITY.count`가 0일 때만 만든다. 이미 있는 경보는 판정이 `PARTIAL` 이상이 되거나 검토 활동이 생기면 해제한다(해제 사유: "검토에 참여했습니다")
- 이 규칙은 판정 기준을 바꾸지 않는다. 판정이 `NONE`인 것은 그대로 보이고, 경보만 내지 않는다
- 판정을 바꾸는 버전(7장)에서 이 규칙을 다시 본다. 검토 활동이 판정에 들어가면 이 규칙은 필요 없어진다
- 과부하 경보(`FULL`이 한 명)는 바꾸지 않는다

## 5. 다시 계산하는 때

지금(30분마다, 업무 완료, 수동 재계산)에 더해, 체크인·파일 업로드·회차 결정·피드백 작성과 해결·충족 확인 뒤에 그 프로젝트를 비동기로 다시 계산한다(#134 8.4의 결함 수정). 화면의 수동 재계산은 이 변경 뒤 C가 뺀다(#134 6.3).

## 6. 쓰는 곳

| 곳 | 하는 일 | 담당 |
| --- | --- | --- |
| 기여도 화면 | 네 항목 칸과 판정은 그대로, 그 아래 "검토 참여" 횟수와 세부. `description`을 도움말로. "항목마다 했는지만 봅니다. 양과 질은 재지 않습니다"를 늘 보인다 | C |
| 프로젝트 홈 기여도 카드 | 판정 배지는 그대로. 검토 참여 횟수를 함께 보인다 | C |
| 교수용 PDF | 항목 표를 `items`로 그리고 `criteriaVersion`과 `period`를 적는다 | A |
| AI 도우미 점수 도구(`get_scores`) | `items`를 그대로 넘기고, 지시문에 "판정에 넣지 않는 항목"을 밝힌다 | A |
| 업무 보드 | 점수 칸(`totalScore`)과 재계산 버튼을 뺀다(#134 6.3) | C |

## 7. 판정 기준의 변경(이 버전에서는 하지 않음)

- 판정은 결국 바꾼다. 이 버전은 바꿀 때 필요한 자료(항목별 `count`)를 먼저 쌓는 단계다
- 어떻게 바꿀지는 #134 8.6에 날짜, 본 자료(항목별 `count` 분포 등), 결론을 기록하며 검토한다. 정해지면 이 계약의 다음 버전에서 `countsTowardLevel`과 판정 규칙을 바꾸고 `criteriaVersion`을 올린다. 4.2의 임시 규칙도 그때 다시 본다
- 검토에 쓸 자료는 이 계약의 `items`로 충분하다. 따로 모으는 표는 만들지 않는다

## 8. 확인을 부탁하는 것

- 손정협님: 3장의 세는 법이 B의 결함 수정 계획과 맞는지, 4.1의 `TASK_MESSAGE`를 어느 동작(작성, 답변)에 남길지, 4.2의 무임승차 경보 규칙, 5장의 재계산 시점이 부담되지 않는지
- 손정효님: 6장의 화면이 2장의 응답만으로 그려지는지

## 9. 수용 테스트

- 검토만 한 팀원(회차 결정 1, 피드백 3)은 `REVIEW_ACTIVITY.count`가 4이고 판정은 지금과 같다
- `items`의 판정 항목 `passed`가 네 불리언과 같다
- 같은 이름의 파일을 세 번 올리면 `FILE_UPLOAD.count`는 1이다
- 피드백을 A가 해결하고 다시 연 뒤 B가 해결하면 `FEEDBACK_RESOLVED`는 B만 1이다
- 요구사항 문구가 바뀌어 확인이 풀리면 `REQUIREMENT_ASSESSED`에서 빠진다
- 결함 수정 뒤, 가입 전 회의는 `MEETING_ATTENDANCE.total`에 들어가지 않는다
- 판정이 `NONE`이고 검토 활동이 있는 팀원에게는 무임승차 경보가 생기지 않고, 이미 있던 경보는 해제된다. 검토 활동이 0이면 지금처럼 생긴다
- 확정·제출, AI 채택은 어느 항목에도 들어가지 않는다
- 회차를 결정한 팀원은 그 뒤 2주 안에 이탈 경보를 받지 않는다(4장의 기록)
