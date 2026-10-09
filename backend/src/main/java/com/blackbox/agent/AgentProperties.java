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

/** 에이전트의 설정값(app.agent). 값은 application.yml에 둔다. 빠지거나 틀리면 서버가 뜨지 않는다. */
@Component
@ConfigurationProperties(prefix = "app.agent")
@Validated
@Getter @Setter
public class AgentProperties {
    @NotNull private ZoneId zone;       // "오늘"과 "늦음"을 판단하는 시간대
    @Min(1) private int toolListLimit;   // 도구가 한 번에 돌려주는 목록의 건수 상한
    @NotNull private Duration runTimeLimit;   // 실행 하나의 시간 상한. 넘긴 실행은 끝난 것으로 본다
    @Min(1) private int runsPerHour;          // 프로젝트 하나가 한 시간에 실행할 수 있는 횟수
    @Min(1) private int maxConcurrentRuns;    // 서버 전체에서 동시에 도는 실행의 수
    @Min(1) private int briefMaxLength;       // 과제 안내문의 글자 수 상한
    @Min(1) private int questionMaxLength;    // 질문의 글자 수 상한

    public LocalDate today() { return LocalDate.now(zone); }
}
