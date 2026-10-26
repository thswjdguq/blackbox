package com.blackbox.service;

import com.blackbox.dto.MeetingResponse;
import com.blackbox.entity.*;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeetingOutcomeServiceTest {
    final MeetingService meetings = mock(MeetingService.class);
    final AiService ai = mock(AiService.class);
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final MeetingOutcomeService service = new MeetingOutcomeService(meetings,
            new ProjectAccessChecker(projects, members), ai, new ObjectMapper());
    final Project project = new Project();
    final User user = new User();
    final UUID meetingId = UUID.randomUUID();
    final String notes = "발표는 월요일로 확정했다. 설문을 작성한다.";

    @BeforeEach void setup() {
        project.setId(UUID.randomUUID()); user.setId(UUID.randomUUID());
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
    }
    void role(String role) {
        var member = new ProjectMember(); member.setRole(role);
        when(members.findByProjectAndUser(project, user)).thenReturn(Optional.of(member));
    }
    void notes(String text) {
        when(meetings.getMeeting(project.getId(), meetingId, user)).thenReturn(new MeetingResponse(
                meetingId, project.getId(), "회의", null, null, text, null, null, null, null, null, user.getId(), null));
    }
    @Test void extractsGroundedProposalsWithoutSaving() {
        role("MEMBER"); notes(notes);
        when(ai.rawCall(anyString(), eq(3500))).thenReturn("""
                {"decisions":[{"text":"월요일 발표","evidence":"발표는 월요일로 확정했다."}],
                 "actions":[{"text":"설문 작성","evidence":"설문을 작성한다."}]}
                """);
        var result = service.extract(project.getId(), meetingId, user);
        assertEquals("월요일 발표", result.decisions().get(0).text());
        assertEquals("설문 작성", result.actions().get(0).text());
        verify(meetings).getMeeting(project.getId(), meetingId, user);
        verifyNoMoreInteractions(meetings);
    }
    @Test void acceptsNoConclusions() {
        var result = service.parse("{\"decisions\":[],\"actions\":[]}", notes);
        assertTrue(result.decisions().isEmpty()); assertTrue(result.actions().isEmpty());
    }
    @Test void rejectsInventedEvidence() {
        var ex = assertThrows(ResponseStatusException.class, () -> service.parse(
                "{\"decisions\":[{\"text\":\"금요일 발표\",\"evidence\":\"금요일 확정\"}],\"actions\":[]}", notes));
        assertEquals(502, ex.getStatusCode().value());
    }
    @Test void rejectsMalformedOrMissingArrays() {
        for (String response : new String[]{"not json", "null", "{}", "{\"decisions\":[],\"actions\":null}"}) {
            assertEquals(502, assertThrows(ResponseStatusException.class, () -> service.parse(response, notes)).getStatusCode().value());
        }
    }
    @Test void observerCannotUseAi() {
        role("OBSERVER");
        assertThrows(ForbiddenException.class, () -> service.extract(project.getId(), meetingId, user));
        verifyNoInteractions(meetings, ai);
    }
    @Test void nonMemberCannotUseAi() {
        assertThrows(ForbiddenException.class, () -> service.extract(project.getId(), meetingId, user));
        verifyNoInteractions(meetings, ai);
    }
    @Test void stopsBeforeAiWhenMeetingIsOutsideProject() {
        role("MEMBER");
        when(meetings.getMeeting(project.getId(), meetingId, user)).thenThrow(new NotFoundException("회의를 찾을 수 없습니다"));
        assertThrows(NotFoundException.class, () -> service.extract(project.getId(), meetingId, user));
        verifyNoInteractions(ai);
    }
    @Test void rejectsBlankAndOversizedNotes() {
        role("LEADER");
        for (String text : new String[]{" ", "가".repeat(30001)}) {
            notes(text);
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.extract(project.getId(), meetingId, user)).getStatusCode().value());
        }
        verifyNoInteractions(ai);
    }
    @Test void providerFailureDoesNotExposeInternalMessage() {
        role("MEMBER"); notes(notes);
        when(ai.rawCall(anyString(), anyInt())).thenThrow(new IllegalStateException("private provider detail"));
        var ex = assertThrows(ResponseStatusException.class, () -> service.extract(project.getId(), meetingId, user));
        assertEquals(503, ex.getStatusCode().value());
        assertFalse(ex.getReason().contains("private provider detail"));
    }
}
