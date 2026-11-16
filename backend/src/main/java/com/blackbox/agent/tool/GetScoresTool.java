package com.blackbox.agent.tool;

import com.blackbox.entity.ContributionScoreItem;
import com.blackbox.entity.User;
import com.blackbox.service.ScoreService;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GetScoresTool implements ProjectTool {
    public static final String NAME = "get_scores";

    private final ScoreService scores;

    @Override public String name() { return NAME; }
    @Override public String label() { return "기여도 조회"; }
    @Override public String description() {
        return "멤버별 기여도를 돌려준다. 업무·회의·파일·액션아이템에 참여했는지와 참여 수준, 계산한 시각, 그 계산의 항목(items)과 기준(criteriaVersion)이 들어 있다. "
                + "items의 count는 횟수이고 total이 있으면 전체 수다(회의). countsTowardLevel이 false인 항목은 참여 수준 판정에 넣지 않는 기록이므로 판정의 근거로 말하지 않는다. "
                + "items가 null이면 지금 기준으로 다시 계산하기 전의 기록이다.";
    }

    @Override public Object read(UUID projectId, User user, JsonNode arguments) {
        return scores.getScores(projectId, user).stream()
                .map(s -> new Item(s.name(), s.taskParticipated(), s.meetingParticipated(), s.fileParticipated(),
                        s.actionParticipated(), s.participationLevel(), s.calculatedAt(), s.items(), s.criteriaVersion())).toList();
    }

    record Item(String name, boolean taskParticipated, boolean meetingParticipated, boolean fileParticipated,
                boolean actionParticipated, String participationLevel, OffsetDateTime calculatedAt,
                List<ContributionScoreItem> items, String criteriaVersion) {}
}
