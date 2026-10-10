package com.blackbox.service;

import com.blackbox.dto.ConfirmationDtos.*;
import com.blackbox.entity.*;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 최종 확정과 제출 기록(K-20). 준비 조회와 확정 명령이 같은 검사를 쓴다. */
@Service @RequiredArgsConstructor @Transactional
public class DeliverableConfirmationService {
    // K-20 3장의 checks[].code. 응답에는 이 순서로 들어간다
    static final String REQUIRED_REQUIREMENTS = "REQUIRED_REQUIREMENTS", APPROVED_FILE = "APPROVED_FILE",
            NO_UNRESOLVED_COMMENTS = "NO_UNRESOLVED_COMMENTS";

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

    @Transactional(readOnly = true)
    public Response get(UUID projectId, UUID deliverableId, User user) {
        Project project = access.getProject(projectId);
        access.requireMember(project, user);
        return response(deliverables.findByIdAndProject(deliverableId, project)
                .orElseThrow(() -> new NotFoundException("제출물을 찾을 수 없습니다")));
    }

    private Response response(Deliverable d) {
        Optional<ReviewRound> latest = rounds.findFirstByDeliverableOrderByRoundNoDesc(d);
        String status = statuses.of(d, latest).status();
        boolean sole = soleContributor(d.getProject());
        Optional<DeliverableConfirmation> confirmation = confirmations.findById(d.getId());
        if (confirmation.isEmpty()) {
            Readiness r = readiness(d, latest);
            return new Response(status, r.ready(), r.checks(), r.candidate() == null ? null : Candidate.of(r.candidate()), sole, null, null);
        }
        DeliverableConfirmation c = confirmation.get();
        // 확정 뒤에 내용이 다른 새 버전이 올라왔는지만 알린다. 확정본은 회차가 가리키는 버전 그대로다
        return new Response(status, false, List.of(), null, sole, Confirmation.from(c, reviews.approvedFileIsNewest(c.getReview())),
                submissions.findById(d.getId()).map(Submission::from).orElse(null));
    }

    /** 세 검사와, 승인된 파일 검사를 통과했을 때의 확정 대상. */
    private record Readiness(List<Check> checks, ReviewRound candidate) {
        boolean ready() { return checks.stream().allMatch(Check::passed); }
    }

    private Readiness readiness(Deliverable d, Optional<ReviewRound> latest) {
        // 통과 여부는 회차 목록의 currentBasis와 같은 규칙으로 정한다(K-10 3장)
        ReviewRound candidate = latest.filter(reviews::approvedFileIsNewest).orElse(null);
        return new Readiness(List.of(requirementCheck(d),
                new Check(APPROVED_FILE, candidate != null, fileDetail(latest, candidate != null)),
                commentCheck(d)), candidate);
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

    // 통과하지 못한 사유는 K-10 7장과 같은 말을 쓰고, 다음에 할 일을 덧붙인다
    private static String fileDetail(Optional<ReviewRound> latest, boolean passed) {
        if (latest.isEmpty()) return "검토 회차가 없습니다. 파일을 골라 검토를 요청해주세요";
        ReviewRound r = latest.get();
        String round = r.getRoundNo() + "회차";
        if (passed) return round + "에서 " + r.getFile().getFileName() + " " + r.getFile().getVersion() + "버전이 승인됐습니다";
        if (r.getDecision() == null) return round + "가 결정 전입니다. 승인을 받아주세요";
        if (!"APPROVED".equals(r.getDecision())) return round + "에서 수정 요청을 받았습니다. 보완한 뒤 새 회차를 열어주세요";
        if (r.getFile() == null) return round + "는 파일 없는 회차입니다. 파일을 골라 새 회차를 열어주세요";
        return round + "에서 승인된 파일이 같은 이름의 최신 버전과 내용이 다릅니다. 최신 버전으로 새 회차를 열어주세요";
    }

    private Check commentCheck(Deliverable d) {
        long unresolved = unresolvedComments.countUnresolvedComments(d.getProject().getId(), d.getId());
        return new Check(NO_UNRESOLVED_COMMENTS, unresolved == 0,
                unresolved == 0 ? "미해결 피드백이 없습니다" : "미해결 피드백 " + unresolved + "건을 해결해야 확정할 수 있습니다");
    }

    /** 팀장·팀원이 한 명뿐이면 올린 사람이 자기 파일을 승인할 수 없어 확정 조건을 채울 수 없다(K-20 5장). */
    private boolean soleContributor(Project project) {
        return members.countByProjectAndRole(project, "LEADER") + members.countByProjectAndRole(project, "MEMBER") <= 1;
    }
}
