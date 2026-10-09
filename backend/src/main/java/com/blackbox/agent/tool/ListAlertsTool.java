package com.blackbox.agent.tool;

import com.blackbox.entity.User;
import com.blackbox.service.AlertService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ListAlertsTool implements ProjectTool {
    public static final String NAME = "list_alerts";

    private final AlertService alerts;

    @Override public String name() { return NAME; }
    @Override public String label() { return "경보 조회"; }
    @Override public String description() { return "아직 풀리지 않은 경보를 최근 순으로 돌려준다. 종류, 심각도, 내용이 들어 있다."; }

    @Override public Object read(UUID projectId, User user, JsonNode arguments) {
        return alerts.getAlerts(projectId, user).stream()
                .map(a -> new Item(a.alertType(), a.severity(), a.message(), a.createdAt())).toList();
    }

    record Item(String type, String severity, String message, OffsetDateTime createdAt) {}
}
