package com.blackbox.service;

import com.blackbox.controller.MeetingController;
import com.blackbox.dto.UpdateMeetingRequest;
import com.blackbox.entity.*;
import com.blackbox.exception.*;
import com.blackbox.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeetingWriteAccessTest {
    final MeetingRepository meetings = mock(MeetingRepository.class);
    final MeetingAttendeeRepository attendees = mock(MeetingAttendeeRepository.class);
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final NotionService notion = mock(NotionService.class);
    final AiService ai = mock(AiService.class);
    final DiscordNotionNotifier notifier = mock(DiscordNotionNotifier.class);
    final MeetingService service = new MeetingService(meetings, attendees, members, mock(TaskRepository.class),
            mock(TaskService.class), new ProjectAccessChecker(projects, members), mock(ActivityLogService.class),
            mock(AlertService.class), mock(DiscordNotificationService.class), notion, mock(EntityManager.class));
    final MeetingController controller = new MeetingController(service, ai, notion, notifier);
    final User user = new User();
    final Project project = new Project();
    final Meeting meeting = new Meeting();
    final ProjectMember membership = new ProjectMember();
    @BeforeEach void setup() {
        user.setId(UUID.randomUUID()); project.setId(UUID.randomUUID()); meeting.setId(UUID.randomUUID());
        meeting.setProject(project); meeting.setCreatedBy(user); meeting.setTitle("합성 회의");
        meeting.setMeetingDate(OffsetDateTime.parse("2026-11-10T10:00:00+09:00"));
        membership.setUser(user); membership.setProject(project); membership.setRole("OBSERVER");
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(members.findByProjectAndUser(project, user)).thenReturn(Optional.of(membership));
        when(meetings.findById(meeting.getId())).thenReturn(Optional.of(meeting));
    }
    @Test void observerCanReadButAllExternalWritesFailBeforeCallingProviders() {
        assertEquals(meeting.getId(), service.getMeeting(project.getId(), meeting.getId(), user).id());
        assertThrows(ForbiddenException.class, () -> controller.summarizeMeeting(project.getId(), meeting.getId(), user));
        assertThrows(ForbiddenException.class, () -> controller.exportToNotion(project.getId(), meeting.getId(), Map.of(), user));
        assertThrows(ForbiddenException.class, () -> controller.syncToCalendar(project.getId(), meeting.getId(), user));
        assertThrows(ForbiddenException.class, () -> service.saveAiSummary(project.getId(), meeting.getId(), "요약", user));
        assertThrows(ForbiddenException.class, () -> service.saveNotionInfo(project.getId(), meeting.getId(), "page", user));
        verifyNoInteractions(ai, notion, notifier); verify(meetings, never()).save(any());
    }
    @Test void nonMemberAndCrossProjectCannotTriggerAi() {
        when(members.findByProjectAndUser(project, user)).thenReturn(Optional.empty());
        assertThrows(ForbiddenException.class, () -> controller.summarizeMeeting(project.getId(), meeting.getId(), user));
        when(members.findByProjectAndUser(project, user)).thenReturn(Optional.of(membership)); membership.setRole("MEMBER");
        var other = new Project(); other.setId(UUID.randomUUID()); meeting.setProject(other);
        assertThrows(NotFoundException.class, () -> controller.summarizeMeeting(project.getId(), meeting.getId(), user));
        verifyNoInteractions(ai, notion, notifier);
    }
    @Test void contributorCanSummarizeAndStoreNotionInfo() {
        membership.setRole("MEMBER"); when(ai.summarizeMeeting(anyString(), any(), any(), any())).thenReturn("합성 요약");
        assertNotNull(controller.summarizeMeeting(project.getId(), meeting.getId(), user).getBody());
        assertEquals("합성 요약", meeting.getAiSummary());
        service.saveNotionInfo(project.getId(), meeting.getId(), "synthetic-page", user);
        assertEquals("synthetic-page", meeting.getNotionPageId()); assertNotNull(meeting.getNotionSyncedAt());
    }
    @Test void recordedAttendanceStillLocksDateWithReadableReason() {
        membership.setRole("LEADER"); var attendee = new MeetingAttendee(); attendee.setCheckedIn(true);
        when(attendees.findByMeeting(meeting)).thenReturn(List.of(attendee));
        var ex = assertThrows(ResponseStatusException.class, () -> service.updateMeeting(project.getId(), meeting.getId(),
                new UpdateMeetingRequest(null, meeting.getMeetingDate().plusHours(1), null, null, null), user));
        assertEquals(409, ex.getStatusCode().value()); assertTrue(ex.getReason().contains("참석 기록")); assertFalse(ex.getReason().contains("?"));
        verify(meetings, never()).save(any());
    }
    @Test void invalidAttendanceWindowIs400WithReadableReason() {
        membership.setRole("LEADER");
        var ex = assertThrows(ResponseStatusException.class, () -> service.updateMeeting(project.getId(), meeting.getId(),
                new UpdateMeetingRequest(null, null, null, null, null, 10, 5), user));
        assertEquals(400, ex.getStatusCode().value()); assertTrue(ex.getReason().contains("0~120분")); assertFalse(ex.getReason().contains("?"));
    }
}
