package com.blackbox.service;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.entity.*;
import com.blackbox.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskRequestEmailEligibilityTest {
    final MailNotificationProperties settings=new MailNotificationProperties();
    final ProjectRepository projects=mock(ProjectRepository.class);
    final UserRepository users=mock(UserRepository.class);
    final ProjectMemberRepository members=mock(ProjectMemberRepository.class);
    final TaskWorkflowService workflow=mock(TaskWorkflowService.class);
    final PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
    final TaskRequestEmailEligibility eligibility=new TaskRequestEmailEligibility(settings,projects,users,members,workflow,transactions);

    @BeforeEach void transactions() { when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus()); }

    @Test void disabledOrEmptyAllowlistNeverReadsRepositories() {
        settings.setRecipientAllowlist(List.of("synthetic@example.invalid"));
        assertTrue(eligibility.currentRecipient(request()).isEmpty());
        settings.setEnabled(true); settings.setRecipientAllowlist(List.of());
        assertTrue(eligibility.currentRecipient(request()).isEmpty());
        verifyNoInteractions(projects,users,members,workflow);
        verify(transactions,never()).getTransaction(any());
    }
    @Test void unsupportedTypesAndBrokenShapeNeverReadRepositories() {
        settings.setEnabled(true); settings.setRecipientAllowlist(List.of("synthetic@example.invalid"));
        UserNotification n=new UserNotification(); n.setType("REVIEW_REQUESTED");
        assertTrue(eligibility.currentRecipient(n).isEmpty());
        n.setType("TASK_COMPLETION_REQUESTED"); assertTrue(eligibility.currentRecipient(n).isEmpty());
        n.setProjectId(UUID.randomUUID()); n.setRecipientId(UUID.randomUUID()); n.setActorId(UUID.randomUUID()); n.setTaskId(UUID.randomUUID()); n.setReviewId(UUID.randomUUID());
        assertTrue(eligibility.currentRecipient(n).isEmpty()); verifyNoInteractions(projects,users,members,workflow);
    }
    @Test void databaseFailureIsNotSilentlyConvertedToIneligible() {
        settings.setEnabled(true); settings.setRecipientAllowlist(List.of("synthetic@example.invalid"));
        UserNotification n=request();
        Project p=new Project(); p.setId(n.getProjectId());
        User recipient=new User(); recipient.setId(n.getRecipientId()); recipient.setEmail("synthetic@example.invalid");
        ProjectMember member=new ProjectMember(); member.setProject(p); member.setUser(recipient); member.setRole("MEMBER");
        when(projects.findById(n.getProjectId())).thenReturn(Optional.of(p));
        when(users.findById(n.getRecipientId())).thenReturn(Optional.of(recipient));
        when(members.findByProjectAndUser(p,recipient)).thenReturn(Optional.of(member));
        var failure=new org.springframework.dao.DataAccessResourceFailureException("synthetic failure");
        when(workflow.view(p.getId(),n.getTaskId(),recipient)).thenThrow(failure);
        assertSame(failure,assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,() -> eligibility.currentRecipient(n)));
    }
    private UserNotification request() {
        UserNotification n=new UserNotification(); n.setType("TASK_COMPLETION_REQUESTED");
        n.setProjectId(UUID.randomUUID()); n.setRecipientId(UUID.randomUUID()); n.setActorId(UUID.randomUUID()); n.setTaskId(UUID.randomUUID()); return n;
    }
}
