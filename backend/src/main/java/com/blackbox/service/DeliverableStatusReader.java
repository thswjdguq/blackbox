package com.blackbox.service;

import java.util.UUID;

/**
 * 업무 명령이 "이 업무가 연결된 제출물이 확정됐는가"를 확인할 때 쓰는 읽기 인터페이스(K-20 6장).
 * 권한은 보지 않는다. 부르는 쪽이 사용자 권한을 먼저 확인한다.
 * 피드백 명령은 ReviewRoundReader가 돌려주는 deliverableStatus에 같은 판단(isConfirmed)을 쓰면 된다.
 */
public interface DeliverableStatusReader {
    // K-20 2장의 제출물 상태
    String DRAFT = "DRAFT", IN_REVIEW = "IN_REVIEW", CONFIRMED = "CONFIRMED", SUBMITTED = "SUBMITTED";
    /** 확정된 제출물에 대한 쓰기를 막을 때 쓰는 409의 detail(K-20 4장). */
    String CONFIRMED_DETAIL = "확정된 제출물은 변경할 수 없습니다";

    /** 프로젝트와 제출물이 맞지 않거나 없으면 NotFoundException */
    String statusOf(UUID projectId, UUID deliverableId);

    /**
     * 같은 확인을 하되 제출물 행을 먼저 잠근다. 확정과 차례대로 처리되어, 확정 직전에 읽은 상태로 쓰는 일이 없다.
     * 쓰기 명령의 트랜잭션 안에서 부른다. 트랜잭션 밖에서 부르면 잠금이 바로 풀리므로 예외를 던진다.
     */
    String lockForWrite(UUID projectId, UUID deliverableId);

    /** 확정됐거나, 확정된 뒤 제출까지 기록된 상태인가. 이 상태의 제출물과 그에 딸린 것은 바꿀 수 없다(K-20 4장). */
    static boolean isConfirmed(String status) {
        return CONFIRMED.equals(status) || SUBMITTED.equals(status);
    }
}
