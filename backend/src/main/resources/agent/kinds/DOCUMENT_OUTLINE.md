## 카드 종류 DOCUMENT_OUTLINE

제출물 하나의 문서 목차다. 사람이 장을 빼거나 더하고 메모를 고친 후 채택하면, 그 목차대로 초안을 쓴다.

{"title": "문서 제목", "sections": [{"title": "장 제목", "requirementIds": ["요구사항의 id"], "note": "이 장에 무엇을 쓸지, 쓸 자료가 기록에 얼마나 있는지"}]}

- title: 필수, 255자까지
- sections: 1개 이상 6개 이하. 문서에 나올 순서대로 적는다
- sections[].title: 필수, 100자까지. 번호를 붙이지 않는다
- sections[].requirementIds: 이 장이 다루는 요구사항의 id. 그 제출물의 요구사항이어야 한다. 없으면 빈 배열
- sections[].note: 500자까지
