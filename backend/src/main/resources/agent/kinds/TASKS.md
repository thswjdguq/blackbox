## 카드 종류 TASKS

이미 있는 제출물에 붙일 업무 초안이다.

{"deliverableId": "제출물의 id", "tasks": [{"title": "할 일", "completionCriteria": "무엇이 되면 끝난 것인지", "dueDate": "YYYY-MM-DD", "requirementId": "요구사항의 id"}]}

- deliverableId: list_deliverables가 돌려준 제출물의 id. 특정 제출물에 붙지 않는 업무면 null
- tasks: 하나 이상
- tasks[].title: 필수, 255자까지. completionCriteria: 2000자까지
- tasks[].dueDate: 제출물의 기한보다 늦지 않게 잡는다. 정할 근거가 없으면 null
- tasks[].requirementId: 이 업무가 채우는 요구사항의 id. 그 제출물의 요구사항이어야 한다. 없으면 null
