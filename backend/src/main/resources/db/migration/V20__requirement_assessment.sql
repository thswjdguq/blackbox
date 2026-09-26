-- K-14 요구사항 충족 확인. assessed_at에 값이 있으면 충족, 둘 다 NULL이면 확인 전이다.
-- 기존 행은 모두 확인 전으로 시작한다.
ALTER TABLE deliverable_requirements ADD COLUMN assessed_by UUID REFERENCES users(id);
ALTER TABLE deliverable_requirements ADD COLUMN assessed_at TIMESTAMPTZ;
-- 응답을 만들 때 둘이 함께 있다고 가정하므로 DB에서도 짝을 강제한다.
ALTER TABLE deliverable_requirements ADD CONSTRAINT ck_requirement_assessment
    CHECK ((assessed_by IS NULL) = (assessed_at IS NULL));
