package com.blackbox.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 액션 아이템을 뽑을 때 모델에 보내는 글을 본다. 모델은 부르지 않는다. */
class AbstractLlmClientTest {
    final List<String> prompts = new ArrayList<>();
    final AbstractLlmClient client = new AbstractLlmClient() {
        @Override public boolean isConfigured() { return true; }
        @Override protected String callModel(String userPrompt, int maxTokens) { return ""; }
        @Override protected String callModelWithSystem(String systemPrompt, String userPrompt, int maxTokens) { prompts.add(userPrompt); return "[]"; }
    };

    @Test void theMeetingDateIsGivenAsTheReferenceForRelativeDueDates() {
        // 한국 시간으로는 10월 14일 수요일 아침이다. UTC 날짜(13일)로 적으면 하루가 어긋난다
        client.extractStructuredActionItems("구조도 초안은 다음 주 수요일까지", "", OffsetDateTime.parse("2026-10-13T23:30:00Z"));

        assertTrue(prompts.get(0).contains("회의 날짜는 2026-10-14(수요일)이다"), prompts.get(0));
        assertTrue(prompts.get(0).contains("구조도 초안은 다음 주 수요일까지"));
    }

    @Test void withoutAMeetingDateThePromptIsAsBefore() {
        client.extractStructuredActionItems("메모", "결정", null);

        assertFalse(prompts.get(0).contains("회의 날짜"), prompts.get(0));
    }
}
