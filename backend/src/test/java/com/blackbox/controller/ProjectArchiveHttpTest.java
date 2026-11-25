package com.blackbox.controller;

import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import com.blackbox.scheduler.ScoreScheduler;
import com.blackbox.security.JwtService;
import com.blackbox.service.ProjectAccessChecker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 프로젝트 보관(K-60). 실제 PostgreSQL과 인증을 쓰고, 테스트 트랜잭션은 끝나면 되돌린다 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ProjectArchiveHttpTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @MockitoBean ScoreScheduler scoreScheduler;

    User leader, member, observer, outsider;
    Project project;
    String base;

    @BeforeEach void setUp() {
        leader = user(em, "보관 팀장"); member = user(em, "보관 팀원"); observer = user(em, "보관 관찰자"); outsider = user(em, "보관 외부인");
        project = project(em, leader); project.setInviteCode(UUID.randomUUID().toString().substring(0, 8));
        member(em, project, leader, "LEADER"); member(em, project, member, "MEMBER"); member(em, project, observer, "OBSERVER");
        em.flush();
        base = "/api/projects/" + project.getId();
    }

    ResultActions call(HttpMethod method, String path, User u, String json) throws Exception {
        var req = request(method, path).header("Authorization", "Bearer " + jwt.generateAccessToken(u.getEmail()));
        if (json != null) req = req.contentType("application/json").content(json);
        return mvc.perform(req);
    }
    void archive() throws Exception { call(HttpMethod.POST, base + "/archive", leader, null).andExpect(status().isOk()); }
    ResultActions expectArchived(ResultActions result) throws Exception {
        return result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"))
                .andExpect(jsonPath("$.detail").value(ProjectAccessChecker.ARCHIVED_NOTICE));
    }

    @Test void onlyLeaderArchivesAndRepeatingKeepsTheFirstTime() throws Exception {
        call(HttpMethod.POST, base + "/archive", member, null).andExpect(status().isForbidden());
        call(HttpMethod.POST, base + "/archive", observer, null).andExpect(status().isForbidden());
        call(HttpMethod.POST, base + "/archive", outsider, null).andExpect(status().isForbidden());

        String first = call(HttpMethod.POST, base + "/archive", leader, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedNotice").value(ProjectAccessChecker.ARCHIVED_NOTICE))
                .andReturn().getResponse().getContentAsString();
        String again = call(HttpMethod.POST, base + "/archive", leader, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(archivedAt(first), archivedAt(again), "다시 보관해도 처음 시각이 남는다");
        assertNotNull(archivedAt(first));
        call(HttpMethod.GET, base, member, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedNotice").value(ProjectAccessChecker.ARCHIVED_NOTICE));

        call(HttpMethod.POST, base + "/unarchive", member, null).andExpect(status().isForbidden());
        call(HttpMethod.POST, base + "/unarchive", leader, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isEmpty()).andExpect(jsonPath("$.archivedNotice").isEmpty());
        call(HttpMethod.POST, base + "/unarchive", leader, null).andExpect(status().isOk());   // 보관되지 않은 프로젝트의 해제도 그대로
    }

    private static String archivedAt(String json) {
        var m = java.util.regex.Pattern.compile("\"archivedAt\":(\"[^\"]*\"|null)").matcher(json);
        return m.find() ? m.group(1) : "missing";
    }

    @Test void archivedProjectRefusesEveryWriteWith409() throws Exception {
        String meetingId = com.jayway.jsonpath.JsonPath.read(call(HttpMethod.POST, base + "/meetings", leader,
                "{\"title\":\"보관 전 회의\",\"meetingDate\":\"2026-11-20T10:00:00Z\"}").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        var deliverable = deliverable(em, project, "보관 전 제출물"); em.flush();
        archive();
        UUID any = UUID.randomUUID();
        expectArchived(call(HttpMethod.POST, base + "/tasks", member, "{\"title\":\"보관 후 업무\"}"));
        // 회원 검사만 하는 바깥 메서드가 안쪽의 업무 만들기 검사로 막힌다(K-60 6장)
        expectArchived(call(HttpMethod.POST, base + "/meetings/" + meetingId + "/action-items", member, "{\"title\":\"보관 후 할 일\"}"));
        expectArchived(call(HttpMethod.PUT, base + "/deliverables/" + deliverable.getId(), member,
                "{\"title\":\"바뀐 제출물\",\"dueDate\":\"2026-12-01\"}"));
        expectArchived(mvc.perform(multipart(base + "/files")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "보관.txt", "text/plain", "x".getBytes()))
                .header("Authorization", "Bearer " + jwt.generateAccessToken(member.getEmail()))));
        expectArchived(call(HttpMethod.PATCH, base, leader, "{\"name\":\"바뀐 이름\"}"));
        expectArchived(call(HttpMethod.POST, base + "/invite-code/regenerate", leader, null));
        expectArchived(call(HttpMethod.DELETE, base + "/members/me", member, null));
        expectArchived(call(HttpMethod.POST, "/api/projects/join", outsider, "{\"inviteCode\":\"" + project.getInviteCode() + "\"}"));
        expectArchived(call(HttpMethod.PUT, base + "/deliverables/" + any + "/confirmation", leader, "{\"reviewId\":\"" + any + "\"}"));
        expectArchived(call(HttpMethod.POST, base + "/deliverables/" + any + "/confirmation/cancellation", leader,
                "{\"reviewId\":\"" + any + "\",\"reason\":\"보관 후 취소\"}"));
        expectArchived(call(HttpMethod.POST, base + "/agent/runs", member, "{\"skill\":\"ASK\",\"input\":{\"question\":\"지금 상태는?\"}}"));
        expectArchived(call(HttpMethod.POST, base + "/agent/runs", observer, "{\"skill\":\"ASK\",\"input\":{\"question\":\"지금 상태는?\"}}"));
        expectArchived(call(HttpMethod.POST, base + "/agent/proposals/" + any + "/accept", member, null));
        expectArchived(call(HttpMethod.POST, base + "/agent/proposals/" + any + "/reject", member, null));
        expectArchived(call(HttpMethod.PUT, base + "/agent/proposals/" + any, member, "{\"content\":{}}"));
    }

    @Test void roleAndMembershipAreCheckedBeforeArchive() throws Exception {
        archive();
        // 관찰자의 쓰기는 지금처럼 403, 회원이 아닌 사람에게는 보관 여부를 알리지 않는다
        call(HttpMethod.POST, base + "/tasks", observer, "{\"title\":\"관찰자 업무\"}").andExpect(status().isForbidden());
        call(HttpMethod.GET, base, outsider, null).andExpect(status().isForbidden());
        call(HttpMethod.POST, base + "/tasks", outsider, "{\"title\":\"외부인 업무\"}").andExpect(status().isForbidden());
    }

    @Test void readsDownloadsAndPersonalSettingsStillWork() throws Exception {
        archive();
        call(HttpMethod.GET, base, observer, null).andExpect(status().isOk());
        call(HttpMethod.GET, base + "/tasks", member, null).andExpect(status().isOk());
        call(HttpMethod.GET, base + "/members", member, null).andExpect(status().isOk());
        call(HttpMethod.GET, base + "/report", member, null).andExpect(status().isOk());
        call(HttpMethod.GET, base + "/evidence-package", member, null).andExpect(status().isOk());
        call(HttpMethod.POST, base + "/consent", member,
                "{\"consentPlatform\":false,\"consentGithub\":false,\"consentDrive\":false,\"consentAiAnalysis\":false}")
                .andExpect(status().isOk());
        call(HttpMethod.GET, "/api/projects", member, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + project.getId() + "')].archivedNotice").value(ProjectAccessChecker.ARCHIVED_NOTICE));
    }

    @Test void unarchivingRestoresWritesAndDeletionRuleIsUnchanged() throws Exception {
        archive();
        call(HttpMethod.POST, base + "/unarchive", leader, null).andExpect(status().isOk());
        call(HttpMethod.POST, base + "/tasks", member, "{\"title\":\"해제 후 업무\"}").andExpect(status().isCreated());

        // 기록이 없는 프로젝트는 보관 중이어도 지금처럼 지울 수 있다(K-60 1장)
        User solo = user(em, "혼자 팀장"); Project empty = project(em, solo); member(em, empty, solo, "LEADER"); em.flush();
        String emptyBase = "/api/projects/" + empty.getId();
        call(HttpMethod.POST, emptyBase + "/archive", solo, null).andExpect(status().isOk());
        call(HttpMethod.DELETE, emptyBase, solo, null).andExpect(status().isNoContent());
    }
}
