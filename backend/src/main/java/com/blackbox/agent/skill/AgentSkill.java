package com.blackbox.agent.skill;

import com.blackbox.agent.run.AgentEvents;
import com.blackbox.entity.User;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * 실행 종류 하나(K-30 2장). 빈으로 등록하면 실행할 수 있게 된다.
 * 지시문은 resources/agent/skills/{name}.md에 둔다. 없으면 서버가 뜨지 않는다.
 */
public interface AgentSkill {
    String name();

    /** 화면의 버튼에 보일 말 */
    String label();

    /** 기록을 만들 수 있는 사람(팀장·팀원)만 실행할 수 있는가. false면 관찰자도 실행한다 */
    default boolean contributorsOnly() { return true; }

    /** contributor(팀장·팀원)인지에 따라 이 사람이 실행할 수 있는가 */
    default boolean allows(boolean contributor) { return contributor || !contributorsOnly(); }

    /** 모델이 부를 수 있는 도구의 이름 */
    List<String> tools();

    /** 만들 수 있는 카드 종류. 비어 있으면 글만 만든다. 관찰자의 실행은 여기 무엇이 있든 카드를 만들지 않는다 */
    default List<String> proposalKinds() { return List.of(); }

    /**
     * 입력을 검증하고 모델에 자료로 넘길 글을 만든다. 입력이 틀리면 400, 다른 프로젝트의 id면 404가 되는 예외를 던진다.
     * 스트림이 시작되기 전에 부른다. begin이 글을 다시 정하면 모델은 그 글을 읽는다
     */
    String material(UUID projectId, User user, JsonNode input);

    /**
     * 실행이 자리를 얻은 뒤(409·429를 지난 뒤), 모델을 부르기 전에 한 번 부른다. 막힌 실행에서는 하지 않을 일을 여기서 한다
     * (예: 안내문 저장). 돌려준 입력을 complete가 받는다.
     * 기록을 읽어 카드의 근거로 남기는 실행은 여기서 읽은 것으로 모델에 줄 글도 정한다. material과 begin 사이에 기록이 바뀌어도
     * 모델이 읽은 글과 카드가 가리키는 근거가 같은 시점의 것이 된다
     */
    default Begun begin(UUID projectId, User user, JsonNode input, AgentEvents events) { return new Begun(input, null); }

    /** material이 null이면 material()이 만든 글을 그대로 모델에 준다 */
    record Begun(JsonNode input, String material) {}

    /**
     * 모델이 만든 카드 내용 중 서버가 이미 아는 값을 채운다. input은 begin이 돌려준 것이다.
     * 카드를 만들지 않을 것이면 그 이유("근거를 확인할 수 없어")를 돌려준다. 실행의 마지막 글에 "이유 n건은 제외했습니다"로 알린다
     */
    default String complete(String kind, ObjectNode content, JsonNode input) { return null; }

    static String requiredText(JsonNode input, String field, String label, int maxLength) {
        String text = input.path(field).asString("").strip();
        if (text.isEmpty()) throw badInput(label + " 입력이 필요합니다");
        if (text.length() > maxLength) throw badInput(label + " 길이는 최대 " + maxLength + "자입니다");
        return text;
    }

    static UUID deliverableId(JsonNode input) {
        try {
            return UUID.fromString(input.path("deliverableId").asString(""));
        } catch (IllegalArgumentException e) {
            throw badInput("제출물을 골라주세요");
        }
    }

    static ResponseStatusException badInput(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
