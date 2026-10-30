package com.blackbox.agent.model;

import com.blackbox.agent.AgentProperties;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 공급자를 Gemini로 고르면 서버가 뜨고 우리 설정이 클라이언트까지 닿는지 본다. 키는 가짜이고 Gemini를 부르지 않는다.
 * 실제 모델 테스트는 키가 있을 때만 돌기 때문에, 조립이 깨지는 것은 여기서 잡는다.
 */
@SpringBootTest(properties = {"spring.ai.model.chat=google-genai", "spring.ai.google.genai.api-key=test-key-not-real"})
@ActiveProfiles("test")
class GeminiWiringTest {
    @Autowired ConfigurableApplicationContext context;
    @Autowired AgentModel model;
    @Autowired AgentProperties properties;

    @Test void serverStartsWithGeminiAndOurClientIsTheOneInUse() {
        assertTrue(model.available());
        assertArrayEquals(new String[] {"googleGenAiClient"}, context.getBeanNamesForType(Client.class));
        String madeBy = context.getBeanFactory().getBeanDefinition("googleGenAiClient").getFactoryBeanName();
        assertEquals("googleGenAiClientConfig", madeBy, "Spring AI의 자동 설정이 아니라 우리 구성이 만든 클라이언트다");
        // 설정이 빈 채로 굳지 않았다(모델 빈이 너무 일찍 만들어지면 여기가 null이다)
        assertNotNull(properties.getModelRequestTimeout());
        assertNotNull(properties.getZone());
    }
}
