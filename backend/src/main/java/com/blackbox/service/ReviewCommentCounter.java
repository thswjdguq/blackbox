package com.blackbox.service;

import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** 읽기 전용 구현을 명령 서비스와 분리해 회차 서비스와의 순환 주입을 막는다. */
@Service @RequiredArgsConstructor
public class ReviewCommentCounter implements UnresolvedCommentCounter {
    private final ReviewCommentRepository comments;
    private final DeliverableRepository deliverables;
    private final ProjectAccessChecker access;

    /** 호출자가 사용자 권한과 제출물 잠금을 확보한다. 모든 회차의 미해결 피드백을 센다. */
    @Override @Transactional(readOnly = true)
    public long countUnresolvedComments(UUID projectId, UUID deliverableId) {
        var project = access.getProject(projectId);
        deliverables.findByIdAndProject(deliverableId, project)
                .orElseThrow(() -> new NotFoundException("제출물을 찾을 수 없습니다"));
        return comments.countByProjectIdAndDeliverableIdAndResolvedAtIsNull(projectId, deliverableId);
    }
}
