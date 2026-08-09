package com.ok.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ok.WedSiteOnlineTestingApplication;
import com.ok.domain.enums.ExamStatus;
import com.ok.domain.enums.ExamType;
import com.ok.domain.enums.QuestionType;
import com.ok.domain.enums.Role;
import com.ok.entity.ExamEntity;
import com.ok.entity.QuestionEntity;
import com.ok.entity.QuestionOptionEntity;
import com.ok.entity.UserEntity;
import com.ok.repository.ExamRepository;
import com.ok.repository.QuestionRepository;
import com.ok.repository.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        classes = WedSiteOnlineTestingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("e2e")
class StudentExamFlowE2ETests {

    @LocalServerPort int port;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserRepository userRepository;
    @Autowired ExamRepository examRepository;
    @Autowired QuestionRepository questionRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void studentStartsAnswersSubmitsAndReadsOneCanonicalAttemptOverHttp() throws Exception {
        UserEntity teacher = userRepository.save(new UserEntity(
                "E2E Teacher",
                "e2e-flow-teacher@example.com",
                passwordEncoder.encode("Password123"),
                Role.TEACHER));
        Instant now = Instant.now();
        ExamEntity exam = new ExamEntity(
                teacher,
                "E2E Quiz",
                "Canonical attempt flow",
                now.minusSeconds(60),
                now.plusSeconds(3600),
                30,
                BigDecimal.valueOf(2),
                ExamType.MULTIPLE_CHOICE);
        exam.changeStatus(ExamStatus.PUBLISHED);
        exam = examRepository.saveAndFlush(exam);

        QuestionEntity question = new QuestionEntity(
                exam,
                "2 + 2 = ?",
                QuestionType.MULTIPLE_CHOICE,
                BigDecimal.valueOf(2),
                1);
        question.addOption(new QuestionOptionEntity("4", true, 1));
        question.addOption(new QuestionOptionEntity("5", false, 2));
        question = questionRepository.saveAndFlush(question);
        Long correctOptionId = question.getOptions().stream()
                .filter(QuestionOptionEntity::isCorrect)
                .findFirst().orElseThrow().getId();

        String email = "e2e-flow-student@example.com";
        String password = "Password123";
        assertThat(postJson("/api/auth/student/register", Map.of(
                "fullName", "E2E Flow Student",
                "email", email,
                "password", password), null).statusCode()).isEqualTo(201);

        HttpResponse<String> login = postJson("/api/auth/login", Map.of(
                "email", email,
                "password", password), null);
        String token = objectMapper.readTree(login.body()).get("token").asText();

        HttpResponse<String> start = postJson(
                "/api/student/exams/" + exam.getId() + "/attempts/start",
                null,
                token);
        assertThat(start.statusCode()).isEqualTo(200);
        long attemptId = objectMapper.readTree(start.body()).get("attemptId").asLong();

        HttpResponse<String> save = putJson(
                "/api/student/exam-attempts/" + attemptId
                        + "/questions/" + question.getId() + "/answer",
                Map.of("selectedOptionId", correctOptionId, "clientRevision", 1),
                token);
        assertThat(save.statusCode()).isEqualTo(200);

        HttpResponse<String> submit = postJson(
                "/api/student/exams/" + exam.getId()
                        + "/attempts/" + attemptId + "/submit",
                null,
                token);
        JsonNode submitted = objectMapper.readTree(submit.body());
        assertThat(submit.statusCode()).isEqualTo(200);
        assertThat(submitted.get("score").decimalValue()).isEqualByComparingTo("2.0");

        HttpResponse<String> result = get(
                "/api/student/exams/" + exam.getId() + "/attempts/me",
                token);
        JsonNode resultBody = objectMapper.readTree(result.body());
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(resultBody.get("attemptId").asLong()).isEqualTo(attemptId);
        assertThat(resultBody.get("score").decimalValue()).isEqualByComparingTo("2.0");
    }

    private HttpResponse<String> postJson(String path, Object body, String token) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(publisher);
        authorize(builder, token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> putJson(String path, Object body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        authorize(builder, token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        authorize(builder, token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void authorize(HttpRequest.Builder builder, String token) {
        if (token != null) builder.header("Authorization", "Bearer " + token);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
