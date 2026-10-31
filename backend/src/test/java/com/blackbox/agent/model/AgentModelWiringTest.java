package com.blackbox.agent.model;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** 공급자 설정이 없는 기본 상태에서 서버가 뜨고 에이전트만 꺼지는지 본다(K-30 7장). */
@SpringBootTest
@ActiveProfiles("test")
class AgentModelWiringTest {
    @Autowired AgentModel model;

    @Test void serverStartsWithoutAKeyAndTheAgentIsOff() {
        assertFalse(model.available());
    }
}
