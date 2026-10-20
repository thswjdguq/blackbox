package com.blackbox.service;

import com.blackbox.dto.ActionItemDto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * LLM 공급자(Claude, OpenAI 등)가 구현하는 공통 인터페이스.
 * Task 26 — LLM 클라이언트 추상화.
 */
public interface LlmClient {

    boolean isConfigured();

    String summarizeMeeting(String title, String purpose, String notes, String decisions);

    /** meetingDate는 "다음 주 수요일"처럼 돌려 말한 기한을 날짜로 바꾸는 기준이다 */
    List<ActionItemDto> extractStructuredActionItems(String notes, String decisions, OffsetDateTime meetingDate);

    String rawCall(String userPrompt, int maxTokens);
}
