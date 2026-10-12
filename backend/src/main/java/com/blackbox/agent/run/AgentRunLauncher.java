package com.blackbox.agent.run;

import com.blackbox.agent.AgentProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 준비된 실행을 요청 스레드 밖에서 돌리고, 시간 상한이 지나면 끝났다고 알린다.
 * 스레드는 여기서만 쓰는 것을 따로 둔다. 모델이 멈춰도 다른 기능(@Async, 요청 처리)의 스레드가 줄지 않는다.
 * 빈으로 내놓지 않는 것은 Executor 빈이 생기면 Spring Boot의 기본 실행기가 사라져 @Async가 이쪽으로 오기 때문이다.
 */
@Component
public class AgentRunLauncher {
    private final AgentRunner runner;
    private final AgentProperties properties;
    private final ThreadPoolExecutor executor;

    public AgentRunLauncher(AgentRunner runner, AgentProperties properties) {
        this.runner = runner;
        this.properties = properties;
        AtomicInteger number = new AtomicInteger();
        // 기다리는 줄이 없다. 자리가 없으면 바로 거절한다
        this.executor = new ThreadPoolExecutor(0, properties.getMaxConcurrentRuns(), 60, TimeUnit.SECONDS,
                new SynchronousQueue<>(), work -> new Thread(work, "agent-run-" + number.incrementAndGet()));
    }

    /** 자리가 없으면 그 실행을 실패로 끝내고 429가 되는 예외를 던진다 */
    public void launch(AgentRunner.Prepared prepared, AgentEvents events) {
        try {
            CompletableFuture.runAsync(() -> runner.execute(prepared, events), executor)
                    .orTimeout(properties.getRunTimeLimit().toMillis(), TimeUnit.MILLISECONDS)
                    // 시간 상한을 넘기면 듣는 쪽에는 여기서 알린다. 돌던 실행은 답이 와도 저장하지 않고 실패로 끝난다(AgentRunner)
                    // ponytail: 알림이 JDK의 공용 타이머 스레드에서 나간다. 느린 화면 하나가 다른 실행의 알림을 늦출 수 있다. 문제가 되면 알림용 스레드를 따로 둔다
                    .exceptionally(late -> { events.error(AgentRunner.FAILED); return null; });
        } catch (RejectedExecutionException e) {
            runner.fail(prepared, "동시에 돌릴 수 있는 실행 수를 넘겼습니다");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 뒤 다시 시도해주세요");
        }
    }

    @PreDestroy void stop() { executor.shutdownNow(); }
}
