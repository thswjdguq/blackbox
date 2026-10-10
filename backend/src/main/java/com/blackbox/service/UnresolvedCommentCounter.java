package com.blackbox.service;

import java.util.UUID;

/** 승인과 확정 검사가 쓰는 미해결 피드백 수(K-10 5장, K-11 4장). 구현은 피드백 쪽(B-20)이 등록한다. */
public interface UnresolvedCommentCounter {
    long countUnresolvedComments(UUID projectId, UUID deliverableId);
}
