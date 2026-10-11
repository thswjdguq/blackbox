package com.blackbox.service;

import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * 피드백 기능(B-20)이 들어오기 전까지 쓰는 기본 구현. 코멘트가 아직 없으므로 항상 0이다.
 * B-20 PR이 실제 구현을 등록하면서 이 파일을 지운다(K-10 5장).
 * 조건부 등록으로 바꾸지 않는다. 구현이 정확히 하나일 때만 서버가 떠야, 실제 조회가 빠진 채 0으로 승인되는 일이 없다.
 */
@Component
class NoCommentsYet implements UnresolvedCommentCounter {
    @Override public long countUnresolvedComments(UUID projectId, UUID deliverableId) { return 0; }
}
