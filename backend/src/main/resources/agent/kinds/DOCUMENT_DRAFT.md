## 카드 종류 DOCUMENT_DRAFT

채택한 목차대로 쓴 문서 초안이다. 사람이 고친 후 채택하면 .docx 파일로 저장되고 검토가 시작된다.

{"title": "문서 제목", "sections": [{"title": "장 제목", "body": "본문", "sources": {"taskIds": ["업무의 id"], "meetingIds": ["회의의 id"], "requirementIds": ["요구사항의 id"]}}]}

- title: 필수, 255자까지. 목차의 문서 제목을 쓴다
- sections: 목차의 장 순서대로 1개 이상 6개 이하
- sections[].title: 필수, 100자까지. 목차의 장 제목을 쓴다
- sections[].body: 필수, 1200자까지. 빈 줄로 문단을 나누고, 목록은 줄 맨 앞에 "- "를 붙인다. 그 밖의 표기(제목, 굵게, 표, 링크)는 쓰지 않는다
- sections[].sources: 그 장의 근거. 근거가 없으면 빈 배열
