package com.blackbox.agent.model;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 공급자를 openai로 고르고 주소를 바꾸면(OpenAI 호환 게이트웨이) 요청이 그 주소로 우리 설정대로 나가는지 본다.
 * 실제 API 대신 이 테스트가 띄운 서버가 답한다. 키는 가짜다.
 */
@SpringBootTest(properties = {"spring.ai.model.chat=openai", "spring.ai.openai.api-key=test-key-not-real",
        "spring.ai.openai.chat.options.model=test-model", "app.agent.model-request-timeout=2s"})
@ActiveProfiles("test")
class OpenAiWiringTest {
    static final List<String> requests = new CopyOnWriteArrayList<>();
    static volatile long delayMillis;
    static final HttpServer gateway = start();

    @Autowired AgentModel model;

    static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requests.add(exchange.getRequestURI().getPath() + " " + exchange.getRequestHeaders().getFirst("Authorization") + " " + body);
                try { Thread.sleep(delayMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                byte[] answer = ("{\"id\":\"x\",\"object\":\"chat.completion\",\"created\":0,\"model\":\"test-model\",\"choices\":[{\"index\":0,"
                        + "\"message\":{\"role\":\"assistant\",\"content\":\"게이트웨이의 답\"},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                try { exchange.sendResponseHeaders(200, answer.length); exchange.getResponseBody().write(answer); } catch (IOException ignored) { }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource static void gatewayAddress(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + gateway.getAddress().getPort());
    }

    @AfterAll static void stop() { gateway.stop(0); }

    @BeforeEach void reset() { requests.clear(); delayMillis = 0; }

    @Test void requestGoesToTheConfiguredAddressWithOurKeyAndModel() {
        String text = model.generate("지시", "질문", List.of());

        assertTrue(model.available());
        assertEquals("게이트웨이의 답", text);
        assertEquals(1, requests.size());
        assertTrue(requests.get(0).startsWith("/v1/chat/completions Bearer test-key-not-real "), requests.get(0));
        assertTrue(requests.get(0).contains("\"model\":\"test-model\""), requests.get(0));
    }

    /** 응답이 없는 게이트웨이가 실행 스레드를 끝없이 잡지 않는다: 요청마다 시간 제한(여기서는 2초)이 걸리고 정해 둔 횟수(2번)만 보낸다 */
    @Test void aSilentGatewayIsGivenUpOnWithinTheTimeLimit() {
        delayMillis = 8_000;
        long started = System.nanoTime();

        assertThrows(AgentModelException.class, () -> model.generate("지시", "질문", List.of()));

        long seconds = (System.nanoTime() - started) / 1_000_000_000;
        assertTrue(seconds < 8, "시간 제한이 걸리지 않았다: " + seconds + "초");
        assertTrue(requests.size() <= 2, "다시 보낸 횟수: " + requests.size());
    }
}
