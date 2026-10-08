package com.blackbox.service;

import com.blackbox.controller.ReviewCommentController;
import com.blackbox.dto.ReviewCommentDtos.*;
import com.blackbox.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** standalone MVC 입력·오류 형식 검사. 인증 필터 검증으로 보고하지 않는다. */
class ReviewCommentControllerTest {
    final ReviewCommentService service=mock(ReviewCommentService.class);
    MockMvc mvc;
    final String path="/api/projects/"+UUID.randomUUID()+"/deliverables/"+UUID.randomUUID()+"/reviews/"+UUID.randomUUID()+"/comments";
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.standaloneSetup(new ReviewCommentController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void resolutionRequiresObjectBody() throws Exception {
        mvc.perform(put(path+"/"+UUID.randomUUID()+"/resolution").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mvc.perform(put(path+"/"+UUID.randomUUID()+"/resolution").contentType(MediaType.APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void emptyObjectAllowsOptionalReason() throws Exception {
        mvc.perform(put(path+"/"+UUID.randomUUID()+"/resolution").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        verify(service).resolve(any(),any(),any(),any(),eq(new ResolutionRequest(null)),any());
    }
    @Test void invalidUuidIs400() throws Exception {
        mvc.perform(get(path+"/wrong/resolution")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(path+"/wrong/resolution")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void conflictHasProblemDetail() throws Exception {
        when(service.create(any(),any(),any(),any(),any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,"승인된 검토 회차는 변경할 수 없습니다"));
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"검토\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("detail").value("승인된 검토 회차는 변경할 수 없습니다"));
    }
}
