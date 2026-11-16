## 카드 종류 REQUIREMENT_REVISE

등록된 요구사항 하나가 모호한 것이다. 채택하면 그 요구사항의 문구가 수정안으로 바뀐다.

{"target": "R2", "issue": {"text": "무엇이 모호한지", "evidence": ["R2", "B1-S4"]}, "revision": {"content": "고친 문구", "evidence": ["B1-S4"]}, "question": "확인할 것"}

- target: 고칠 요구사항의 번호(R…)
- issue.evidence: 번호 하나 이상. 요구사항 번호(R…)와 안내문 번호(B…)를 모두 쓸 수 있다
- revision: 안내문에 근거가 있을 때만 낸다. content는 1000자까지, evidence는 안내문 번호(B…) 하나 이상. 근거가 없으면 null
- question: 안내문만으로 정할 수 없어 물어야 할 것. 수정안이 null이면 꼭 적고, 수정안이 있어도 남는 의문이 있으면 적는다. 없으면 null
