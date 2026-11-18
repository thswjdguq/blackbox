package com.blackbox.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 실제 Tomcat에서 리디렉션의 Location이 상대 경로인지 본다. MockMvc는 Tomcat의 절대 주소 변환을 거치지 않아 여기서만 확인된다.
 * 운영은 nginx가 https를 받아 http로 전달하므로, 절대 주소면 http://로 나간다(캘린더 OAuth 콜백)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RelativeRedirectHttpTest {
    @LocalServerPort int port;

    @Test void calendarCallbackRedirectsWithRelativeLocation() throws Exception {
        var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/google/callback?error=access_denied&state=probe"))
                .header("X-Forwarded-Proto", "https").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.discarding());
        assertEquals(302, response.statusCode());
        assertEquals("/profile/settings?calendar=error", response.headers().firstValue("Location").orElse(null));
    }
}
