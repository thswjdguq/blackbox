package com.blackbox.agent.skill;

import com.blackbox.agent.skill.FindRequirementGapsSkill.Sentence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 안내문의 문장 나누기(K-31 6.1). 같은 본문이면 어느 제출물에서 찾든 번호와 위치가 같다 */
class FindRequirementGapsSkillTest {
    @Test void sentencesSplitAtLineBreaksAndSentenceEndsButNotInsideListItems() {
        String body = "웹 프로젝트 중간 과제\n\n보고서는 A4 10쪽 이내로 작성한다. 파일은 PDF로 낸다!\n"
                + "1. 표지에 팀 이름을 적는다. 학번도 적는다.\n- 참고문헌은 쪽수에 넣지 않는다\n"
                + "점수는 3.5점 만점이다?　발표는 10분이다.  \n  \n가. 들여 쓴 항목. 둘째 문장\n"
                + "2026. 10. 18. 오후 6시까지 낸다. 늦으면 받지 않는다.";

        List<Sentence> sentences = FindRequirementGapsSkill.sentences(body);

        assertEquals(List.of("웹 프로젝트 중간 과제", "보고서는 A4 10쪽 이내로 작성한다.", "파일은 PDF로 낸다!",
                "1. 표지에 팀 이름을 적는다. 학번도 적는다.", "- 참고문헌은 쪽수에 넣지 않는다",
                "점수는 3.5점 만점이다?", "발표는 10분이다.", "가. 들여 쓴 항목. 둘째 문장",
                "2026. 10. 18. 오후 6시까지 낸다.", "늦으면 받지 않는다."),
                sentences.stream().map(Sentence::text).toList());
        sentences.forEach(s -> assertEquals(s.text(), body.substring(s.start(), s.end()), "위치가 본문의 그 문장을 가리킨다"));
        assertEquals(sentences, FindRequirementGapsSkill.sentences(body));
    }
}
