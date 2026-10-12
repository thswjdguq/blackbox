package com.blackbox.agent.skill;

import com.blackbox.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    /** 입력을 검증하고 모델에 자료로 넘길 글을 만든다. 입력이 틀리면 400, 다른 프로젝트의 id면 404가 되는 예외를 던진다 */
    String material(UUID projectId, User user, JsonNode input);

    /** 모델이 만든 카드 내용 중 서버가 이미 아는 값을 채운다 */
    default void complete(ObjectNode content, JsonNode input) {}

    static String requiredText(JsonNode input, String field, String label, int maxLength) {
        String text = input.path(field).asText("").strip();
        if (text.isEmpty()) throw badInput(label + " 입력이 필요합니다");
        if (text.length() > maxLength) throw badInput(label + " 길이는 최대 " + maxLength + "자입니다");
        return text;
    }

    static ResponseStatusException badInput(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
