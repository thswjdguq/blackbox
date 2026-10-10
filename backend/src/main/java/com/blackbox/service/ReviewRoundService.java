package com.blackbox.service;

import com.blackbox.dto.ReviewDtos.*;
import com.blackbox.entity.*;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** 검토 회차(K-10). 확정 근거는 저장하지 않고 회차 목록에서 계산한다. 제출물 상태는 DeliverableStatuses가 정한다. */
@Service @RequiredArgsConstructor @Transactional
public class ReviewRoundService implements ReviewRoundReader {
    private final ReviewRoundRepository rounds;
    private final DeliverableRepository deliverables;
    private final FileVaultRepository files;
    private final ProjectAccessChecker access;
    // 형식으로만 주입받는다. 구현이 없거나 둘이면 서버가 뜨지 않아야 한다(K-10 5장)
    private final UnresolvedCommentCounter unresolvedComments;
    private final DeliverableStatuses statuses;

    @Transactional(readOnly = true)
    public ListResponse list(UUID projectId, UUID deliverableId, User user) {
        Project project = access.getProject(projectId);
        access.requireMember(project, user);
        Deliverable d = find(project, deliverableId);
        List<ReviewRound> all = rounds.findByDeliverableOrderByRoundNoDesc(d);
        return new ListResponse(status(d, all), unresolvedComments.countUnresolvedComments(projectId, deliverableId),
                all.stream().map(r -> round(r, r == all.get(0))).toList());
    }

    public Round open(UUID projectId, UUID deliverableId, OpenRequest req, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        Deliverable d = lock(project, deliverableId);
        List<ReviewRound> all = rounds.findByDeliverableOrderByRoundNoDesc(d);
        ReviewRound r = new ReviewRound();
        r.setProject(project);
        r.setDeliverable(d);
        r.setRoundNo(all.isEmpty() ? 1 : all.get(0).getRoundNo() + 1);
        if (req.fileId() != null) r.setFile(files.findByIdAndProject(req.fileId(), project)
                .orElseThrow(() -> new NotFoundException("파일을 찾을 수 없습니다")));
        r.setOpenedBy(user);
        return round(rounds.save(r), true);
    }

    public Round decide(UUID projectId, UUID deliverableId, UUID reviewId, String decision, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        List<ReviewRound> all = rounds.findByDeliverableOrderByRoundNoDesc(lock(project, deliverableId));
        ReviewRound r = pick(all, reviewId);
        if (r != all.get(0)) throw conflict("최신 회차에만 결정할 수 있습니다");
        if (r.getFile() != null && r.getFile().getUploader().getId().equals(user.getId()))
            throw new ForbiddenException("직접 올린 파일은 결정할 수 없습니다");
        if (r.getDecision() != null) {
            // 같은 결정을 다시 보낸 것은 중복 클릭으로 보고 지금 상태를 돌려준다
            if (r.getDecision().equals(decision)) return round(r, true);
            throw conflict("이미 결정된 회차입니다. 결정을 바꾸려면 새 회차를 여세요");
        }
        if ("APPROVED".equals(decision)) {
            long unresolved = unresolvedComments.countUnresolvedComments(projectId, deliverableId);
            if (unresolved > 0) throw conflict("미해결 피드백 " + unresolved + "건을 먼저 해결해주세요");
        }
        r.decide(decision, user);
        return round(r, true);
    }

    @Override @Transactional(readOnly = true)
    public ReviewRoundView get(UUID projectId, UUID deliverableId, UUID reviewId) {
        return view(find(access.getProject(projectId), deliverableId), reviewId);
    }

    @Override
    public ReviewRoundView lockForWrite(UUID projectId, UUID deliverableId, UUID reviewId) {
        return view(lock(access.getProject(projectId), deliverableId), reviewId);
    }

    private ReviewRoundView view(Deliverable d, UUID reviewId) {
        List<ReviewRound> all = rounds.findByDeliverableOrderByRoundNoDesc(d);
        ReviewRound r = pick(all, reviewId);
        return new ReviewRoundView(r.getId(), d.getId(), d.getProject().getId(), r.getRoundNo(),
                r == all.get(0), r.getDecision(), status(d, all));
    }

    // 회차는 저장하지 않은 값 두 가지를 함께 내보낸다: 최신 회차인가, 이 승인이 지금 확정 근거인가
    private Round round(ReviewRound r, boolean latest) {
        return Round.from(r, latest, latest && approvedFileIsNewest(r));
    }

    /**
     * 승인됐고 파일이 있으며, 그 파일 이름의 최신 버전이 이 회차의 파일과 내용이 같은가.
     * 최신 회차가 이것을 채우면 확정 근거다(K-10 3장). 확정(K-20)도 같은 규칙을 쓴다.
     */
    public boolean approvedFileIsNewest(ReviewRound r) {
        if (!"APPROVED".equals(r.getDecision()) || r.getFile() == null) return false;
        // 같은 내용을 다시 올려도 버전은 올라가므로 버전 번호가 아니라 해시로 비교한다
        return files.findTopByProjectAndFileNameOrderByVersionDesc(r.getProject(), r.getFile().getFileName())
                .map(newest -> newest.getFileHash().equals(r.getFile().getFileHash())).orElse(false);
    }

    private String status(Deliverable d, List<ReviewRound> all) {
        return statuses.of(d, all.stream().findFirst()).status();
    }

    private static ReviewRound pick(List<ReviewRound> all, UUID reviewId) {
        return all.stream().filter(r -> r.getId().equals(reviewId)).findFirst()
                .orElseThrow(() -> new NotFoundException("검토 회차를 찾을 수 없습니다"));
    }

    private Deliverable find(Project project, UUID id) {
        return deliverables.findByIdAndProject(id, project).orElseThrow(() -> new NotFoundException("제출물을 찾을 수 없습니다"));
    }

    private Deliverable lock(Project project, UUID id) {
        return deliverables.lockByIdAndProject(id, project).orElseThrow(() -> new NotFoundException("제출물을 찾을 수 없습니다"));
    }

    private static ResponseStatusException conflict(String detail) {
        return new ResponseStatusException(HttpStatus.CONFLICT, detail);
    }
}
