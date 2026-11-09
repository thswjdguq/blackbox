package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.exception.*;
import com.blackbox.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeetingAttendanceServiceTest {
    final MeetingRepository meetings = mock(MeetingRepository.class);
    final MeetingAttendeeRepository attendees = mock(MeetingAttendeeRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ActivityLogService logs = mock(ActivityLogService.class);
    final Project project = new Project();
    final User user = new User();
    final Meeting meeting = new Meeting();
    final MeetingAttendee attendee = new MeetingAttendee();
    final ProjectMember member = new ProjectMember();
    final OffsetDateTime start = OffsetDateTime.parse("2026-10-12T14:00:00+09:00");
    MeetingAttendanceService at(OffsetDateTime time) {
        return new MeetingAttendanceService(meetings, attendees, members, new ProjectAccessChecker(projects, members), logs,
                Clock.fixed(time.toInstant(), ZoneOffset.UTC));
    }
    @BeforeEach void setup() {
        project.setId(UUID.randomUUID()); user.setId(UUID.randomUUID()); user.setName("팀원");
        meeting.setId(UUID.randomUUID()); meeting.setProject(project); meeting.setMeetingDate(start);
        attendee.setMeeting(meeting); attendee.setUser(user);
        member.setUser(user); member.setProject(project); member.setRole("MEMBER");
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(members.findByProjectAndUser(project, user)).thenReturn(Optional.of(member));
        when(members.findByProject(project)).thenReturn(List.of(member));
        when(meetings.findById(meeting.getId())).thenReturn(Optional.of(meeting));
        when(meetings.findByProjectOrderByMeetingDateDesc(project)).thenReturn(List.of(meeting));
        when(attendees.findByMeeting(meeting)).thenReturn(List.of(attendee));
        when(attendees.findForAttendanceUpdate(meeting, user)).thenReturn(Optional.of(attendee));
    }
    @Test void onTimeAtGraceBoundaryAndIdempotentRetry() {
        var service = at(start.plusMinutes(10));
        assertEquals("PRESENT", service.mark(project.getId(), meeting.getId(), true, user).status());
        assertEquals("PRESENT", at(start.plusMinutes(20)).mark(project.getId(), meeting.getId(), true, user).status());
        assertEquals(start.plusMinutes(10).toInstant(), attendee.getCheckedAt().toInstant());
        verify(attendees, times(1)).save(attendee); verify(logs, times(1)).record(any(), any(), anyString(), anyString());
    }
    @Test void afterGraceIsLate() {
        assertEquals("LATE", at(start.plusMinutes(10).plusSeconds(1)).mark(project.getId(), meeting.getId(), true, user).status());
    }
    @Test void tooEarlyAndDeadlineRejectAttendance() {
        for (OffsetDateTime time : List.of(start.minusMinutes(11), start.plusMinutes(60))) {
            assertEquals(409, assertThrows(ResponseStatusException.class, () -> at(time).mark(project.getId(), meeting.getId(), true, user)).getStatusCode().value());
        }
        verify(attendees, never()).save(any());
    }
    @Test void absenceIsDerivedAtDeadlineWithoutChangingRows() {
        assertEquals("PENDING", at(start.plusMinutes(59)).list(project.getId(), meeting.getId(), user).attendees().get(0).status());
        assertEquals("ABSENT", at(start.plusMinutes(60)).list(project.getId(), meeting.getId(), user).attendees().get(0).status());
        verify(attendees, never()).save(any());
    }
    @Test void canDeclareAbsenceBeforeMeetingButCannotRewriteIt() {
        var service = at(start.minusDays(1));
        assertEquals("ABSENT", service.mark(project.getId(), meeting.getId(), false, user).status());
        assertThrows(ResponseStatusException.class, () -> at(start).mark(project.getId(), meeting.getId(), true, user));
    }
    @Test void observerReadOnlyAndExcludedFromRoster() {
        member.setRole("OBSERVER");
        assertTrue(at(start).list(project.getId(), meeting.getId(), user).attendees().isEmpty());
        assertThrows(ForbiddenException.class, () -> at(start).mark(project.getId(), meeting.getId(), true, user));
        verify(attendees, never()).save(any());
    }
    @Test void nonMemberAndCrossProjectAreDenied() {
        when(members.findByProjectAndUser(project, user)).thenReturn(Optional.empty());
        assertThrows(ForbiddenException.class, () -> at(start).list(project.getId(), meeting.getId(), user));
        when(members.findByProjectAndUser(project, user)).thenReturn(Optional.of(member));
        var other = new Project(); other.setId(UUID.randomUUID()); meeting.setProject(other);
        assertThrows(NotFoundException.class, () -> at(start).mark(project.getId(), meeting.getId(), true, user));
    }
    @Test void enrollingAddsOnlyOpenMeetingsAndDoesNotMarkPresent() {
        at(start).enroll(project, user);
        verify(attendees).save(argThat(a -> a.getMeeting() == meeting && !a.isCheckedIn() && a.getAttendanceStatus() == null));
        clearInvocations(attendees);
        at(start.plusMinutes(60)).enroll(project, user); verify(attendees, never()).save(any());
    }
    @Test void enrollingDoesNotDuplicateExistingMemberOrIncludeObserver() {
        when(attendees.findByMeetingAndUser(meeting, user)).thenReturn(Optional.of(attendee));
        at(start).enroll(project, user); member.setRole("OBSERVER"); at(start).enroll(project, user);
        verify(attendees, never()).save(any());
    }
    @Test void onlyLeaderCanCorrectAndCorrectionUpdatesCountFlag() {
        assertThrows(ForbiddenException.class, () -> at(start).correct(project.getId(), meeting.getId(), user.getId(), "PRESENT", user));
        member.setRole("LEADER"); attendee.setCheckedIn(true);
        assertEquals("ABSENT", at(start.plusHours(2)).correct(project.getId(), meeting.getId(), user.getId(), "ABSENT", user).status());
        assertFalse(attendee.isCheckedIn());
    }
    @Test void legacyAttendanceIsNotReclassified() {
        attendee.setCheckedIn(true);
        assertEquals("LEGACY", at(start.plusDays(1)).list(project.getId(), meeting.getId(), user).attendees().get(0).status());
    }
}
