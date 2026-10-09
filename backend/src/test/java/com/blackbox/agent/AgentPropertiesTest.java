package com.blackbox.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

/** 설정이 빠지거나 틀리면 실행 중에 엉뚱하게 동작하지 않고 서버가 뜨지 않는다. */
class AgentPropertiesTest {
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withBean(AgentProperties.class);

    @Test void bindsTheValues() {
        runner.withPropertyValues("app.agent.zone=Asia/Seoul", "app.agent.tool-list-limit=100", "app.agent.run-time-limit=120s",
                "app.agent.runs-per-hour=30", "app.agent.brief-max-length=20000", "app.agent.question-max-length=1000").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(100, context.getBean(AgentProperties.class).getToolListLimit());
            assertNotNull(context.getBean(AgentProperties.class).today());
        });
    }

    @Test void refusesToStartWithoutAZoneOrWithALimitBelowOne() {
        runner.withPropertyValues("app.agent.tool-list-limit=100", "app.agent.run-time-limit=120s",
                "app.agent.runs-per-hour=30", "app.agent.brief-max-length=20000", "app.agent.question-max-length=1000").run(context -> assertNotNull(context.getStartupFailure()));
        runner.withPropertyValues("app.agent.zone=Asia/Seoul", "app.agent.tool-list-limit=0", "app.agent.run-time-limit=120s",
                "app.agent.runs-per-hour=30", "app.agent.brief-max-length=20000", "app.agent.question-max-length=1000").run(context -> assertNotNull(context.getStartupFailure()));
    }
}
