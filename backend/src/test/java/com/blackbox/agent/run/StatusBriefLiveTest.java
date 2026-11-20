package com.blackbox.agent.run;

import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.AgentTestData;
import com.blackbox.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 업무가 많은 프로젝트에서 상태 요약이 끝까지, 짧게 답하는지 실제 모델로 본다(STATUS_BRIEF.md의 길이 기준).
 * 운영에서 업무가 많은 프로젝트의 상태 요약이 출력 상한에 걸려 빈 답으로 끝났다(A-70). 지시문을 고치다 길이 기준이 빠지면 여기서 걸린다.
 *
 * 공급자를 환경 변수로 골랐을 때만 돈다. 테스트용 프로젝트만 모델로 나간다. 결과는 테스트 출력(SBRIEF로 시작하는 줄)에 남는다.
 * 모델의 답은 매번 달라지므로 한 번 실패했다고 코드가 틀린 것은 아니다. 어느 항목이 왜 틀렸는지를 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_AI_MODEL_CHAT", matches = "google-genai|bedrock-converse|openai")
class StatusBriefLiveTest {
    static final int TASKS = 80, LATE = 35, LISTED_LATE_MAX = 5, LINES_MAX = 15, CARD_TASKS_MAX = 5;

    @Autowired AgentRunner runner;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    AgentTestData data;
    final List<String> failures = new ArrayList<>();

    @AfterEach void cleanUp() { if (data != null) data.cleanup(); }

    @Test void briefStaysShortOnABusyProject() throws Exception {
        data = new AgentTestData(em, transactions, jdbc);
        LocalDate today = LocalDate.now();
        User leader = data.user("김팀장"), lee = data.user("이팀원"), park = data.user("박팀원");
        Project project = data.project("캡스톤 디자인", leader);
        data.join(project, lee, "MEMBER"); data.join(project, park, "MEMBER");
        Deliverable report = data.deliverable(project, "중간 보고서", today.plusDays(4));
        Deliverable demo = data.deliverable(project, "시연 영상", today.plusDays(12));
        // 요구사항은 두 제출물에 번갈아 붙는다: 0·2·4번은 시연 영상, 1·3·5번은 중간 보고서
        List<DeliverableRequirement> requirements = new ArrayList<>();
        for (int i = 0; i < 6; i++) requirements.add(data.requirement(i % 2 == 1 ? report : demo, "요구사항 " + (i + 1) + "을 충족한다", true));
        User[] people = {leader, lee, park};
        for (int i = 1; i <= TASKS; i++) {
            LocalDate due = i <= LATE ? today.minusDays(1 + i % 9) : today.plusDays(1 + i % 20);
            boolean toReport = i % 2 == 0;
            DeliverableRequirement requirement = requirements.get((i % 3) * 2 + (toReport ? 1 : 0));
            data.task(project, leader, "업무 " + i + " 자료 조사와 정리", i % 3 == 0 ? "IN_PROGRESS" : "TODO", due,
                    toReport ? report : demo, requirement, people[i % 3]);
        }
        data.alert(project, park, "박팀원님이 최근 2주 동안 활동 기록이 없습니다");

        List<String> seen = new ArrayList<>(); List<AgentProposal> cards = new ArrayList<>(); StringBuilder text = new StringBuilder();
        runner.execute(runner.prepare(project.getId(), leader, "STATUS_BRIEF", json.readTree("{}")), new AgentEvents() {
            @Override public void step(String tool, String label) { seen.add("step"); }
            @Override public void text(String t) { seen.add("text"); text.append(t); }
            @Override public void proposal(AgentProposal p) { seen.add("proposal"); cards.add(p); }
            @Override public void done(UUID runId, List<UUID> ids) { seen.add("done"); }
            @Override public void error(String detail) { seen.add("error"); }
        });
        String brief = text.toString();
        long lines = Arrays.stream(brief.split("\n")).filter(l -> !l.isBlank()).count();
        long listedLate = Pattern.compile("board\\?task=").matcher(brief).results().count();
        System.out.println("SBRIEF|" + seen + "|줄 " + lines + "|업무 링크 " + listedLate + "|카드 " + cards.size() + "|" + brief.replace("\n", " ⏎ "));

        check("S1 끝까지 답함", seen.contains("done") && !seen.contains("error") && !brief.isBlank(), seen.toString());
        check("S2 " + LINES_MAX + "줄 이내", lines <= LINES_MAX, lines + "줄");
        check("S3 늦은 업무는 " + LISTED_LATE_MAX + "개까지만 적음", listedLate <= LISTED_LATE_MAX, listedLate + "개");
        check("S4 늦은 업무의 전체 개수(" + LATE + ")를 알림", brief.contains(String.valueOf(LATE)), brief);
        check("S5 업무 카드는 한 장, 업무 " + CARD_TASKS_MAX + "개까지", cards.size() <= 1
                && cards.stream().allMatch(c -> c.getContent().path("tasks").size() <= CARD_TASKS_MAX),
                cards.stream().map(c -> c.getContent().path("tasks").size()).toList().toString());

        failures.forEach(System.out::println);
        assertTrue(failures.isEmpty(), failures.size() + "개 항목이 기대와 달랐다:\n" + String.join("\n", failures));
    }

    private void check(String name, boolean ok, String detail) {
        System.out.println("SBRIEF|" + (ok ? "통과" : "실패") + "|" + name);
        if (!ok) failures.add("- " + name + ": " + detail.replace("\n", " "));
    }
}
