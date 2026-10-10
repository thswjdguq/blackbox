package com.blackbox.service;

import com.blackbox.dto.ReviewDtos.*;
import com.blackbox.entity.*;
import com.blackbox.exception.*;
import com.blackbox.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewRoundServiceTest {
    final ReviewRoundRepository rounds = mock(ReviewRoundRepository.class);
    final DeliverableRepository deliveries = mock(DeliverableRepository.class);
    final FileVaultRepository files = mock(FileVaultRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final ProjectRepository projects = mock(ProjectRepository.class);
    final UnresolvedCommentCounter unresolved = mock(UnresolvedCommentCounter.class);
    final DeliverableConfirmationRepository confirmations = mock(DeliverableConfirmationRepository.class);
    final DeliverableSubmissionRepository submissions = mock(DeliverableSubmissionRepository.class);
    final ProjectAccessChecker access = new ProjectAccessChecker(projects, members);
    final DeliverableStatuses statuses = new DeliverableStatuses(access, deliveries, rounds, confirmations, submissions);
    final ReviewRoundService service = new ReviewRoundService(rounds, deliveries, files, access, unresolved, statuses);
    final Project project = new Project();
    final Deliverable delivery = new Deliverable();
    final User reviewer = user("LEADER"), uploader = user("MEMBER"), observer = user("OBSERVER");

    @BeforeEach void setup() {
        project.setId(UUID.randomUUID());
        delivery.setId(UUID.randomUUID()); delivery.setProject(project);
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(deliveries.findByIdAndProject(delivery.getId(), project)).thenReturn(Optional.of(delivery));
        when(deliveries.lockByIdAndProject(delivery.getId(), project)).thenReturn(Optional.of(delivery));
        when(rounds.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test void statusFollowsLatestRound() {
        assertEquals("DRAFT", statusOf());
        assertEquals("IN_REVIEW", statusOf(round(1, null, null)));
        assertEquals("DRAFT", statusOf(round(1, null, "CHANGES_REQUESTED")));
        assertEquals("IN_REVIEW", statusOf(round(1, null, "APPROVED")));
        assertEquals("IN_REVIEW", statusOf(round(2, null, null), round(1, null, "CHANGES_REQUESTED")));
    }

    /** 확정과 제출 기록은 회차로 계산한 상태보다 먼저다(K-20 2장). 피드백 명령이 읽는 값에도 그대로 나온다. */
    @Test void confirmationAndSubmissionComeBeforeTheRoundStatus() {
        ReviewRound approved = round(1, null, "APPROVED");
        when(confirmations.existsById(delivery.getId())).thenReturn(true);
        assertEquals("CONFIRMED", statusOf(approved));
        assertEquals("CONFIRMED", service.get(project.getId(), delivery.getId(), approved.getId()).deliverableStatus());
        when(submissions.existsById(delivery.getId())).thenReturn(true);
        assertEquals("SUBMITTED", statusOf(approved));
        assertEquals("SUBMITTED", service.lockForWrite(project.getId(), delivery.getId(), approved.getId()).deliverableStatus());
    }

    @Test void openNumbersTheRoundAfterTheLatest() {
        existing();
        assertEquals(1, service.open(project.getId(), delivery.getId(), new OpenRequest(null), uploader).roundNo());
        existing(round(4, null, "APPROVED"), round(3, null, null));
        Round opened = service.open(project.getId(), delivery.getId(), new OpenRequest(null), uploader);
        assertEquals(5, opened.roundNo());
        assertTrue(opened.latest());
        assertNull(opened.file());
        assertNull(opened.decision());
        verify(deliveries, times(2)).lockByIdAndProject(delivery.getId(), project);
    }

    @Test void openRejectsFileOutsideTheProject() {
        existing();
        UUID foreign = UUID.randomUUID();
        when(files.findByIdAndProject(foreign, project)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.open(project.getId(), delivery.getId(), new OpenRequest(foreign), uploader));
        verify(rounds, never()).save(any());
    }

    @Test void observerReadsButCannotOpenOrDecide() {
        ReviewRound r = round(1, null, null);
        existing(r);
        assertEquals(1, service.list(project.getId(), delivery.getId(), observer).rounds().size());
        assertThrows(ForbiddenException.class, () -> service.open(project.getId(), delivery.getId(), new OpenRequest(null), observer));
        assertThrows(ForbiddenException.class, () -> service.decide(project.getId(), delivery.getId(), r.getId(), "APPROVED", observer));
        assertNull(r.getDecision());
    }

    @Test void uploaderCannotDecideOwnFileButAnyoneDecidesFilelessRound() {
        ReviewRound withFile = round(1, file("a"), null);
        existing(withFile);
        assertThrows(ForbiddenException.class, () -> service.decide(project.getId(), delivery.getId(), withFile.getId(), "APPROVED", uploader));
        assertNull(withFile.getDecision());

        ReviewRound fileless = round(2, null, null);
        existing(fileless, withFile);
        assertEquals("CHANGES_REQUESTED", service.decide(project.getId(), delivery.getId(), fileless.getId(), "CHANGES_REQUESTED", uploader).decision().result());
    }

    @Test void onlyLatestRoundIsDecidedAndOnlyOnce() {
        ReviewRound old = round(1, null, null), latest = round(2, null, null);
        existing(latest, old);
        assertConflict(() -> service.decide(project.getId(), delivery.getId(), old.getId(), "APPROVED", reviewer));
        assertNull(old.getDecision());

        Round first = service.decide(project.getId(), delivery.getId(), latest.getId(), "CHANGES_REQUESTED", reviewer);
        assertEquals(reviewer.getId(), first.decision().decidedBy().userId());
        var decidedAt = latest.getDecidedAt();
        // 같은 결정을 다시 보내면 그대로 돌려주고, 다른 사람이 보내도 결정자와 시각은 바뀌지 않는다
        Round again = service.decide(project.getId(), delivery.getId(), latest.getId(), "CHANGES_REQUESTED", uploader);
        assertEquals(reviewer.getId(), again.decision().decidedBy().userId());
        assertEquals(decidedAt, latest.getDecidedAt());
        assertConflict(() -> service.decide(project.getId(), delivery.getId(), latest.getId(), "APPROVED", reviewer));
        assertEquals("CHANGES_REQUESTED", latest.getDecision());
    }

    @Test void approvalNeedsZeroUnresolvedComments() {
        ReviewRound r = round(1, null, null);
        existing(r);
        when(unresolved.countUnresolvedComments(project.getId(), delivery.getId())).thenReturn(2L);
        var conflict = assertConflict(() -> service.decide(project.getId(), delivery.getId(), r.getId(), "APPROVED", reviewer));
        assertTrue(conflict.getReason().contains("2건"));
        assertNull(r.getDecision());
        assertEquals(2L, service.list(project.getId(), delivery.getId(), reviewer).unresolvedCommentCount());

        when(unresolved.countUnresolvedComments(project.getId(), delivery.getId())).thenReturn(0L);
        assertEquals("APPROVED", service.decide(project.getId(), delivery.getId(), r.getId(), "APPROVED", reviewer).decision().result());
    }

    @Test void changesCanBeRequestedWhileCommentsAreUnresolved() {
        ReviewRound r = round(1, null, null);
        existing(r);
        when(unresolved.countUnresolvedComments(project.getId(), delivery.getId())).thenReturn(3L);
        assertEquals("CHANGES_REQUESTED", service.decide(project.getId(), delivery.getId(), r.getId(), "CHANGES_REQUESTED", reviewer).decision().result());
    }

    @Test void approvalIsBasisOnlyWhileLatestRoundAndNewestFileHasSameContent() {
        FileVault approved = file("a");
        ReviewRound r = round(1, approved, "APPROVED");
        existing(r);
        newest(file("a"));                       // 같은 내용을 다시 올려 버전만 오른 경우
        assertTrue(basisOf(r));
        newest(file("b"));                       // 내용이 다른 새 버전
        assertFalse(basisOf(r));
        newest(file("a"));                       // 승인한 내용으로 되돌아온 경우
        assertTrue(basisOf(r));

        ReviewRound next = round(2, null, null);  // 새 회차가 열리면 이전 승인은 근거가 아니다
        existing(next, r);
        assertFalse(basisOf(r));

        ReviewRound fileless = round(3, null, "APPROVED");  // 파일 없는 회차의 승인
        existing(fileless, next, r);
        assertFalse(basisOf(fileless));
        assertFalse(basisOf(round(1, approved, "CHANGES_REQUESTED"), r));
    }

    @Test void readerReportsRoundStateAndLocksOnlyForWrite() {
        ReviewRound old = round(1, null, "CHANGES_REQUESTED"), latest = round(2, null, "APPROVED");
        existing(latest, old);
        var view = service.get(project.getId(), delivery.getId(), old.getId());
        assertEquals(1, view.roundNo());
        assertFalse(view.latest());
        assertEquals("CHANGES_REQUESTED", view.decision());
        assertEquals("IN_REVIEW", view.deliverableStatus());
        assertEquals(project.getId(), view.projectId());
        verify(deliveries, never()).lockByIdAndProject(any(), any());

        var locked = service.lockForWrite(project.getId(), delivery.getId(), latest.getId());
        assertTrue(locked.latest());
        assertEquals("APPROVED", locked.decision());
        verify(deliveries).lockByIdAndProject(delivery.getId(), project);
        assertThrows(NotFoundException.class, () -> service.get(project.getId(), delivery.getId(), UUID.randomUUID()));
        assertThrows(NotFoundException.class, () -> service.lockForWrite(project.getId(), UUID.randomUUID(), latest.getId()));
    }

    /** 미해결 수 구현이 정확히 하나일 때만 서버가 뜬다(K-10 5장). 기본 구현이 실제 조회 누락을 가리면 안 된다. */
    @Test void serviceStartsOnlyWithExactlyOneUnresolvedCommentCounter() {
        assertEquals(NoSuchBeanDefinitionException.class, startupFailure().getMostSpecificCause().getClass());
        assertEquals(NoUniqueBeanDefinitionException.class,
                startupFailure(OneCounter.class, AnotherCounter.class).getMostSpecificCause().getClass());
        try (var context = context(OneCounter.class)) {
            assertNotNull(context.getBean(ReviewRoundService.class));
        }
    }

    // 기본 구현(NoCommentsYet)을 직접 가리키지 않는다. 피드백 구현이 그 파일을 지워도 이 테스트는 그대로 돈다
    static class OneCounter implements UnresolvedCommentCounter {
        @Override public long countUnresolvedComments(UUID projectId, UUID deliverableId) { return 0; }
    }
    static class AnotherCounter extends OneCounter {}

    private UnsatisfiedDependencyException startupFailure(Class<?>... counters) {
        return assertThrows(UnsatisfiedDependencyException.class, () -> context(counters).close());
    }

    private AnnotationConfigApplicationContext context(Class<?>... counters) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(ReviewRoundRepository.class, () -> rounds);
        context.registerBean(DeliverableRepository.class, () -> deliveries);
        context.registerBean(FileVaultRepository.class, () -> files);
        context.registerBean(ProjectAccessChecker.class, () -> new ProjectAccessChecker(projects, members));
        context.registerBean(DeliverableStatuses.class, () -> statuses);
        context.register(ReviewRoundService.class);
        if (counters.length > 0) context.register(counters);
        context.refresh();
        return context;
    }

    private ResponseStatusException assertConflict(org.junit.jupiter.api.function.Executable call) {
        var thrown = assertThrows(ResponseStatusException.class, call);
        assertEquals(HttpStatus.CONFLICT, thrown.getStatusCode());
        return thrown;
    }

    private String statusOf(ReviewRound... latestFirst) {
        existing(latestFirst);
        return service.list(project.getId(), delivery.getId(), reviewer).deliverableStatus();
    }

    private boolean basisOf(ReviewRound target, ReviewRound... others) {
        if (others.length > 0) { var all = new ArrayList<>(List.of(target)); all.addAll(List.of(others)); existing(all.toArray(ReviewRound[]::new)); }
        return service.list(project.getId(), delivery.getId(), reviewer).rounds().stream()
                .filter(r -> r.id().equals(target.getId())).findFirst().orElseThrow().currentBasis();
    }

    private void existing(ReviewRound... latestFirst) {
        when(rounds.findByDeliverableOrderByRoundNoDesc(delivery)).thenReturn(List.of(latestFirst));
    }

    private void newest(FileVault file) {
        when(files.findTopByProjectAndFileNameOrderByVersionDesc(project, "보고서.pdf")).thenReturn(Optional.of(file));
    }

    private ReviewRound round(int no, FileVault file, String decision) {
        ReviewRound r = new ReviewRound(); r.setId(UUID.randomUUID()); r.setProject(project); r.setDeliverable(delivery);
        r.setRoundNo(no); r.setFile(file); r.setOpenedBy(uploader);
        if (decision != null) r.decide(decision, reviewer);
        return r;
    }

    private FileVault file(String content) {
        FileVault f = new FileVault(); f.setId(UUID.randomUUID()); f.setProject(project); f.setUploader(uploader);
        f.setFileName("보고서.pdf"); f.setFileHash(content.repeat(64)); f.setVersion(1);
        return f;
    }

    private User user(String role) {
        User u = new User(); u.setId(UUID.randomUUID()); u.setName(role);
        ProjectMember m = new ProjectMember(); m.setUser(u); m.setProject(project); m.setRole(role);
        when(members.findByProjectAndUser(project, u)).thenReturn(Optional.of(m));
        return u;
    }
}
