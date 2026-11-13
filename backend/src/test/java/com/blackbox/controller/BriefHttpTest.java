package com.blackbox.controller;

import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.AgentRun;
import com.blackbox.entity.*;
import com.blackbox.security.JwtService;
import com.blackbox.service.UnresolvedCommentCounter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * K-31 0.3의 과제 안내문 공간(2장)과 제출물 연결(3장)을 실제 DB와 HTTP 요청으로 확인한다(13장의 삭제·잠금·프로젝트 삭제 줄).
 * 테스트마다 트랜잭션 하나로 묶여 끝나면 되돌아간다.
 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class BriefHttpTest {
    static final String BRIEFS = "/api/projects/{projectId}/briefs";
    static final String LINK = "/api/projects/{projectId}/deliverables/{deliverableId}/briefs/{briefId}";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @MockitoBean UnresolvedCommentCounter unresolved;

    User leader, member, observer, outsider;
    Project project;
    Deliverable report, slides;

    @BeforeEach void setup() {
        leader = user(em, "송팀장"); member = user(em, "손팀원"); observer = user(em, "관찰자"); outsider = user(em, "외부인");
        project = project(em, leader);
        member(em, project, leader, "LEADER"); member(em, project, member, "MEMBER"); member(em, project, observer, "OBSERVER");
        report = deliverable(em, project, "중간 보고서");
        slides = deliverable(em, project, "발표자료");
    }

    /** 등록·조회·수정·목록. 앞뒤 공백은 떼고 저장하고, 본문을 고치면 해시가 바뀐다 */
    @Test void membersSaveAndEveryoneReads() throws Exception {
        FileVault file = file(em, project, member, "과제안내.pdf", 1, 'a');
        JsonNode created = save(member, null, "  웹 프로젝트 중간 과제  ", "  1. 분량은 10쪽 이내\n2. PDF로 제출  ", file.getId(), 201);
        String id = created.get("id").asString();
        assertEquals("웹 프로젝트 중간 과제", created.get("title").asString());
        assertEquals("1. 분량은 10쪽 이내\n2. PDF로 제출", created.get("body").asString());
        assertEquals(sha256("1. 분량은 10쪽 이내\n2. PDF로 제출"), created.get("bodyHash").asString());
        assertEquals("과제안내.pdf", created.at("/sourceFile/fileName").asString());
        assertEquals(1, created.at("/sourceFile/version").asInt());
        assertEquals("손팀원", created.at("/createdBy/name").asString());
        assertEquals(0, created.at("/usage/deliverables").size());
        assertEquals(0, created.at("/usage/pendingProposals").asLong());
        assertFalse(created.get("locked").asBoolean());

        JsonNode edited = save(leader, id, "웹 프로젝트 중간 과제", "1. 분량은 12쪽 이내", null, 200);
        assertNotEquals(created.get("bodyHash"), edited.get("bodyHash"));
        assertEquals("송팀장", edited.at("/updatedBy/name").asString());
        assertEquals("손팀원", edited.at("/createdBy/name").asString());
        assertTrue(edited.get("sourceFile").isNull());
        assertEquals(edited, call(get(BRIEFS + "/{id}", project.getId(), id), observer, 200));

        save(member, null, "추가 공지", "발표는 7분", null, 201);

        // 눈에 같은 글은 같은 해시다. macOS에서 붙여 넣은 풀어쓴 자모(NFD)와 Windows 줄바꿈을 저장 전에 맞춘다
        String nfd = java.text.Normalizer.normalize("분량은 10쪽\r\nPDF로 제출", java.text.Normalizer.Form.NFD);
        JsonNode pasted = save(member, null, "붙여 넣은 과제", nfd, null, 201);
        assertEquals("분량은 10쪽\nPDF로 제출", pasted.get("body").asString());
        assertEquals(sha256("분량은 10쪽\nPDF로 제출"), pasted.get("bodyHash").asString());
        assertEquals(List.of("붙여 넣은 과제", "추가 공지", "웹 프로젝트 중간 과제"),
                call(get(BRIEFS, project.getId()), observer, 200).findValuesAsString("title"), "최근 수정이 먼저다");
    }

    @Test void refusals() throws Exception {
        String id = save(member, null, "과제", "본문", null, 201).get("id").asString();
        save(observer, null, "과제", "본문", null, 403);
        save(observer, id, "과제", "본문", null, 403);
        call(delete(BRIEFS + "/{id}", project.getId(), id), observer, 403);
        call(post(LINK, project.getId(), report.getId(), id), observer, 403);
        call(get(BRIEFS, project.getId()), outsider, 403);

        // 화면은 detail을 그대로 보여 준다. 서버의 언어 설정과 관계없이 한국어로, 지금 길이와 함께 알린다
        assertEquals("제목은 최대 200자입니다. 지금 201자입니다", save(member, null, "가".repeat(201), "본문", null, 400).get("detail").asString());
        assertEquals("본문은 최대 20000자입니다. 지금 20001자입니다", save(member, null, "과제", "가".repeat(20001), null, 400).get("detail").asString());
        save(member, null, "과제", "  " + "가".repeat(20000) + "  ", null, 201);   // 앞뒤 공백을 뗀 길이로 잰다
        assertEquals("제목을 입력해 주세요", save(member, null, "   ", "본문", null, 400).get("detail").asString());
        // 한글 문서에서 붙여 넣은 전각 공백만 있는 본문도 빈 본문이다
        assertEquals("본문을 입력해 주세요", save(member, null, "과제", "\u3000\u3000\n", null, 400).get("detail").asString());
        // 웹 페이지에서 복사한 줄바꿈 없는 공백도 공백이다. 파일에서 복사할 때 앞에 붙는 BOM은 뗀다
        assertEquals("본문을 입력해 주세요", save(member, null, "과제", "\u00A0\u00A0", null, 400).get("detail").asString());
        assertEquals("본문", save(member, null, "과제", "\uFEFF본문\u00A0", null, 201).get("body").asString());
        JsonNode missing = call(post(BRIEFS, project.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"과제\"}"), member, 400);
        assertEquals("본문을 입력해 주세요", missing.at("/errors/body").asString());

        // 다른 프로젝트의 파일·안내문·제출물
        Project other = project(em, outsider);
        member(em, other, outsider, "LEADER");
        save(member, null, "과제", "본문", file(em, other, outsider, "남의 파일.pdf", 1, 'b').getId(), 404);
        String othersBrief = save(outsider, null, "남의 안내문", "본문", null, 201, other).get("id").asString();
        call(get(BRIEFS + "/{id}", project.getId(), othersBrief), member, 404);
        call(post(LINK, project.getId(), report.getId(), othersBrief), member, 404);
        call(post(LINK, project.getId(), deliverable(em, other, "남의 제출물").getId(), id), member, 404);
    }

    /** 한 안내문을 두 제출물에, 한 제출물에 여러 안내문을 건다. 다시 걸기·떼기는 지금 배열을 그대로 돌려준다 */
    @Test void linkAndUnlinkAreIdempotentAndShowOnDeliverables() throws Exception {
        String brief = save(member, null, "웹 프로젝트 중간 과제", "본문", null, 201).get("id").asString();
        String notice = save(member, null, "추가 공지", "발표는 7분", null, 201).get("id").asString();

        link(report, brief, 200);
        JsonNode both = link(report, notice, 200);
        assertEquals(List.of("웹 프로젝트 중간 과제", "추가 공지"), both.findValuesAsString("title"), "건 순서다");
        assertEquals(both, link(report, brief, 200));
        assertEquals(sha256("발표는 7분"), both.get(1).get("bodyHash").asString());
        assertFalse(both.get(0).get("linkedAt").isNull());
        link(slides, brief, 200);

        JsonNode one = call(get("/api/projects/{p}/deliverables/{d}", project.getId(), report.getId()), observer, 200);
        assertEquals(both, one.get("briefs"));
        for (JsonNode d : call(get("/api/projects/{p}/deliverables", project.getId()), observer, 200))
            assertEquals(d.get("id").asString().equals(report.getId().toString()) ? 2 : 1, d.get("briefs").size());

        JsonNode usage = call(get(BRIEFS + "/{id}", project.getId(), brief), observer, 200).get("usage");
        assertEquals(List.of("중간 보고서", "발표자료"), usage.get("deliverables").findValuesAsString("title"));
        assertEquals(List.of("DRAFT", "DRAFT"), usage.get("deliverables").findValuesAsString("status"));

        JsonNode left = unlink(report, brief, 200);
        assertEquals(List.of("추가 공지"), left.findValuesAsString("title"));
        assertEquals(left, unlink(report, brief, 200));
    }

    /** 쓰는 곳이 있으면 삭제가 409이고 usage가 2.1과 같은 모양이다. 거절한 카드는 막지 않는다 */
    @Test void deleteIsRefusedWhileInUse() throws Exception {
        String brief = save(member, null, "과제", "본문", null, 201).get("id").asString();
        link(report, brief, 200);

        JsonNode refused = call(delete(BRIEFS + "/{id}", project.getId(), brief), member, 409);
        assertEquals("BRIEF_IN_USE", refused.get("code").asString());
        assertEquals("이 안내문을 쓰는 곳이 있어 지울 수 없습니다(제출물 '중간 보고서'). 제출물에서 떼거나 제안을 처리한 뒤 지워 주세요",
                refused.get("detail").asString());
        assertEquals(call(get(BRIEFS + "/{id}", project.getId(), brief), member, 200).get("usage"), refused.get("usage"));
        assertEquals(report.getId().toString(), refused.at("/usage/deliverables/0/id").asString());

        unlink(report, brief, 200);
        AgentProposal pending = proposal(brief);
        refused = call(delete(BRIEFS + "/{id}", project.getId(), brief), member, 409);
        assertEquals(0, refused.at("/usage/deliverables").size());
        assertEquals(1, refused.at("/usage/pendingProposals").asLong());
        assertEquals("이 안내문을 쓰는 곳이 있어 지울 수 없습니다(검토 대기 제안 1개). 제출물에서 떼거나 제안을 처리한 뒤 지워 주세요",
                refused.get("detail").asString());

        em.find(AgentProposal.class, pending.getId()).reject(member, OffsetDateTime.now());
        call(delete(BRIEFS + "/{id}", project.getId(), brief), member, 204);
        call(get(BRIEFS + "/{id}", project.getId(), brief), member, 404);
    }

    /** 확정된 제출물에 걸린 안내문은 고칠 수 없고, 그 제출물에서는 걸기·떼기가 409다. 다른 제출물에는 건다 */
    @Test void confirmedDeliverableLocksItsBriefs() throws Exception {
        String brief = save(member, null, "과제", "본문", null, 201).get("id").asString();
        String notice = save(member, null, "추가 공지", "공지", null, 201).get("id").asString();
        link(report, brief, 200);
        ReviewRound approved = round(em, report, 1, file(em, project, member, "보고서.pdf", 1, 'a'), member, "APPROVED");
        call(put("/api/projects/{p}/deliverables/{d}/confirmation", project.getId(), report.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reviewId\":\"" + approved.getId() + "\"}"), leader, 200);

        JsonNode locked = call(get(BRIEFS + "/{id}", project.getId(), brief), member, 200);
        assertTrue(locked.get("locked").asBoolean());
        assertEquals("CONFIRMED", locked.at("/usage/deliverables/0/status").asString());
        JsonNode refused = save(member, brief, "과제", "고친 본문", null, 409);
        assertEquals("BRIEF_LOCKED", refused.get("code").asString());
        assertEquals("확정된 제출물('중간 보고서')이 쓰는 안내문은 고칠 수 없습니다. 바뀐 내용은 추가 공지로 새로 등록해 주세요",
                refused.get("detail").asString());
        assertEquals("본문", call(get(BRIEFS + "/{id}", project.getId(), brief), member, 200).get("body").asString());

        link(report, notice, 409);
        unlink(report, brief, 409);
        assertEquals(1, link(slides, brief, 200).size());
        assertFalse(call(get(BRIEFS + "/{id}", project.getId(), notice), member, 200).get("locked").asBoolean());
    }

    /** 안내문이 걸린 제출물이 있는 프로젝트도 지워진다(13장). 제출물을 지우면 연결만 지워진다 */
    @Test void deletingDeliverableOrProjectRemovesLinks() throws Exception {
        String brief = save(member, null, "과제", "본문", null, 201).get("id").asString();
        link(report, brief, 200);
        link(slides, brief, 200);

        call(delete("/api/projects/{p}/deliverables/{d}", project.getId(), slides.getId()), member, 204);
        assertEquals(1, call(get(BRIEFS + "/{id}", project.getId(), brief), member, 200).at("/usage/deliverables").size());

        call(delete("/api/projects/{p}", project.getId()), leader, 204);
        assertNull(em.find(ProjectBrief.class, UUID.fromString(brief)));
        assertEquals(0L, em.createQuery("select count(l) from DeliverableBrief l where l.brief.id = :id", Long.class)
                .setParameter("id", UUID.fromString(brief)).getSingleResult());
    }

    /** 이 안내문을 근거로 한 검토 대기 카드 하나 */
    private AgentProposal proposal(String briefId) {
        AgentRun run = AgentRun.start(project, member, "FIND_REQUIREMENT_GAPS", OffsetDateTime.now());
        em.persist(run);
        AgentProposal p = AgentProposal.propose(run, "REQUIREMENT_ADD", "빠진 요구사항", "근거", json.createObjectNode(), OffsetDateTime.now());
        em.persist(p);
        em.flush();
        em.createNativeQuery("insert into agent_proposal_briefs (proposal_id, brief_id) values (:p, :b)")
                .setParameter("p", p.getId()).setParameter("b", UUID.fromString(briefId)).executeUpdate();
        return p;
    }

    private JsonNode link(Deliverable d, String briefId, int expectedStatus) throws Exception {
        return call(post(LINK, project.getId(), d.getId(), briefId), member, expectedStatus);
    }

    private JsonNode unlink(Deliverable d, String briefId, int expectedStatus) throws Exception {
        return call(delete(LINK, project.getId(), d.getId(), briefId), member, expectedStatus);
    }

    private JsonNode save(User as, String id, String title, String body, UUID sourceFileId, int expectedStatus) throws Exception {
        return save(as, id, title, body, sourceFileId, expectedStatus, project);
    }

    private JsonNode save(User as, String id, String title, String body, UUID sourceFileId, int expectedStatus, Project in) throws Exception {
        var node = json.createObjectNode().put("title", title).put("body", body);
        if (sourceFileId != null) node.put("sourceFileId", sourceFileId.toString());
        var request = id == null ? post(BRIEFS, in.getId()) : put(BRIEFS + "/{id}", in.getId(), id);
        return call(request.contentType(MediaType.APPLICATION_JSON).content(node.toString()), as, expectedStatus);
    }

    private static String sha256(String text) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    /** 요청마다 서버가 DB에서 새로 읽도록 영속성 컨텍스트를 비운다. */
    private JsonNode call(MockHttpServletRequestBuilder request, User as, int expectedStatus) throws Exception {
        em.flush(); em.clear();
        request.header("Authorization", "Bearer " + jwt.generateAccessToken(as.getEmail()));
        var response = mvc.perform(request).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        assertEquals(expectedStatus, response.getStatus(), response.getContentAsString());
        return response.getContentAsString().isEmpty() ? json.createObjectNode() : json.readTree(response.getContentAsString());
    }
}
