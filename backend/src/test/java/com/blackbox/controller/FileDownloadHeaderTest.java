package com.blackbox.controller;

import com.blackbox.service.FileVaultService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** 내려받기 응답의 파일 이름을 브라우저가 올린 이름 그대로 읽는지 본다. */
class FileDownloadHeaderTest {
    final FileVaultService files = mock(FileVaultService.class);
    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FileVaultController(files)).build();

    @Test void koreanNameSurvives() throws Exception {
        String header = header("중간보고서_초안.pdf");
        assertTrue(header.contains("filename*=UTF-8''"), header);
        assertEquals("중간보고서_초안.pdf", ContentDisposition.parse(header).getFilename());
    }

    @Test void plainNameStaysReadable() throws Exception {
        assertEquals("attachment; filename=\"report-v2.pdf\"", header("report-v2.pdf"));
    }

    @Test void quoteInNameDoesNotBreakTheHeader() throws Exception {
        ContentDisposition parsed = ContentDisposition.parse(header("a\"; filename=\"evil.exe"));
        assertTrue(parsed.isAttachment());
        assertEquals("a\"; filename=\"evil.exe", parsed.getFilename());
    }

    private String header(String fileName) throws Exception {
        when(files.download(any(), any())).thenReturn(
                new FileVaultService.DownloadResult(fileName, 3L, new ByteArrayResource(new byte[]{1, 2, 3})));
        var response = mvc.perform(get("/api/files/{id}/download", UUID.randomUUID())).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        return response.getHeader(HttpHeaders.CONTENT_DISPOSITION);
    }
}
