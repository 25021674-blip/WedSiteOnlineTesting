package com.ok.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ok.WedSiteOnlineTestingApplication;
import com.ok.repository.UserRepository;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        classes = WedSiteOnlineTestingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("e2e")
class AuthFlowE2ETests {

    @LocalServerPort
    int port;

    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void studentRegistersLogsInAndCallsProtectedApiOverRealHttp() throws Exception {
        String email = "e2e-student@example.com";
        String password = "Password123";

        HttpResponse<String> registration = postJson("/api/auth/student/register", Map.of(
                "fullName", "E2E Student",
                "email", email,
                "password", password));
        assertThat(registration.statusCode()).isEqualTo(201);
        assertThat(objectMapper.readTree(registration.body()).get("token").asText()).isNotBlank();

        HttpResponse<String> login = postJson("/api/auth/login", Map.of(
                "email", email,
                "password", password));
        assertThat(login.statusCode()).isEqualTo(200);
        String token = objectMapper.readTree(login.body()).get("token").asText();

        HttpRequest examsRequest = HttpRequest.newBuilder(uri("/api/exams"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> exams = http.send(examsRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(exams.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(exams.body()).isArray()).isTrue();

        var saved = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(passwordEncoder.matches(password, saved.getPassword())).isTrue();
    }

    private HttpResponse<String> postJson(String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
