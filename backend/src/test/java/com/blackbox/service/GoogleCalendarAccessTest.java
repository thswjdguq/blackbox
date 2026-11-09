package com.blackbox.service;

import com.blackbox.dto.*;
import com.blackbox.entity.*;
import com.blackbox.exception.*;
import com.blackbox.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleCalendarAccessTest {
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final GoogleCalendarTokenRepository tokens = mock(GoogleCalendarTokenRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final AiService ai = mock(AiService.class);
    final GoogleCalendarService service = new GoogleCalendarService("client", "secret", "https://example.test/callback",
            tokens, users, members, ai, new ProjectAccessChecker(projects, members));
    final Project project = new Project();
    final User actor = user(), teammate = user(), outsider = user();
    final ProjectMember membership = new ProjectMember();
    @BeforeEach void setUp() {
        project.setId(UUID.randomUUID()); membership.setProject(project); membership.setUser(actor); membership.setRole("MEMBER");
        var other = new ProjectMember(); other.setUser(teammate); other.setProject(project); other.setRole("MEMBER");
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(members.findByProjectAndUser(project, actor)).thenReturn(Optional.of(membership));
        when(members.findByProject(project)).thenReturn(List.of(membership, other));
    }
    @Test void allProjectEntrypointsRejectOutsidersBeforeTokenOrAiAccess() {
        assertThrows(ForbiddenException.class, () -> service.getConnectionStatus(project.getId(), outsider));
        assertThrows(ForbiddenException.class, () -> service.getAvailability(project.getId(), "invalid-date", outsider));
        assertThrows(ForbiddenException.class, () -> service.recommendMeetingTimes(recommend(List.of(actor.getId())), outsider));
        assertThrows(ForbiddenException.class, () -> service.createCalendarEvents(project.getId(), event(List.of(actor.getId())), outsider));
        verifyNoInteractions(tokens, ai, users);
    }
    @Test void unavailableProjectIs404BeforeTokenAccess() {
        assertThrows(NotFoundException.class, () -> service.getConnectionStatus(UUID.randomUUID(), actor));
        verifyNoInteractions(tokens, ai);
    }
    @Test void invalidAttendeeRejectsWholeEventAndRecommendationBeforeAnyExternalLookup() {
        for (UUID id : List.of(outsider.getId(), UUID.randomUUID())) {
            var ids = List.of(actor.getId(), id);
            assertThrows(ForbiddenException.class, () -> service.createCalendarEvents(project.getId(), event(ids), actor));
            assertThrows(ForbiddenException.class, () -> service.recommendMeetingTimes(recommend(ids), actor));
        }
        verifyNoInteractions(tokens, ai, users);
    }
    @Test void observerMayReadButCannotWriteCalendars() {
        membership.setRole("OBSERVER");
        assertEquals(2, service.getConnectionStatus(project.getId(), actor).members().size());
        clearInvocations(tokens);
        assertThrows(ForbiddenException.class, () -> service.createCalendarEvents(project.getId(), event(List.of()), actor));
        verifyNoInteractions(tokens);
    }
    @Test void validAttendeesAreDeduplicatedAndEmptyEventStillMeansSelf() {
        service.createCalendarEvents(project.getId(), event(List.of(actor.getId(), teammate.getId(), actor.getId())), actor);
        verify(tokens).findByUser(actor); verify(tokens).findByUser(teammate); verifyNoInteractions(users, ai);
        clearInvocations(tokens);
        service.createCalendarEvents(project.getId(), event(List.of()), actor);
        verify(tokens).findByUser(actor); verifyNoMoreInteractions(tokens);
    }
    @Test void validMemberCanReadAvailabilityAndRecommendWithoutConnectedAccounts() {
        assertEquals(2, service.getAvailability(project.getId(), "2026-11-10", actor).memberStatuses().size());
        assertTrue(service.recommendMeetingTimes(recommend(List.of()), actor).recommendations().isEmpty());
        verifyNoInteractions(ai, users);
    }
    private CalendarEventRequest event(List<UUID> ids) {
        return new CalendarEventRequest("합성 회의", "2026-11-10T10:00:00+09:00", "2026-11-10T11:00:00+09:00", ids, null);
    }
    private CalendarRecommendRequest recommend(List<UUID> ids) {
        return new CalendarRecommendRequest(project.getId(), "2026-11-10", ids, null, 60);
    }
    private static User user() { var user = new User(); user.setId(UUID.randomUUID()); user.setName("합성 사용자"); return user; }
}
