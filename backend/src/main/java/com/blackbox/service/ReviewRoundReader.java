package com.blackbox.service;

import java.util.UUID;

/**
 * 피드백(K-11)·업무 전환(K-12)의 명령이 회차를 확인할 때 쓰는 읽기 인터페이스(K-10 5장).
 * 권한은 보지 않는다. 부르는 쪽이 사용자 권한을 먼저 확인한다.
 */
public interface ReviewRoundReader {
    /** 프로젝트·제출물·회차가 서로 맞는지 확인한다. 맞지 않거나 없으면 NotFoundException */
    ReviewRoundView get(UUID projectId, UUID deliverableId, UUID reviewId);

    /** 같은 확인을 하되 제출물 행을 먼저 잠근다. 쓰기 명령의 트랜잭션 안에서 부른다 */
    ReviewRoundView lockForWrite(UUID projectId, UUID deliverableId, UUID reviewId);

    /** decision은 null·APPROVED·CHANGES_REQUESTED, deliverableStatus는 K-10 2장의 계산 값 */
    record ReviewRoundView(UUID reviewId, UUID deliverableId, UUID projectId, int roundNo,
            boolean latest, String decision, String deliverableStatus) {}
}
