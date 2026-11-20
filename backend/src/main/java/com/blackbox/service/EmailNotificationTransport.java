package com.blackbox.service;

import java.util.UUID;

/** A의 SMTP 어댑터 연결점. 커밋된 대기 건만 worker가 호출하고 주소·본문·원본 SMTP 예외를 로그로 남기지 않는다. */
public interface EmailNotificationTransport {
    enum Result { ACCEPTED, TEMPORARY_FAILURE, PERMANENT_FAILURE, AUTHENTICATION_FAILURE, UNKNOWN_OUTCOME }

    /** deliveryId는 재시도 시 같은 Message-ID를 만드는 데 사용한다. ACCEPTED는 받은편지함 도착 보장이 아니다. */
    Result send(UUID deliveryId, String recipient, EmailNotificationComposer.Content content);
}
