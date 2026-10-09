package com.blackbox.agent.proposal;

import com.blackbox.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Validator;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 제안 카드의 종류 하나(K-30 3장). content의 모양을 정한다. 빈으로 등록하면 쓸 수 있게 된다.
 * 모델에게 알려 줄 content의 형식은 resources/agent/kinds/{name}.md에 둔다. 없으면 서버가 뜨지 않는다.
 */
public interface ProposalKind {
    String name();

    /** content를 이 종류의 모양으로 읽는다. 모델이나 사람이 보낸 것을 저장하기 전에 지난다 */
    Checked check(UUID projectId, User user, JsonNode content);

    /**
     * content는 이 종류가 아는 칸만 남긴 내용이고, problems는 형식에 맞지 않는 곳("어디: 무엇")이다.
     * problems가 비어 있을 때만 content를 저장한다.
     */
    record Checked(JsonNode content, List<String> problems) {
        static Checked unreadable(JsonNode content) { return new Checked(content, List.of("형식을 읽을 수 없다")); }
    }

    /** 기존 API의 요청 객체에 붙어 있는 검증을 그대로 쓴다. skip은 넘어갈 필드 이름이다 */
    static List<String> violations(Validator validator, String where, Object request, String... skip) {
        Set<String> skipped = Set.of(skip);
        return validator.validate(request).stream()
                .filter(v -> !skipped.contains(v.getPropertyPath().toString()))
                .map(v -> where + "." + v.getPropertyPath() + ": " + v.getMessage())
                .sorted().toList();
    }
}
