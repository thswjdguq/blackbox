package com.blackbox.agent.skill;

import com.blackbox.agent.AgentProperties;
import com.blackbox.agent.proposal.RequirementAddKind;
import com.blackbox.agent.proposal.RequirementEvidence.Quote;
import com.blackbox.agent.proposal.RequirementEvidence.RequirementQuote;
import com.blackbox.agent.proposal.RequirementReviseKind;
import com.blackbox.agent.run.AgentEvents;
import com.blackbox.dto.DeliverableDtos;
import com.blackbox.entity.ProjectBrief;
import com.blackbox.entity.User;
import com.blackbox.exception.CodedException;
import com.blackbox.service.BriefService;
import com.blackbox.service.DeliverableService;
import com.blackbox.service.DeliverableStatusReader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 빠졌거나 모호한 요구사항 찾기(K-31 5·6장). 제출물에 걸린 안내문의 문장과 등록된 요구사항에 번호를 붙여 모델에 보내고,
 * 모델이 단 근거 번호를 서버가 확인한다. 확인되지 않은 근거는 지우고, 근거가 남지 않은 카드는 만들지 않는다
 */
@Component
@Order(5)
@RequiredArgsConstructor
public class FindRequirementGapsSkill implements AgentSkill {
    public static final String NAME = "FIND_REQUIREMENT_GAPS";
    static final String NO_EVIDENCE = "근거를 확인할 수 없어";
    static final String ALREADY_REGISTERED = "이미 등록된 요구사항과 같아";
    private static final String REFS = "refs";
    // 목록의 항목은 한 문장이다(6.1). 줄 앞의 기호나 번호 뒤에 공백이 오면 항목으로 본다. 공백(\\s)에는 전각 공백도 든다
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*(?:[-*•·◦▪‣○●■□※>]|\\d{1,3}[.)]|\\(\\d{1,3}\\)|[가-하][.)]|[①-⑳])\\s",
            Pattern.UNICODE_CHARACTER_CLASS);
    // 숫자 바로 뒤의 마침표에서는 나누지 않는다. "2026. 10. 18.까지"가 날짜 조각 셋으로 갈라지지 않게 한다
    private static final Pattern SENTENCE_END = Pattern.compile("(?:(?<!\\d)\\.|[?!])(?=\\s)", Pattern.UNICODE_CHARACTER_CLASS);

    private final DeliverableService deliverables;
    private final BriefService briefs;
    private final AgentProperties properties;
    private final ObjectMapper json;

    @Override public String name() { return NAME; }
    @Override public String label() { return "빠졌거나 모호한 요구사항 찾기"; }
    // 필요한 자료는 모두 번호를 붙여 자료로 보낸다. 모델이 다른 기록을 조회해 근거 없는 지적을 섞지 않게 도구는 주지 않는다
    @Override public List<String> tools() { return List.of(); }
    @Override public List<String> proposalKinds() { return List.of(RequirementAddKind.NAME, RequirementReviseKind.NAME); }

    /** 실행 전에 입력을 확인한다(404·409·400). 모델이 읽는 글은 begin이 다시 읽은 자료로 만든다 */
    @Override public String material(UUID projectId, User user, JsonNode input) {
        return text(source(projectId, user, input));
    }

    private static String text(Source s) {
        StringBuilder text = new StringBuilder("제출물: ").append(oneLine(s.deliverable().title()));
        for (int b = 0; b < s.briefs().size(); b++) {
            BriefService.Text brief = s.briefs().get(b);
            text.append("\n\n[B").append(b + 1).append("] 과제 안내문 \"").append(oneLine(brief.title())).append("\"");
            List<Sentence> sentences = sentences(brief.body());
            for (int i = 0; i < sentences.size(); i++) text.append("\n[B").append(b + 1).append("-S").append(i + 1).append("] ").append(sentences.get(i).text());
        }
        text.append("\n\n등록된 요구사항");
        List<DeliverableDtos.RequirementResponse> requirements = s.deliverable().requirements();
        if (requirements.isEmpty()) text.append("\n없음");
        for (int i = 0; i < requirements.size(); i++) {
            DeliverableDtos.RequirementResponse r = requirements.get(i);
            text.append("\n[R").append(i + 1).append("] (").append(r.required() ? "필수" : "선택").append(") ").append(oneLine(r.content()));
        }
        return text.toString();
    }

    /** 한 줄이 번호 하나다. 요구사항 문구나 제목의 줄바꿈이 "[B1-S2] …" 같은 가짜 번호 줄을 만들지 않게 공백으로 바꾼다 */
    private static String oneLine(String s) { return s.replaceAll("\\R", " "); }

    /**
     * 걸린 안내문과 요구사항을 한 번 읽어, 번호 붙인 글을 모델에 주고 번호마다 원문을 담은 표를 입력에 붙인다.
     * complete가 그 표로 근거를 확인하고 카드에 원문을 넣는다. 같은 읽기에서 만들므로 모델이 본 번호와 표의 원문이 같다.
     * 도구를 쓰지 않아 모델의 답이 오기까지 다른 이벤트가 없으므로, 읽는 안내문을 먼저 알린다
     */
    @Override public Begun begin(UUID projectId, User user, JsonNode input, AgentEvents events) {
        Source s = source(projectId, user, input);
        s.briefs().forEach(b -> events.brief(b.id(), b.title(), false));
        ObjectNode refs = json.createObjectNode();
        for (int b = 0; b < s.briefs().size(); b++) {
            BriefService.Text brief = s.briefs().get(b);
            List<Sentence> sentences = sentences(brief.body());
            for (int i = 0; i < sentences.size(); i++) {
                Sentence t = sentences.get(i);
                refs.set("B" + (b + 1) + "-S" + (i + 1), json.valueToTree(new Quote(brief.id(), t.text(), t.start(), t.end(), brief.bodyHash())));
            }
        }
        List<DeliverableDtos.RequirementResponse> requirements = s.deliverable().requirements();
        for (int i = 0; i < requirements.size(); i++) {
            refs.set("R" + (i + 1), json.valueToTree(new RequirementQuote(requirements.get(i).id(), requirements.get(i).content())));
        }
        ObjectNode begun = ((ObjectNode) input).deepCopy();
        begun.set(REFS, refs);
        return new Begun(begun, text(s));
    }

    /** 근거 확인(6.3). 모델이 쓴 deliverableId·원문은 믿지 않고 서버가 채운다 */
    @Override public String complete(String kind, ObjectNode content, JsonNode input) {
        JsonNode refs = input.path(REFS);
        content.put("kind", kind);
        content.put("deliverableId", input.path("deliverableId").asString());
        Set<String> used = new LinkedHashSet<>();
        if (kind.equals(RequirementAddKind.NAME)) {
            if (kept(content.get("requirement"), refs, false, used).isEmpty() || kept(content.get("reason"), refs, false, used).isEmpty()) return NO_EVIDENCE;
            String wanted = key(content.path("requirement").path("content").asString(""));
            for (JsonNode ref : refs) if (ref.has("requirementId") && key(ref.path("content").asString()).equals(wanted)) return ALREADY_REGISTERED;
        } else {
            // 모델은 고칠 요구사항을 R 번호로 가리킨다(target). 서버가 id와 그때의 문구로 바꾼다
            JsonNode target = refs.path(content.path("target").asString(""));
            content.remove("target");
            if (!target.has("requirementId")) return NO_EVIDENCE;
            content.put("requirementId", target.path("requirementId").asString());
            content.put("currentContent", target.path("content").asString());
            if (kept(content.get("issue"), refs, true, used).isEmpty()) return NO_EVIDENCE;
            // 수정안은 안내문에 근거가 있을 때만 낸다. 없으면 확인 질문만 남긴다
            if (kept(content.get("revision"), refs, false, used).isEmpty()) content.putNull("revision");
            if (content.get("revision").isNull() && content.path("question").asString("").isBlank()) return NO_EVIDENCE;
        }
        ObjectNode quotes = content.putObject("evidenceText");
        ObjectNode requirements = json.createObjectNode();
        for (String ref : used) (ref.startsWith("B") ? quotes : requirements).set(ref, refs.get(ref));
        if (kind.equals(RequirementReviseKind.NAME)) content.set("evidenceRequirements", requirements);
        return null;
    }

    /**
     * 문장의 근거 번호 중 이 실행에 보낸 것만 남기고(6.3의 1), 남은 번호를 used에 더해 돌려준다.
     * R 번호는 모호한 점에만 인정한다(6.2). 문장이 객체가 아니면 근거가 없는 것이다
     */
    private static List<String> kept(JsonNode sentence, JsonNode refs, boolean requirementsAllowed, Set<String> used) {
        if (!(sentence instanceof ObjectNode s)) return List.of();
        JsonNode evidence = s.path("evidence");
        List<String> kept = new ArrayList<>();
        for (int i = 0; evidence.isArray() && i < evidence.size(); i++) {
            String id = evidence.get(i).asString("");
            if (refs.has(id) && (id.startsWith("B") || requirementsAllowed) && !kept.contains(id)) kept.add(id);
        }
        kept.forEach(s.putArray("evidence")::add);
        used.addAll(kept);
        return kept;
    }

    /** 같은 문구인지 볼 때 앞뒤 공백과 대소문자를 가리지 않는다(5장) */
    private static String key(String text) { return ProjectBrief.normalize(text).toLowerCase(Locale.ROOT); }

    private record Source(DeliverableDtos.Response deliverable, List<BriefService.Text> briefs) {}

    private Source source(UUID projectId, User user, JsonNode input) {
        // 다른 프로젝트의 제출물이면 여기서 404가 된다
        DeliverableDtos.Response deliverable = deliverables.get(projectId, AgentSkill.deliverableId(input), user);
        if (DeliverableStatusReader.isConfirmed(deliverable.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "확정된 제출물은 요구사항을 바꿀 수 없어 찾지 않습니다");
        }
        if (deliverable.briefs().isEmpty()) {
            throw new CodedException(HttpStatus.BAD_REQUEST, "NO_BRIEF", "제출물에 과제 안내문을 먼저 걸어 주세요", Map.of());
        }
        List<BriefService.Text> texts = briefs.texts(projectId, deliverable.briefs().stream().map(DeliverableDtos.BriefSummary::id).toList());
        int total = texts.stream().mapToInt(t -> t.body().length()).sum();
        int limit = properties.getBriefMaxLength();
        if (total > limit) {
            throw new CodedException(HttpStatus.BAD_REQUEST, "BRIEFS_TOO_LONG",
                    "걸린 안내문의 글자 수 합이 " + limit + "자를 넘습니다. 지금 " + total + "자입니다. 일부를 떼어 주세요",
                    Map.of("limit", limit, "total", total,
                            "briefs", texts.stream().map(t -> new Length(t.id(), t.title(), t.body().length())).toList()));
        }
        return new Source(deliverable, texts);
    }

    /** BRIEFS_TOO_LONG 응답의 briefs 항목(8장) */
    public record Length(UUID id, String title, int length) {}

    /** 본문의 한 문장. start·end는 본문 안의 위치이고 text는 그 사이의 글이다 */
    record Sentence(int start, int end, String text) {}

    /**
     * 문장 나누기(6.1). 줄바꿈마다 나누고, 한 줄 안에서는 마침표·물음표·느낌표 뒤의 공백에서 나눈다.
     * 숫자 바로 뒤의 마침표와 목록의 항목에서는 나누지 않는다(날짜 "2026. 10. 18.", 번호 "1. "). 문장 앞뒤의 공백은 위치에 넣지 않고, 빈 문장은 번호를 받지 않는다
     */
    static List<Sentence> sentences(String body) {
        List<Sentence> out = new ArrayList<>();
        int lineStart = 0;
        for (String line : body.split("\n", -1)) {
            int from = 0;
            if (!LIST_ITEM.matcher(line).find()) {
                Matcher end = SENTENCE_END.matcher(line);
                while (end.find()) { add(out, body, lineStart + from, lineStart + end.end()); from = end.end(); }
            }
            add(out, body, lineStart + from, lineStart + line.length());
            lineStart += line.length() + 1;
        }
        return out;
    }

    private static void add(List<Sentence> out, String body, int start, int end) {
        while (start < end && blank(body.charAt(start))) start++;
        while (end > start && blank(body.charAt(end - 1))) end--;
        if (start < end) out.add(new Sentence(start, end, body.substring(start, end)));
    }

    private static boolean blank(char c) { return Character.isWhitespace(c) || Character.isSpaceChar(c); }
}
