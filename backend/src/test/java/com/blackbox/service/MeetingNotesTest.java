package com.blackbox.service;

import com.blackbox.dto.UpdateMeetingRequest;
import com.blackbox.entity.*;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeetingNotesTest {
    final MeetingRepository meetings = mock(MeetingRepository.class);
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final NotionService notion = mock(NotionService.class);
    final MeetingService service = new MeetingService(meetings, mock(MeetingAttendeeRepository.class),
            members, mock(TaskRepository.class), mock(TaskService.class), new ProjectAccessChecker(projects, members),
            mock(ActivityLogService.class), mock(AlertService.class), mock(DiscordNotificationService.class), notion,
            mock(EntityManager.class));
    final Project project = new Project();
    final User user = new User();
    final Meeting meeting = new Meeting();

    @BeforeEach void setup() {
        project.setId(UUID.randomUUID()); user.setId(UUID.randomUUID());
        meeting.setId(UUID.randomUUID()); meeting.setProject(project); meeting.setCreatedBy(user);
        meeting.setNotes("기존 회의록"); meeting.setDecisions("기존 결정");
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(meetings.findById(meeting.getId())).thenReturn(Optional.of(meeting));
    }
    void role(String role) {
        ProjectMember member = new ProjectMember(); member.setRole(role);
        when(members.findByProjectAndUser(project, user)).thenReturn(Optional.of(member));
    }
    @Test void memberSavesMarkdownWithoutNotion() {
        role("MEMBER"); String notes = "## 안건\n\n- [ ] 자료 조사\n\n| 담당 | 업무 |\n| --- | --- |\n| 팀원 | 조사 |";
        var result = service.updateMeeting(project.getId(), meeting.getId(), new UpdateMeetingRequest(null, null, null, notes, null), user);
        assertEquals(notes, result.notes()); assertEquals("기존 결정", result.decisions());
        verifyNoInteractions(notion);
    }
    @Test void leaderCanClearContent() {
        role("LEADER");
        var result = service.updateMeeting(project.getId(), meeting.getId(), new UpdateMeetingRequest(null, null, null, "", ""), user);
        assertNull(result.notes()); assertNull(result.decisions());
    }
    @Test void observerCannotWrite() {
        role("OBSERVER");
        assertThrows(ForbiddenException.class, () -> service.updateMeeting(project.getId(), meeting.getId(), new UpdateMeetingRequest(null,null,null,"수정",null), user));
        assertEquals("기존 회의록", meeting.getNotes()); verify(meetings, never()).save(any());
    }
    @Test void observerCanRead() {
        role("OBSERVER"); assertEquals("기존 회의록", service.getMeeting(project.getId(), meeting.getId(), user).notes());
    }
    @Test void nonMemberCannotWrite() {
        assertThrows(ForbiddenException.class, () -> service.updateMeeting(project.getId(), meeting.getId(), new UpdateMeetingRequest(null,null,null,"수정",null), user));
        verify(meetings, never()).save(any());
    }
    @Test void crossProjectMeetingIsNotFound() {
        role("MEMBER"); Project other = new Project(); other.setId(UUID.randomUUID()); meeting.setProject(other);
        assertThrows(NotFoundException.class, () -> service.updateMeeting(project.getId(), meeting.getId(), new UpdateMeetingRequest(null,null,null,"수정",null), user));
        verify(meetings, never()).save(any());
    }
}
