package com.blackbox.agent.model;

/** 모델 호출이 실패한 이유를 우리 말로 바꿔 올린다. 부르는 쪽은 공급자의 예외 종류를 몰라도 된다. */
public class AgentModelException extends RuntimeException {
    public enum Reason {
        /** 공급자가 설정되지 않았다 */
        UNAVAILABLE,
        /** 호출 실패, 시간 초과, 응답 없음 */
        FAILED
    }

    private final Reason reason;

    public AgentModelException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
