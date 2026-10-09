package com.blackbox.agent.run;

import com.blackbox.agent.proposal.ProposalKind;
import com.blackbox.agent.skill.AgentSkill;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 모델에 줄 지시문을 resources/agent의 파일에서 읽어 조립한다. 등록된 실행 종류나 카드 종류의 파일이 없으면 서버가 뜨지 않는다. */
@Component
public class AgentInstructions {
    private static final String ROOT = "agent/";
    private static final String COMMON = "common.md", TEXT_FORMAT = "format-text.md",
            PROPOSAL_FORMAT = "format-proposals.md", MATERIAL = "material.md", REPAIR = "repair.md";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final Map<String, String> files = new HashMap<>();

    public AgentInstructions(List<AgentSkill> skills, List<ProposalKind> kinds) {
        List.of(COMMON, TEXT_FORMAT, PROPOSAL_FORMAT, MATERIAL, REPAIR).forEach(this::load);
        skills.forEach(s -> load(skill(s.name())));
        kinds.forEach(k -> load(kind(k.name())));
    }

    /** 공통 규칙, 실행 종류의 지시문, 답의 형식 순서다. kinds가 비어 있으면 글로만 답하게 한다 */
    public String forRun(AgentSkill skill, List<String> kinds, UUID projectId) {
        StringBuilder text = new StringBuilder(fill(COMMON, Map.of("projectId", projectId.toString())))
                .append("\n\n").append(files.get(skill(skill.name())))
                .append("\n\n").append(files.get(kinds.isEmpty() ? TEXT_FORMAT : PROPOSAL_FORMAT));
        kinds.forEach(k -> text.append("\n\n").append(files.get(kind(k))));
        return text.toString();
    }

    /** 사용자가 준 글(안내문, 질문)을 자료 표시로 감싼다. 지시문에는 섞지 않는다 */
    public String material(String text) {
        // 글이 자료 표시를 흉내 내 바깥으로 나오지 못하게 표시와 같은 글자는 지운다
        for (String mark : PLACEHOLDER.split(files.get(MATERIAL))) text = text.replace(mark.strip(), "");
        return fill(MATERIAL, Map.of("material", text));
    }

    /** 형식에 맞지 않은 답을 고쳐 달라고 할 때 모델에 넘길 글. material은 material()이 감싼 것이다 */
    public String repair(String material, String answer, List<String> problems) {
        return fill(REPAIR, Map.of("material", material, "answer", answer, "problems", "- " + String.join("\n- ", problems)));
    }

    /** {{이름}}을 한 번에 바꾼다. 넣은 글 안의 {{…}}는 다시 바꾸지 않는다 */
    private String fill(String file, Map<String, String> values) {
        return PLACEHOLDER.matcher(files.get(file)).replaceAll(m -> Matcher.quoteReplacement(values.get(m.group(1))));
    }

    private static String skill(String name) { return "skills/" + name + ".md"; }
    private static String kind(String name) { return "kinds/" + name + ".md"; }

    private void load(String path) {
        try {
            files.put(path, new ClassPathResource(ROOT + path).getContentAsString(StandardCharsets.UTF_8).strip());
        } catch (IOException e) {
            throw new IllegalStateException("지시문 파일이 없습니다: " + ROOT + path, e);
        }
    }
}
