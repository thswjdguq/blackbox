package com.blackbox.agent.model;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 모델에 보내는 요청 하나하나(도구를 부르며 오가는 요청 각각)의 출력 토큰, 끝난 이유, 도구 호출 수를 서버 로그에만 남긴다.
 * SpringAiAgentModel의 로그는 도구 왕복을 합친 한 줄이라, 어느 요청에서 토큰을 썼는지 보이지 않았다(상태 요약이 출력 상한에 걸린 일).
 * Spring AI의 모델은 도구 반복을 안에서 돌리며 요청마다 관측(observation)을 낸다. 이 앱은 actuator가 없어 관측 레지스트리가 없으므로
 * 여기서 이 처리기만 붙인 레지스트리를 둔다. 화면에는 보내지 않는다
 */
@Configuration
class ModelRequestLogConfig {
    private static final Logger log = LoggerFactory.getLogger(ModelRequestLogConfig.class);

    // 레지스트리가 있으면 Spring Security의 필터 체인과 @Scheduled 작업도 관측을 만든다. 처리기는 모델 요청만 받으므로 동작은 같다.
    // 관측을 따로 구성하게 되면(actuator 추가 등) Boot 자동 설정보다 이 빈이 먼저 잡히므로, 이 레지스트리를 빼고 처리기만 빈으로 둔다
    @Bean @ConditionalOnMissingBean
    ObservationRegistry observationRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ModelRequestLog());
        return registry;
    }

    static class ModelRequestLog implements ObservationHandler<ChatModelObservationContext> {
        @Override public boolean supportsContext(Observation.Context context) {
            return context instanceof ChatModelObservationContext;
        }

        @Override public void onStop(ChatModelObservationContext context) {
            ChatResponse response = context.getResponse();
            if (response == null) return;
            Generation result = response.getResult();
            log.info("모델 요청 model={} 입력 {} 출력 {} 토큰 종료={} 도구 호출 {}건", response.getMetadata().getModel(),
                    response.getMetadata().getUsage().getPromptTokens(), response.getMetadata().getUsage().getCompletionTokens(),
                    result == null ? null : result.getMetadata().getFinishReason(),
                    result == null ? 0 : result.getOutput().getToolCalls().size());
        }
    }
}
