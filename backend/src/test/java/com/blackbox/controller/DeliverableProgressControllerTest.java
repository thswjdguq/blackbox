package com.blackbox.controller;

import com.blackbox.dto.DeliverableProgressDtos.RequirementProgress;
import com.blackbox.dto.DeliverableProgressDtos.Response;
import com.blackbox.dto.DeliverableProgressDtos.TaskProgress;
import com.blackbox.entity.User;
import com.blackbox.service.DeliverableProgressService;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DeliverableProgressControllerTest {
    @Test
    void returnsTheContractResponseAtTheProgressPath() throws Exception {
        var service = mock(DeliverableProgressService.class);
        var controller = new DeliverableProgressController(service);
        UUID projectId = UUID.randomUUID();
        UUID deliverableId = UUID.randomUUID();
        var user = new User();
        user.setId(UUID.randomUUID());
        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                          NativeWebRequest request, org.springframework.web.bind.support.WebDataBinderFactory factory) {
                return user;
            }
        };
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(principalResolver)
                .build();
        when(service.get(projectId, deliverableId, user)).thenReturn(new Response(
                deliverableId,
                new TaskProgress(3, 1, new BigDecimal("33.33")),
                new RequirementProgress(2, 1, new BigDecimal("50.00"), true)
        ));

        mvc.perform(get("/api/projects/{projectId}/deliverables/{deliverableId}/progress", projectId, deliverableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliverableId").value(deliverableId.toString()))
                .andExpect(jsonPath("$.tasks.total").value(3))
                .andExpect(jsonPath("$.tasks.completed").value(1))
                .andExpect(jsonPath("$.tasks.percent").value(33.33))
                .andExpect(jsonPath("$.requiredRequirements.total").value(2))
                .andExpect(jsonPath("$.requiredRequirements.met").value(1))
                .andExpect(jsonPath("$.requiredRequirements.percent").value(50.0))
                .andExpect(jsonPath("$.requiredRequirements.assessmentAvailable").value(true));

        verify(service).get(projectId, deliverableId, user);
    }
}
