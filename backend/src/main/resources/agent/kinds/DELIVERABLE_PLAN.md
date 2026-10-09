## 카드 종류 DELIVERABLE_PLAN

제출물 하나와 그 요구사항, 업무다. 제출물이 여러 개면 카드도 여러 장 만든다.

{"deliverable": {"title": "제출물 이름", "description": "무엇을 내는지", "dueDate": "YYYY-MM-DD", "submissionMethod": "어디에 어떻게 내는지"}, "requirements": [{"key": "r1", "content": "채점되거나 꼭 지켜야 하는 것 하나", "required": true}], "tasks": [{"title": "할 일", "completionCriteria": "무엇이 되면 끝난 것인지", "dueDate": "YYYY-MM-DD", "requirementKey": "r1"}]}

- deliverable.title: 필수, 255자까지. description: 5000자까지. submissionMethod: 500자까지
- deliverable.dueDate: 자료에 기한이 없으면 null로 둔다. 추측하지 않는다
- requirements[].key: 이 카드 안에서만 쓰는 이름표다. 서로 달라야 한다
- requirements[].content: 필수, 1000자까지. 요구사항 하나에 한 가지만 적는다
- requirements[].required: 꼭 지켜야 하면 true, 권장이면 false
- tasks[].title: 필수, 255자까지. completionCriteria: 2000자까지
- tasks[].dueDate: 제출물의 기한보다 늦지 않게 잡는다. 정할 근거가 없으면 null
- tasks[].requirementKey: 이 업무가 채우는 요구사항의 key. 없으면 null
