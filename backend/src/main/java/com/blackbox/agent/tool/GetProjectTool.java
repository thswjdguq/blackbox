package com.blackbox.agent.tool;

import com.blackbox.agent.AgentProperties;
import com.blackbox.dto.ProjectResponse;
import com.blackbox.entity.User;
import com.blackbox.service.ProjectService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GetProjectTool implements ProjectTool {
    public static final String NAME = "get_project";

    private final ProjectService projects;
    private final AgentProperties properties;

    @Override public String name() { return NAME; }
    @Override public String label() { return "프로젝트와 멤버 조회"; }
    @Override public String description() { return "프로젝트의 이름과 기간, 멤버의 이름과 역할, 오늘 날짜를 돌려준다."; }

    @Override public Object read(UUID projectId, User user, JsonNode arguments) {
        ProjectResponse project = projects.getProject(projectId, user);
        List<Member> members = projects.listMembers(projectId, user).stream()
                .map(m -> new Member(m.name(), m.role())).toList();
        return new Result(project.name(), project.startDate(), project.endDate(),
                properties.today(), members);
    }

    record Result(String name, LocalDate startDate, LocalDate endDate, LocalDate today, List<Member> members) {}
    record Member(String name, String role) {}
}
