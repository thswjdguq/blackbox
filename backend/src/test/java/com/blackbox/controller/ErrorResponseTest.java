package com.blackbox.controller;

import com.blackbox.exception.GlobalExceptionHandler;
import com.blackbox.service.DeliverableService;
import com.blackbox.service.FileVaultService;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 표준 MVC 오류가 모든 예외를 받는 핸들러에 가로채여 500이 되지 않고 올바른 상태코드로 나가는지 실제 요청으로 확인한다. */
class ErrorResponseTest {
    static final String PROJECT = "/api/projects/" + UUID.randomUUID();
    final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new DeliverableController(mock(DeliverableService.class)),
                    new FileVaultController(mock(FileVaultService.class)))
            .setControllerAdvice(handler())
            .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
            .build();

    static GlobalExceptionHandler handler() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        var handler = new GlobalExceptionHandler();
        handler.setMessageSource(messages);
        return handler;
    }

    @Test void malformedUuidIs400() throws Exception {
        mvc.perform(get("/api/projects/not-a-uuid/deliverables"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("주소나 값의 형식이 올바르지 않습니다"));
    }
    @Test void brokenJsonIs400() throws Exception {
        mvc.perform(post(PROJECT + "/deliverables").contentType("application/json").content("{not json"))
                .andExpect(status().isBadRequest());
    }
    @Test void missingRequestParamIs400() throws Exception {
        mvc.perform(get(PROJECT + "/files/history"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("필요한 값이 빠졌습니다: fileName"));
    }
    @Test void missingFilePartIs400() throws Exception {
        mvc.perform(multipart(PROJECT + "/files"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("첨부 파일이 없습니다"));
    }
    @Test void unsupportedMethodIs405() throws Exception {
        mvc.perform(patch(PROJECT + "/deliverables")).andExpect(status().isMethodNotAllowed());
    }
    @Test void unsupportedContentTypeIs415() throws Exception {
        mvc.perform(post(PROJECT + "/deliverables").contentType("text/plain").content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }
    @Test void validationFailureKeepsErrorsField() throws Exception {
        mvc.perform(post(PROJECT + "/deliverables").contentType("application/json").content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }
}
