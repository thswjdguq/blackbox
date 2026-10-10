package com.blackbox.agent;

import com.blackbox.dto.DeliverableDtos;
import com.blackbox.dto.DeliverableDtos.RequirementResponse;
import com.blackbox.dto.TaskResponse;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 단계 판정이 프로젝트 홈의 "다음 할 일" 카드와 같은 결과를 내는지 본다(K-30 12장).
 * 기준표(agent/stage-cases.txt)는 화면의 판정 함수(frontend NextStepCard.nextStepFor)를 그대로 돌려 만든 것이다.
 * 줄 형식은 요구사항|업무|단계. 요구사항은 R(필수)/O(선택) + C(확인됨)/U(확인 전),
 * 업무는 -(다른 제출물)/n(이 제출물, 요구사항 없음)/0·1(그 순서의 요구사항) + D(끝남)/T(안 끝남).
 */
class DeliverableStageTest {
    static final UUID DELIVERABLE = UUID.randomUUID();

    @Test void judgesEveryCaseTheSameAsTheNextStepCard() throws Exception {
        Set<DeliverableStage> seen = EnumSet.noneOf(DeliverableStage.class);
        List<String> different = new ArrayList<>();
        int count = 0;
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/agent/stage-cases.txt"), StandardCharsets.UTF_8))) {
            for (String line; (line = lines.readLine()) != null; count++) {
                String[] part = line.split("\\|", -1);
                List<RequirementResponse> requirements = codes(part[0]).stream().map(c -> new RequirementResponse(
                        UUID.randomUUID(), "요구사항", c.charAt(0) == 'R',
                        c.charAt(1) == 'C' ? new DeliverableDtos.Assessment(null, null) : null)).toList();
                List<TaskResponse> tasks = codes(part[1]).stream().map(c -> task(
                        c.charAt(0) == '-' ? UUID.randomUUID() : DELIVERABLE,
                        Character.isDigit(c.charAt(0)) ? requirements.get(c.charAt(0) - '0').id() : null,
                        c.charAt(1) == 'D' ? "DONE" : "TODO")).toList();
                DeliverableStage stage = DeliverableStage.of(deliverable(requirements), tasks);
                seen.add(stage);
                if (stage != DeliverableStage.valueOf(part[2])) different.add(line + " → " + stage);
            }
        }
        assertTrue(count > 1000, "기준표를 읽지 못했다: " + count);
        assertEquals(List.of(), different);
        assertEquals(EnumSet.allOf(DeliverableStage.class), seen, "여섯 단계가 모두 나온다");
    }

    private static List<String> codes(String part) {
        return part.isEmpty() ? List.of() : Arrays.asList(part.split(","));
    }

    private static DeliverableDtos.Response deliverable(List<RequirementResponse> requirements) {
        return new DeliverableDtos.Response(DELIVERABLE, UUID.randomUUID(), "제출물", null, LocalDate.of(2026, 10, 18),
                null, null, null, requirements);
    }

    private static TaskResponse task(UUID deliverableId, UUID requirementId, String status) {
        return new TaskResponse(UUID.randomUUID(), UUID.randomUUID(), "업무", null, status, "MEDIUM", null, null, null,
                UUID.randomUUID(), null, null, List.of(), deliverableId, null, requirementId, null, null);
    }
}
