## 카드 종류 REQUIREMENT_ADD

안내문이 요구하는데 등록된 요구사항에 없는 것이다. 채택하면 그 제출물에 요구사항이 더해진다.

{"requirement": {"content": "요구사항 문구", "required": true, "evidence": ["B1-S4"]}, "reason": {"text": "왜 빠졌다고 보는지", "evidence": ["B1-S4"]}}

- requirement.content: 필수, 1000자까지. 안내문의 표현을 살려 한 문장으로 적는다
- requirement.required: "반드시", "필수"처럼 꼭 지켜야 하면 true, "권장"이면 false
- requirement.evidence, reason.evidence: 안내문 번호(B…) 하나 이상
