package com.blackbox.service;

import com.blackbox.dto.ActionItemDto;

import java.util.List;

/**
 * LLM 공급자(Claude, OpenAI 등)가 구현하는 공통 인터페이스.
 * Task 26 — LLM 클라이언트 추상화.
 */
public interface LlmClient {

    boolean isConfigured();

    String summarizeMeeting(String title, String purpose, String notes, String decisions);

    List<ActionItemDto> extractStructuredActionItems(String notes, String decisions);

    List<String> extractActionItems(String notes, String decisions);

    String rawCall(String userPrompt, int maxTokens);
}
