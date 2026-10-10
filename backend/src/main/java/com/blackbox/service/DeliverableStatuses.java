package com.blackbox.service;

import com.blackbox.entity.Deliverable;
import com.blackbox.entity.Project;
import com.blackbox.entity.ReviewRound;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.DeliverableConfirmationRepository;
import com.blackbox.repository.DeliverableRepository;
import com.blackbox.repository.DeliverableSubmissionRepository;
import com.blackbox.repository.ReviewRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 제출물 상태(K-20 2장). 확정과 제출 기록은 저장된 사실이고, 그 앞의 상태는 최신 회차에서 계산한다.
 * 상태를 정하는 곳과, 확정 뒤의 변경을 막는 곳은 여기 하나다. 트랜잭션은 부르는 쪽의 것을 쓴다.
 */
@Component @RequiredArgsConstructor
public class DeliverableStatuses {
    public static final String DRAFT = "DRAFT", IN_REVIEW = "IN_REVIEW", CONFIRMED = "CONFIRMED", SUBMITTED = "SUBMITTED";

    private final DeliverableRepository deliverables;
    private final ReviewRoundRepository rounds;
    private final DeliverableConfirmationRepository confirmations;
    private final DeliverableSubmissionRepository submissions;

    /** latestDecision은 최신 회차의 결정이다. 회차가 없거나 결정 전이면 null */
    public record Status(String status, String latestDecision) {}

    public Status of(Deliverable deliverable) {
        return of(deliverable, rounds.findFirstByDeliverableOrderByRoundNoDesc(deliverable));
    }

    /** 최신 회차를 이미 읽은 쪽이 쓴다. */
    public Status of(Deliverable deliverable, Optional<ReviewRound> latest) {
        boolean submitted = submissions.existsById(deliverable.getId());
        return status(submitted, submitted || confirmations.existsById(deliverable.getId()), latest);
    }

    /** 프로젝트의 제출물들. 제출물이 몇 개든 조회는 세 번이다. */
    public Map<UUID, Status> of(Project project, Collection<Deliverable> deliverables) {
        Set<UUID> submitted = submissions.findDeliverableIdsByProject(project);
        Set<UUID> confirmed = confirmations.findDeliverableIdsByProject(project);
        Map<UUID, ReviewRound> latest = rounds.findLatestByProject(project).stream()
                .collect(Collectors.toMap(r -> r.getDeliverable().getId(), r -> r));
        return deliverables.stream().collect(Collectors.toMap(Deliverable::getId, d -> status(
                submitted.contains(d.getId()), confirmed.contains(d.getId()), Optional.ofNullable(latest.get(d.getId())))));
    }

    /**
     * 제출물과 그 요구사항·회차를 바꾸기 전에 부른다. 제출물 행을 잠가 같은 제출물의 쓰기와 확정을 차례대로 처리하고(K-20 6장),
     * 확정된 제출물이면 409다(K-20 4장). 잠금과 확인을 한 번에 하므로 확정과 거의 동시에 들어온 쓰기가 확정 뒤에 반영되지 않는다.
     */
    public Deliverable lockEditable(Project project, UUID id) {
        Deliverable deliverable = deliverables.lockByIdAndProject(id, project)
                .orElseThrow(() -> new NotFoundException("제출물을 찾을 수 없습니다"));
        if (confirmations.existsById(id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "확정된 제출물은 변경할 수 없습니다");
        return deliverable;
    }

    // K-20 2장의 표. 위에서부터 먼저 맞는 것이 상태다
    private static Status status(boolean submitted, boolean confirmed, Optional<ReviewRound> latest) {
        String decision = latest.map(ReviewRound::getDecision).orElse(null);
        if (submitted) return new Status(SUBMITTED, decision);
        if (confirmed) return new Status(CONFIRMED, decision);
        if (latest.isEmpty() || "CHANGES_REQUESTED".equals(decision)) return new Status(DRAFT, decision);
        return new Status(IN_REVIEW, decision);
    }
}
