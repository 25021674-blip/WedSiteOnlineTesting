package com.ok.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;

import com.ok.domain.enums.Role;
import com.ok.entity.UserEntity;

@Tag("security")
class AuthApiIntegrationTests extends AbstractApiIntegrationTest {

    @Test
    void registerNormalizesInputHashesPasswordAndReturnsToken() throws Exception {
        mvc.perform(post("/api/auth/student/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "  Nguyen Van A  ",
                                "email", "  Student@Example.COM ",
                                "password", "Password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.email").value("student@example.com"))
                .andExpect(jsonPath("$.role").value("STUDENT"));

        UserEntity saved = userRepository.findByEmailIgnoreCase("student@example.com").orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("Password123");
        assertThat(passwordEncoder.matches("Password123", saved.getPassword())).isTrue();
        assertThat(saved.getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void duplicateEmailIsCaseInsensitiveAndReturnsConflictContract() throws Exception {
        user("duplicate@example.com", Role.STUDENT);

        mvc.perform(post("/api/auth/student/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Duplicate",
                                "email", "DUPLICATE@EXAMPLE.COM",
                                "password", "Password123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").isMap());
    }

    @Test
    void loginWithCorrectCredentialsReturnsIdentityAndValidToken() throws Exception {
        UserEntity saved = user("teacher@example.com", Role.TEACHER);

        String token = objectMapper.readTree(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", " TEACHER@EXAMPLE.COM ",
                                "password", "Password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(saved.getId()))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        assertThat(jwtService.extractEmail(token)).isEqualTo("teacher@example.com");
        assertThat(jwtService.isTokenValid(token, saved.getEmail())).isTrue();
    }

    @Test
    void wrongPasswordReturnsUnauthorizedWithoutLeakingWhichCredentialFailed() throws Exception {
        user("student@example.com", Role.STUDENT);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "student@example.com", "password", "WrongPassword"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @ParameterizedTest(name = "invalid register payload: {0}")
    @MethodSource("invalidRegistrations")
    void invalidRegistrationReturnsFieldError(String name, Map<String, Object> body, String field) throws Exception {
        mvc.perform(post("/api/auth/student/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors." + field).exists());
    }

    static Stream<Arguments> invalidRegistrations() {
        return Stream.of(
                Arguments.of("blank name", Map.of("fullName", " ", "email", "a@b.com", "password", "Password123"), "fullName"),
                Arguments.of("long name", Map.of("fullName", "A".repeat(101), "email", "a@b.com", "password", "Password123"), "fullName"),
                Arguments.of("bad email", Map.of("fullName", "A", "email", "not-email", "password", "Password123"), "email"),
                Arguments.of("blank email", Map.of("fullName", "A", "email", " ", "password", "Password123"), "email"),
                Arguments.of("short password", Map.of("fullName", "A", "email", "a@b.com", "password", "1234567"), "password"),
                Arguments.of("long password", Map.of("fullName", "A", "email", "a@b.com", "password", "A".repeat(73)), "password"));
    }

    @ParameterizedTest
    @MethodSource("invalidLogins")
    void invalidLoginPayloadReturnsBadRequest(Map<String, Object> body, String field) throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + field).exists());
    }

    static Stream<Arguments> invalidLogins() {
        return Stream.of(
                Arguments.of(Map.of("email", "", "password", "Password123"), "email"),
                Arguments.of(Map.of("email", "bad", "password", "Password123"), "email"),
                Arguments.of(Map.of("email", "a@b.com", "password", ""), "password"));
    }

    @Test
    void protectedEndpointRejectsMissingAndMalformedTokens() throws Exception {
        mvc.perform(get("/api/exams"))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/exams").header("Authorization", "Bearer malformed.token.value"))
                .andExpect(status().is4xxClientError());
    }
}
