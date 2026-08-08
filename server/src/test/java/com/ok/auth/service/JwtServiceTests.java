package com.ok.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import com.ok.domain.enums.Role;
import com.ok.entity.UserEntity;

@Tag("unit")
@Tag("security")
class JwtServiceTests {

    private static final String SECRET = "a-test-secret-that-is-definitely-long-enough";

    @Test
    void constructorRejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService("too-short", 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatedTokenContainsSubjectAndValidSignature() {
        JwtService service = new JwtService(SECRET, 60_000);
        UserEntity user = new UserEntity("Student", "student@example.com", "hash", Role.STUDENT);

        String token = service.generateToken(user);

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(service.extractEmail(token)).isEqualTo("student@example.com");
        assertThat(service.isTokenValid(token, "STUDENT@EXAMPLE.COM")).isTrue();
    }

    @Test
    void tamperedSignatureIsRejected() {
        JwtService service = new JwtService(SECRET, 60_000);
        String token = service.generateToken(new UserEntity("A", "a@b.com", "hash", Role.STUDENT));

        assertThat(service.isTokenValid(token.substring(0, token.length() - 1) + "x", "a@b.com")).isFalse();
    }

    @Test
    void tokenForAnotherEmailIsRejected() {
        JwtService service = new JwtService(SECRET, 60_000);
        String token = service.generateToken(new UserEntity("A", "a@b.com", "hash", Role.STUDENT));

        assertThat(service.isTokenValid(token, "other@b.com")).isFalse();
    }

    @Test
    void constructorRejectsNonPositiveExpiration() {
        assertThatThrownBy(() -> new JwtService(SECRET, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedTokenCannotBeReadAndIsNotValid() {
        JwtService service = new JwtService(SECRET, 60_000);

        assertThat(service.isTokenValid("not-a-jwt", "a@b.com")).isFalse();
        assertThatThrownBy(() -> service.extractEmail("not-a-jwt"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
