package com.blackbox.agent.model;

import com.blackbox.agent.AgentProperties;
import com.blackbox.agent.tool.AgentTool;
import com.blackbox.service.OpenAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenAiAgentModelTest {
    final OpenAiService client=mock(OpenAiService.class);
    final AgentProperties properties=new AgentProperties();
    final OpenAiAgentModel model=new OpenAiAgentModel(client,properties,new ObjectMapper());
    final AgentTool tool=mock(AgentTool.class);
    @Test void noKeyCannotExecute() {
        assertFalse(model.available());
        assertEquals(AgentModelException.Reason.UNAVAILABLE,assertThrows(AgentModelException.class,()->model.generate("규칙","질문",List.of())).reason());
        verify(client,never()).agentChat(any(),any(),any());
    }
    @Test void configuredClientReturnsText() {
        when(client.isConfigured()).thenReturn(true);doReturn(text("업무를 확인하세요")).when(client).agentChat(any(),any(),any());
        assertEquals("업무를 확인하세요",model.generate("규칙","질문",List.of()));
    }
    @Test void readsOnlyRegisteredToolAndPassesResultBack() {
        when(client.isConfigured()).thenReturn(true);when(tool.name()).thenReturn("list_tasks");when(tool.description()).thenReturn("업무 조회");
        when(tool.inputSchema()).thenReturn("{\"type\":\"object\",\"properties\":{}}");when(tool.call("{}")).thenReturn("[]");
        List<List<Map<String,Object>>> sent=new ArrayList<>();
        when(client.agentChat(any(),any(),any())).thenAnswer(invocation -> {
            sent.add(List.copyOf(invocation.<List<Map<String,Object>>>getArgument(0)));
            return sent.size()==1?call("list_tasks"):text("업무 없음");
        });
        assertEquals("업무 없음",model.generate("규칙","질문",List.of(tool)));verify(tool).call("{}");
        assertEquals(2,sent.get(0).size());assertEquals(4,sent.get(1).size());
        assertEquals("tool",sent.get(1).get(3).get("role"));assertEquals("call-1",sent.get(1).get(3).get("tool_call_id"));
    }
    @Test void unknownToolCannotRun() {
        when(client.isConfigured()).thenReturn(true);doReturn(call("delete_project")).when(client).agentChat(any(),any(),any());
        assertThrows(AgentModelException.class,()->model.generate("규칙","질문",List.of()));verify(tool,never()).call(any());
    }
    @Test void repeatedToolCallsAreBounded() {
        properties.setMaxToolCalls(1);when(client.isConfigured()).thenReturn(true);
        when(tool.name()).thenReturn("list_tasks");when(tool.description()).thenReturn("조회");when(tool.inputSchema()).thenReturn("{}");when(tool.call("{}")).thenReturn("[]");
        doReturn(call("list_tasks")).when(client).agentChat(any(),any(),any());
        assertThrows(AgentModelException.class,()->model.generate("규칙","질문",List.of(tool)));verify(tool,times(1)).call("{}");
    }
    @Test void providerErrorsNeverBecomeUserMessage() {
        when(client.isConfigured()).thenReturn(true);when(client.agentChat(any(),any(),any())).thenThrow(new IllegalStateException("private provider response"));
        var error=assertThrows(AgentModelException.class,()->model.generate("규칙","질문",List.of()));
        assertFalse(error.getMessage().contains("private"));
    }
    private Map<String,Object> text(String content) {return Map.of("choices",List.of(Map.of("message",Map.of("role","assistant","content",content))));}
    private Map<String,Object> call(String name) {return Map.of("choices",List.of(Map.of("message",Map.of("tool_calls",List.of(Map.of("id","call-1","type","function","function",Map.of("name",name,"arguments","{}")))))));}
}
