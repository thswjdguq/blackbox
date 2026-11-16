package com.blackbox.agent.proposal;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 요구사항 카드의 근거(K-31 6장). 근거 번호와 원문은 서버가 실행 때 정하고, 사람이 카드를 고쳐도 바뀌지 않는다.
 * 번호(B1-S4, R2)는 그 실행에서 붙인 이름이라 카드마다 원문을 함께 둔다
 */
public final class RequirementEvidence {
    /** 근거 번호를 단 문장 */
    public record Sentence(String text, List<String> evidence) {}

    /** B 번호의 원문. start·end는 그때 본문 안의 위치(UTF-16 글자 단위)이고, bodyHash가 지금 본문과 같을 때만 맞다 */
    public record Quote(UUID briefId, String text, int start, int end, String bodyHash) {}

    /** R 번호가 가리킨 요구사항과 실행 때의 문구 */
    public record RequirementQuote(UUID requirementId, String content) {}

    private RequirementEvidence() {}

    static Set<UUID> briefIds(JsonNode content) {
        Set<UUID> ids = new HashSet<>();
        content.path("evidenceText").forEach(q -> ids.add(UUID.fromString(q.path("briefId").asString())));
        return ids;
    }

    /** 고치기 전 내용에서 parent.field만 사람이 보낸 값으로 바꾼다. 보내지 않았거나 고치기 전에 parent가 없으면 그대로 둔다 */
    static void take(ObjectNode kept, JsonNode edited, String parent, String field) {
        JsonNode value = edited.path(parent).path(field);
        if (!value.isMissingNode() && kept.get(parent) instanceof ObjectNode p) p.set(field, value);
    }
}
