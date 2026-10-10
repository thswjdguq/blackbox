package com.blackbox.service;

import com.blackbox.dto.ConfirmationDtos.*;
import com.blackbox.entity.*;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import com.blackbox.service.ReviewRoundService.Basis;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 최종 확정과 제출 기록(K-20). 준비 조회와 확정 명령이 같은 검사를 쓴다. */
@Service @RequiredArgsConstructor @Transactional
public class DeliverableConfirmationService {
    // K-20 3장의 checks[].code. 응답에는 이 순서로 들어간다
    static final String REQUIRED_REQUIREMENTS = "REQUIRED_REQUIREMENTS", APPROVED_FILE = "APPROVED_FILE",
            NO_UNRESOLVED_COMMENTS = "NO_UNRESOLVED_COMMENTS";
    // 낸 시각이 서버 시각보다 이만큼까지 뒤여도 받는다. 기록하는 사람의 시계가 조금 빠를 수 있다(K-20 2장)
    static final Duration CLOCK_ALLOWANCE = Duration.ofMinutes(5);

    private final ProjectAccessChecker access;
    private final DeliverableRepository deliverables;
    private final DeliverableRequirementRepository requirements;
    private final ReviewRoundRepository rounds;
    private final ReviewRoundService reviews;
    private final UnresolvedCommentCounter unresolvedComments;
    private final ProjectMemberRepository members;
    private final DeliverableConfirmationRepository confirmations;
    private final DeliverableSubmissionRepository submissions;
    private final DeliverableStatuses statuses;

