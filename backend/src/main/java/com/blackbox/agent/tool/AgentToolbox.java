package com.blackbox.agent.tool;

import com.blackbox.agent.AgentProperties;
import com.blackbox.entity.User;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 등록된 도구를 이름으로 찾아, 한 번의 실행(프로젝트와 요청한 사람)에 묶어 모델이 부를 수 있는 모양으로 내준다. */
@Component
public class AgentToolbox {
    private final Map<String, ProjectTool> tools;
    private final ObjectMapper json;
    private final AgentProperties properties;

    public AgentToolbox(List<ProjectTool> tools, ObjectMapper json, AgentProperties properties) {
        // 이름이 겹치면 서버가 뜰 때 실패한다
        this.tools = tools.stream().collect(Collectors.toMap(ProjectTool::name, Function.identity()));
        this.json = json;
        this.properties = properties;
    }

    public List<AgentTool> bind(UUID projectId, User user, Collection<String> names) {
        return names.stream().map(name -> bound(find(name), projectId, user)).toList();
    }

    private ProjectTool find(String name) {
        ProjectTool tool = tools.get(name);
        if (tool == null) throw new IllegalArgumentException("등록되지 않은 도구입니다: " + name);
        return tool;
    }

    private AgentTool bound(ProjectTool tool, UUID projectId, User user) {
        return new AgentTool() {
            @Override public String name() { return tool.name(); }
            @Override public String description() { return tool.description(); }
            @Override public String inputSchema() { return tool.inputSchema(); }

            @Override public String call(String argumentsJson) {
                JsonNode arguments;
                try {
                    arguments = argumentsJson == null || argumentsJson.isBlank()
                            ? json.createObjectNode() : json.readTree(argumentsJson);
                } catch (JsonProcessingException e) {
                    return error("인자를 읽을 수 없습니다");
                }
                try {
                    Object result = tool.read(projectId, user, arguments);
                    if (result instanceof List<?> list) result = Listing.of(list, properties.getToolListLimit());
                    return json.writeValueAsString(result);
                } catch (NotFoundException | ForbiddenException | ProjectTool.BadArguments e) {
                    // 모델이 잘못 부른 것은 실행을 끝내지 않고 이유를 돌려줘 모델이 고쳐 부르게 한다
                    return error(e.getMessage());
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("도구의 결과를 JSON으로 바꾸지 못했습니다: " + tool.name(), e);
                }
            }
        };
    }

    private String error(String message) {
        return json.createObjectNode().put("error", message).toString();
    }
}
