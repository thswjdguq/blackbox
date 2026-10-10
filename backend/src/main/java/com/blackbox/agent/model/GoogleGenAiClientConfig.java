package com.blackbox.agent.model;

import com.blackbox.agent.AgentProperties;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Gemini에 붙는 클라이언트를 Spring AI 대신 만든다. 공급자가 google-genai일 때만 쓰인다.
 * Spring AI는 클라이언트 빈이 이미 있으면 자기 것을 만들지 않는다(@ConditionalOnMissingBean). 이 공급자에는 시간 제한이나
 * 재시도를 정하는 설정 키도, 클라이언트를 고쳐 쓰는 커스터마이저도 없어서(1.1.8, 문서 2.0.1 확인) 빈을 직접 내놓는 것이 정해진 방법이다.
 * Google SDK는 기본으로 시간 제한 없이 다섯 번까지 다시 보낸다. 응답이 없는 요청 하나가 실행 스레드를 끝없이 잡는 것을 막으려고
 * 요청의 시간 제한과 횟수를 설정(app.agent)에서 받는다. API 키로 붙는 방식만 다루고 Vertex AI로 붙는 방식은 다루지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
class GoogleGenAiClientConfig {
    // 다시 보내는 것은 잠깐의 실패뿐이다. SDK의 기본에는 429(한도 초과)도 들어 있는데, 다시 보내도 같은 답이고 분당 한도만 더 쓴다
    private static final List<Integer> RETRYABLE = List.of(408, 500, 502, 503, 504);

    @Bean
    Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key:}") String apiKey, AgentProperties properties) {
        return client(apiKey, properties.getModelRequestTimeout(), properties.getModelRequestAttempts(), null);
    }

    /** baseUrl은 시험할 때만 준다 */
    static Client client(String apiKey, Duration timeout, int attempts, String baseUrl) {
        HttpOptions.Builder http = HttpOptions.builder()
                .timeout(Math.toIntExact(timeout.toMillis()))
                .retryOptions(HttpRetryOptions.builder().attempts(attempts).httpStatusCodes(RETRYABLE));
        if (baseUrl != null) http.baseUrl(baseUrl);
        Client.Builder client = Client.builder().httpOptions(http.build());
        // 키가 비어 있으면 넣지 않는다. SDK가 환경 변수에서 찾고, 거기도 없으면 Spring AI의 기본 동작처럼 서버가 뜨지 않는다
        if (!apiKey.isBlank()) client.apiKey(apiKey);
        return client.build();
    }
}
