package com.blackbox.agent.tool;

import com.blackbox.entity.User;
import com.blackbox.service.ScoreService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GetScoresTool implements ProjectTool {
    public static final String NAME = "get_scores";

    private final ScoreService scores;

    @Override public String name() { return NAME; }
    @Override public String label() { return "기여도 조회"; }
    @Override public String description() {
        return "멤버별 기여도를 돌려준다. 업무·회의·파일·액션아이템에 참여했는지와 참여 수준, 계산한 시각이 들어 있다.";
    }

    @Override public Object read(UUID projectId, User user, JsonNode arguments) {
        return scores.getScores(projectId, user).stream()
                .map(s -> new Item(s.name(), s.taskParticipated(), s.meetingParticipated(), s.fileParticipated(),
                        s.actionParticipated(), s.participationLevel(), s.calculatedAt())).toList();
    }

    record Item(String name, boolean taskParticipated, boolean meetingParticipated, boolean fileParticipated,
                boolean actionParticipated, String participationLevel, OffsetDateTime calculatedAt) {}
}
