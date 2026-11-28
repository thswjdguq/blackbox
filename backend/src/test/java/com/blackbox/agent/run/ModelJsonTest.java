package com.blackbox.agent.run;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 모델 답의 괄호 짝 맞추기(A-83). 짝을 맞춘 결과가 원래 뜻한 모양과 같은지, 따옴표 안의 글은 그대로인지 본다 */
class ModelJsonTest {
    final ObjectMapper json = new ObjectMapper();

    /** 2026-11-28 Gemini 초안 응답: 카드 객체를 닫는 "}" 없이 목록의 "]"를 먼저 썼다 */
    @Test void aCardObjectClosedByTheListBracketIsRepaired() {
        String answer = "{\"text\": \"초안입니다\", \"proposals\": [{\"kind\": \"DOCUMENT_DRAFT\", \"content\": {\"sections\": [{\"title\": \"결론\"}], \"title\": \"중간 보고서\"}]}";
        var root = ModelJson.read(json, answer);
        assertEquals("DOCUMENT_DRAFT", root.at("/proposals/0/kind").asString());
        assertEquals("중간 보고서", root.at("/proposals/0/content/title").asString());
        assertEquals("결론", root.at("/proposals/0/content/sections/0/title").asString());
    }

    @Test void bracketsInsideStringsAndEscapedQuotesAreLeftAlone() {
        assertEquals("{\"a\":\"[작성 필요: } ] {\\\"표\\\"]\"}", ModelJson.balance("{\"a\":\"[작성 필요: } ] {\\\"표\\\"]\""));
        // 따옴표 안의 괄호가 있어도, 카드를 닫는 "}"가 빠진 답을 고친다
        assertEquals("[작성 필요: } ]", ModelJson.read(json, "{\"p\":[{\"a\":\"[작성 필요: } ]\"]}").at("/p/0/a").asString());
    }

    @Test void missingClosersAtTheEndAndStrayClosersAreFixed() {
        assertEquals("{\"a\":[1,{\"b\":2}]}", ModelJson.balance("{\"a\":[1,{\"b\":2"));
        assertEquals("{\"a\":1}", ModelJson.balance("{\"a\":1}]"));
    }

    @Test void validJsonIsReadAsItIsAndTextOutsideTheObjectIsIgnored() {
        String valid = "{\"text\":\"a\",\"proposals\":[]}";
        assertEquals(valid, ModelJson.balance(valid));
        assertEquals("a", ModelJson.read(json, "```json\n" + valid + "\n```").path("text").asString());
    }

    @Test void whatCannotBeRepairedStillFails() {
        assertThrows(JacksonException.class, () -> ModelJson.read(json, "{\"text\": \"a\" \"b\"}"));
        assertThrows(IndexOutOfBoundsException.class, () -> ModelJson.read(json, "JSON이 아닌 답"));
    }
}
