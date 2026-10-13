package com.blackbox.agent;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

/** 에이전트의 설정값(app.agent). 별도 테스트 기본값을 두고, 설정된 값은 검증 후 바인딩한다. */
@Component
@ConfigurationProperties(prefix = "app.agent")
@Validated
@Getter @Setter
public class AgentProperties {
    // 별도 OpenAI 테스트 환경의 기본 상한. app.agent 설정이 있으면 Spring 바인딩 값으로 교체된다.
    @NotNull private ZoneId zone = ZoneId.of("Asia/Seoul");
    @Min(1) private int toolListLimit = 50;
    @Min(1) private int proposalListLimit = 50;
    @NotNull private Duration runTimeLimit = Duration.ofSeconds(120);
    @NotNull private Duration modelRequestTimeout = Duration.ofSeconds(30);
    @Min(1) private int modelRequestAttempts = 1;
    @Min(1) private int maxToolCalls = 12;
    @Min(1) private int runsPerHour = 20;
    @Min(1) private int maxConcurrentRuns = 2;
    @Min(1) private int briefMaxLength = 20000;
    @Min(1) private int questionMaxLength = 1000;

    public LocalDate today() { return LocalDate.now(zone); }
}