    // ── 조회와 명령 ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Response get(UUID projectId, UUID deliverableId, User user) {
        Project project = access.getProject(projectId);
        access.requireMember(project, user);
        return response(deliverables.findByIdAndProject(deliverableId, project)
                .orElseThrow(DeliverableConfirmationService::noDeliverable));
    }

    /** 팀장이 화면에서 본 회차(reviewId)를 최종본으로 확정한다. 되돌리는 명령은 없다. */
    public Response confirm(UUID projectId, UUID deliverableId, UUID reviewId, User user) {
        Project project = access.getProject(projectId);
        if (!"LEADER".equals(access.requireMember(project, user).getRole()))
            throw new ForbiddenException("최종본 확정은 팀장만 할 수 있습니다");
        Deliverable d = lock(project, deliverableId);
        requireRoundOf(d, reviewId);
        Optional<DeliverableConfirmation> confirmed = confirmations.findById(d.getId());
        if (confirmed.isPresent()) {
            // 같은 회차로 다시 보낸 것은 중복 클릭으로 보고 지금 상태를 돌려준다
            if (!confirmed.get().getReview().getId().equals(reviewId)) throw conflict("이미 확정된 제출물입니다");
            return response(d);
        }
        Readiness readiness = readiness(d, rounds.findFirstByDeliverableOrderByRoundNoDesc(d));
        for (Check check : readiness.checks()) if (!check.passed()) throw conflict(check.detail());
        // 세 검사를 통과했는데 대상이 다르면 팀장이 화면에서 본 것과 다른 파일이다
        if (!readiness.candidate().getId().equals(reviewId))
            throw conflict("확정하려는 대상이 바뀌었습니다. 새로고침해 다시 확인해주세요");
        confirmations.save(DeliverableConfirmation.of(readiness.candidate(), user, OffsetDateTime.now()));
        return response(d);
    }

    /** 학교 시스템 등에 낸 사실을 남긴다. 확정된 제출물에 한 번이고 고치는 명령은 없다. */
    public Response submit(UUID projectId, UUID deliverableId, SubmitRequest req, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        if (req.submittedAt().isAfter(OffsetDateTime.now().plus(CLOCK_ALLOWANCE)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제출 시각은 지금보다 뒤일 수 없습니다");
        Deliverable d = lock(project, deliverableId);
        DeliverableConfirmation confirmation = confirmations.findById(d.getId())
                .orElseThrow(() -> conflict("최종본을 확정한 뒤에 제출을 기록할 수 있습니다"));
        // 낸 곳을 비우면 제출물의 제출 경로를 쓴다. 둘 다 비어 있으면 없는 값으로 남는다
        String channel = StringUtils.hasText(req.channel()) ? req.channel() : d.getSubmissionMethod();
        // 기록 시각은 잠금을 잡은 뒤의 지금이다. 확정을 기다렸다 들어온 기록이 확정보다 앞선 시각으로 남지 않는다
        DeliverableSubmission submission = DeliverableSubmission.of(
                confirmation, channel, req.submittedAt(), req.note(), user, OffsetDateTime.now());
        Optional<DeliverableSubmission> recorded = submissions.findById(d.getId());
        // 같은 내용을 다시 보낸 것은 중복 클릭으로 보고 지금 상태를 돌려준다. 처음 기록한 사람과 시각이 남는다
        if (recorded.isEmpty()) submissions.save(submission);
        else if (!recorded.get().sameContentAs(submission)) throw conflict("이미 제출이 기록됐습니다");
        return response(d);
    }

    // ── 응답 ──────────────────────────────────────────────────────────────

    private Response response(Deliverable d) {
        Optional<ReviewRound> latest = rounds.findFirstByDeliverableOrderByRoundNoDesc(d);
        String status = statuses.of(d, latest).status();
        boolean sole = soleContributor(d.getProject());
        Optional<DeliverableConfirmation> confirmed = confirmations.findById(d.getId());
        if (confirmed.isEmpty()) {
            Readiness readiness = readiness(d, latest);
            Candidate candidate = readiness.candidate() == null ? null : Candidate.of(readiness.candidate());
            return Response.pending(status, readiness.checks(), candidate, sole);
        }
        DeliverableConfirmation confirmation = confirmed.get();
        // 확정 뒤에 내용이 다른 새 버전이 올라왔는지만 알린다. 확정본은 회차가 가리키는 버전 그대로다
        boolean fileStillLatest = reviews.basisOf(confirmation.getReview()) == Basis.CURRENT;
        return Response.confirmed(status, sole, Confirmation.from(confirmation, fileStillLatest),
                submissions.findById(d.getId()).map(Submission::from).orElse(null));
    }

    /** 팀장·팀원이 한 명뿐이면 올린 사람이 자기 파일을 승인할 수 없어 확정 조건을 채울 수 없다(K-20 5장). */
    private boolean soleContributor(Project project) {
        return members.countByProjectAndRole(project, "LEADER") + members.countByProjectAndRole(project, "MEMBER") <= 1;
    }

    // ── 세 검사 ───────────────────────────────────────────────────────────

    /** 세 검사와, 승인된 파일 검사를 통과했을 때의 확정 대상. 통과하지 못했으면 candidate는 null이다. */
    private record Readiness(List<Check> checks, ReviewRound candidate) {}

    private Readiness readiness(Deliverable d, Optional<ReviewRound> latest) {
        Check file = latest.map(this::fileCheck)
                .orElseGet(() -> new Check(APPROVED_FILE, false, "검토 회차가 없습니다. 파일을 골라 검토를 요청해주세요"));
        return new Readiness(List.of(requirementCheck(d), file, commentCheck(d)), file.passed() ? latest.get() : null);
    }

    private Check requirementCheck(Deliverable d) {
        long required = requirements.countByDeliverableAndRequiredTrue(d);
        long unmet = required - requirements.countByDeliverableAndRequiredTrueAndAssessedAtIsNotNull(d);
        // 필수 요구사항이 없으면 통과지만, 빈 목록을 모두 확인한 것처럼 적지 않는다
        String detail = required == 0 ? "필수 요구사항이 없습니다"
                : unmet == 0 ? "필수 요구사항 " + required + "개를 모두 확인했습니다"
                : "필수 요구사항 " + required + "개 중 " + unmet + "개의 충족을 확인해주세요";
        return new Check(REQUIRED_REQUIREMENTS, unmet == 0, detail);
    }

    // 판정과 까닭을 회차 쪽의 한 규칙에서 받는다. 회차 목록의 currentBasis와 어긋나지 않고, 까닭이 늘면 여기서 컴파일이 막힌다.
    // 통과하지 못한 까닭은 K-10 7장과 같은 말을 쓰고 다음에 할 일을 덧붙인다
    private Check fileCheck(ReviewRound latest) {
        Basis basis = reviews.basisOf(latest);
        String round = latest.getRoundNo() + "회차";
        String detail = switch (basis) {
            case CURRENT -> round + "에서 " + latest.getFile().getFileName() + " " + latest.getFile().getVersion() + "버전이 승인됐습니다";
            case UNDECIDED -> round + "가 결정 전입니다. 승인을 받아주세요";
            case CHANGES_REQUESTED -> round + "에서 수정 요청을 받았습니다. 보완한 뒤 새 회차를 열어주세요";
            case NO_FILE -> round + "는 파일 없는 회차입니다. 파일을 골라 새 회차를 열어주세요";
            case NEWER_CONTENT -> round + "에서 승인된 파일이 같은 이름의 최신 버전과 내용이 다릅니다. 최신 버전으로 새 회차를 열어주세요";
        };
        return new Check(APPROVED_FILE, basis == Basis.CURRENT, detail);
    }

    private Check commentCheck(Deliverable d) {
        long unresolved = unresolvedComments.countUnresolvedComments(d.getProject().getId(), d.getId());
        return new Check(NO_UNRESOLVED_COMMENTS, unresolved == 0,
                unresolved == 0 ? "미해결 피드백이 없습니다" : "미해결 피드백 " + unresolved + "건을 해결해야 확정할 수 있습니다");
    }

    // ── 찾기와 오류 ───────────────────────────────────────────────────────

    /** 같은 제출물의 확정, 제출 기록, 충족 확인, 회차 쓰기를 차례대로 처리하는 행 잠금(K-20 6장). */
    private Deliverable lock(Project project, UUID id) {
        return deliverables.lockByIdAndProject(id, project).orElseThrow(DeliverableConfirmationService::noDeliverable);
    }

    /** 이 제출물의 회차가 아닌 id는 다른 프로젝트의 것이든 없는 것이든 똑같이 404다. */
    private void requireRoundOf(Deliverable d, UUID reviewId) {
        if (rounds.findById(reviewId).filter(r -> r.getDeliverable().getId().equals(d.getId())).isEmpty())
            throw new NotFoundException("검토 회차를 찾을 수 없습니다");
    }

    private static NotFoundException noDeliverable() {
        return new NotFoundException("제출물을 찾을 수 없습니다");
    }

    private static ResponseStatusException conflict(String detail) {
        return new ResponseStatusException(HttpStatus.CONFLICT, detail);
    }
}
