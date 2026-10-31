package com.blackbox.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

/** 설정이 빠지거나 틀리면 실행 중에 엉뚱하게 동작하지 않고 서버가 뜨지 않는다. 값은 실제 application.yml에서 읽는다. */
class AgentPropertiesTest {
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withBean(AgentProperties.class);

    @Test void applicationYmlHasEveryValueTheAgentNeeds() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            AgentProperties properties = context.getBean(AgentProperties.class);
            assertNotNull(properties.today());
            assertTrue(properties.getModelRequestTimeout().compareTo(properties.getRunTimeLimit()) < 0,
                    "요청 하나의 시간 제한은 실행의 시간 상한보다 짧아야 한다");
        });
    }

    @Test void refusesToStartWithAValueThatMakesNoSense() {
        for (String wrong : new String[] {"app.agent.zone=Mars/Olympus", "app.agent.tool-list-limit=0", "app.agent.proposal-list-limit=0",
                "app.agent.run-time-limit=soon", "app.agent.model-request-attempts=0", "app.agent.max-tool-calls=0",
                "app.agent.runs-per-hour=0", "app.agent.max-concurrent-runs=0"}) {
            runner.withPropertyValues(wrong).run(context -> assertNotNull(context.getStartupFailure(), wrong));
        }
    }
}
