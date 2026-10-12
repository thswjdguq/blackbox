package com.blackbox.agent.model;

import com.blackbox.agent.AgentProperties;
import com.google.genai.Client;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gemini 클라이언트가 설정한 시간 제한과 횟수를 지키는지 본다. 실제 Gemini 대신 이 테스트가 띄운 서버에 붙인다.
 * 시간 제한이 없으면 응답 없는 요청이 스레드를 끝없이 잡는다(2026-10-10에 16분 넘게 멈춘 것을 봤다).
 */
class GoogleGenAiClientConfigTest {
    HttpServer server;
    final AtomicInteger requests = new AtomicInteger();
    final CountDownLatch release = new CountDownLatch(1);
    volatile int status;   // 0이면 응답하지 않고 붙잡아 둔다

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            try {
                if (status == 0) { release.await(30, TimeUnit.SECONDS); return; }
                byte[] body = "{\"error\":{\"code\":503,\"message\":\"overloaded\",\"status\":\"UNAVAILABLE\"}}".getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException ignored) {
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach void stop() {
        release.countDown();
        server.stop(0);
    }

    @Test void aRequestWithNoAnswerGivesUpAtTheTimeLimit() {
        status = 0;
        Client client = client(Duration.ofMillis(700), 1);

        long started = System.nanoTime();
        assertThrows(RuntimeException.class, () -> client.models.generateContent("gemini-test", "안녕", null));
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertTrue(millis >= 600 && millis < 5_000, "시간 제한 근처에서 포기한다: " + millis + "ms");
    }

    @Test void aTemporaryFailureIsSentAgainOnlyAsManyTimesAsConfigured() {
        status = 503;
        assertThrows(RuntimeException.class, () -> client(Duration.ofSeconds(5), 2).models.generateContent("gemini-test", "안녕", null));
        assertEquals(2, requests.get(), "처음 한 번과 다시 한 번");

        requests.set(0);
        assertThrows(RuntimeException.class, () -> client(Duration.ofSeconds(5), 1).models.generateContent("gemini-test", "안녕", null));
        assertEquals(1, requests.get());
    }

    @Test void aQuotaErrorIsNotSentAgain() {
        // 한도 초과(429)는 다시 보내 봐야 같은 답이고, 분당 한도만 더 쓴다
        status = 429;
        assertThrows(RuntimeException.class, () -> client(Duration.ofSeconds(5), 3).models.generateContent("gemini-test", "안녕", null));
        assertEquals(1, requests.get());
    }

    @Test void springAiDoesNotMultiplyTheAttempts() {
        // Spring AI에도 재시도가 있다(spring.ai.retry). 서버가 실제로 쓰는 것과 같은 재시도를 붙여, 요청 수가 곱으로 늘지 않는지 본다
        status = 503;
        new ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration.class))
                .withPropertyValues("spring.ai.retry.max-attempts=3", "spring.ai.retry.backoff.initial-interval=100ms",
                        "spring.ai.retry.backoff.multiplier=2", "spring.ai.retry.backoff.max-interval=200ms")
                .run(context -> {
                    var chatModel = org.springframework.ai.google.genai.GoogleGenAiChatModel.builder()
                            .genAiClient(client(Duration.ofSeconds(5), 2))
                            .defaultOptions(org.springframework.ai.google.genai.GoogleGenAiChatOptions.builder().model("gemini-test").build())
                            .retryTemplate(context.getBean(org.springframework.retry.support.RetryTemplate.class)).build();

                    assertThrows(RuntimeException.class, () -> chatModel.call(new org.springframework.ai.chat.prompt.Prompt("안녕")));
                    assertEquals(2, requests.get(), "SDK가 정한 횟수만 나간다. Spring AI의 재시도는 Gemini의 실패에 걸리지 않는다");
                });
    }

    @Test void theClientIsOursOnlyWhenGeminiIsTheProvider() {
        AgentProperties properties = new AgentProperties();
        properties.setModelRequestTimeout(Duration.ofSeconds(60)); properties.setModelRequestAttempts(2);
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(GoogleGenAiClientConfig.class).withBean(AgentProperties.class, () -> properties);

        runner.withPropertyValues("spring.ai.model.chat=none").run(context -> assertFalse(context.containsBean("googleGenAiClient")));
        runner.withPropertyValues("spring.ai.model.chat=bedrock-converse").run(context -> assertFalse(context.containsBean("googleGenAiClient")));
        runner.withPropertyValues("spring.ai.model.chat=google-genai", "spring.ai.google.genai.api-key=test-key")
                .run(context -> assertNotNull(context.getBean(Client.class)));
    }

    private Client client(Duration timeout, int attempts) {
        return GoogleGenAiClientConfig.client("test-key", timeout, attempts, "http://localhost:" + server.getAddress().getPort());
    }
}
