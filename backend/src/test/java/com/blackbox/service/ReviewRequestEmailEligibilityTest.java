package com.blackbox.service;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.entity.*;
import com.blackbox.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewRequestEmailEligibilityTest {
    final MailNotificationProperties settings=new MailNotificationProperties();
    final ProjectRepository projects=mock(ProjectRepository.class);
    final UserRepository users=mock(UserRepository.class);
    final ProjectMemberRepository members=mock(ProjectMemberRepository.class);
    final ReviewRoundReader reviews=mock(ReviewRoundReader.class);
    final ReviewRoundRepository rounds=mock(ReviewRoundRepository.class);
    final PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
    final ReviewRequestEmailEligibility eligibility=new ReviewRequestEmailEligibility(settings,projects,users,members,reviews,rounds,transactions);
    @BeforeEach void transaction() { when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus()); }

    @Test void disabledAndEmptyListNeverStartATransaction() {
        settings.setRecipientAllowlist(List.of("synthetic@example.invalid")); assertTrue(eligibility.currentRecipient(request()).isEmpty());
        settings.setEnabled(true); settings.setRecipientAllowlist(List.of()); assertTrue(eligibility.currentRecipient(request()).isEmpty());
        verifyNoInteractions(projects,users,members,reviews,rounds); verify(transactions,never()).getTransaction(any());
    }
    @Test void unsupportedTypeAndTaskTargetNeverReadRepositories() {
        settings.setEnabled(true); settings.setRecipientAllowlist(List.of("synthetic@example.invalid"));
        var n=request(); n.setType("REVIEW_VERSION_POSTED"); assertTrue(eligibility.currentRecipient(n).isEmpty());
        n=request(); n.setTaskId(UUID.randomUUID()); assertTrue(eligibility.currentRecipient(n).isEmpty());
        n=request(); n.setReviewId(null); assertTrue(eligibility.currentRecipient(n).isEmpty());
        verifyNoInteractions(projects,users,members,reviews,rounds);
    }
    @Test void confirmedAndSubmittedReadModelsAreNotPending() {
        var n=request(); allowed(n);
        for (String status:List.of("CONFIRMED","SUBMITTED")) {
            when(reviews.get(n.getProjectId(),n.getDeliverableId(),n.getReviewId())).thenReturn(new ReviewRoundReader.ReviewRoundView(
                    n.getReviewId(),n.getDeliverableId(),n.getProjectId(),1,true,null,status));
            assertTrue(eligibility.currentRecipient(n).isEmpty());
        }
        verifyNoInteractions(rounds);
    }
    @Test void databaseFailureDuringProducerReadIsNotHidden() {
        var n=request(); allowed(n);
        var failure=new org.springframework.dao.DataAccessResourceFailureException("synthetic failure");
        when(reviews.get(n.getProjectId(),n.getDeliverableId(),n.getReviewId())).thenThrow(failure);
        assertSame(failure,assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,() -> eligibility.currentRecipient(n)));
    }
    private void allowed(UserNotification n) {
        settings.setEnabled(true); settings.setRecipientAllowlist(List.of("synthetic@example.invalid"));
        Project p=new Project(); p.setId(n.getProjectId()); User u=new User(); u.setId(n.getRecipientId()); u.setEmail("synthetic@example.invalid");
        ProjectMember m=new ProjectMember(); m.setProject(p); m.setUser(u); m.setRole("MEMBER");
        when(projects.findById(p.getId())).thenReturn(Optional.of(p)); when(users.findById(u.getId())).thenReturn(Optional.of(u));
        when(members.findByProjectAndUser(p,u)).thenReturn(Optional.of(m));
    }
    private UserNotification request() {
        UserNotification n=new UserNotification(); n.setType("REVIEW_REQUESTED"); n.setProjectId(UUID.randomUUID());
        n.setActorId(UUID.randomUUID()); n.setRecipientId(UUID.randomUUID()); n.setDeliverableId(UUID.randomUUID()); n.setReviewId(UUID.randomUUID());
        n.setEventKey("REVIEW_REQUESTED:"+n.getReviewId()); return n;
    }
}
