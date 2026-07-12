package com.ok.auth.service;

import java.util.Base64;
import java.time.Instant;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.ok.entity.UserEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class JwtService {
    
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String SECRET = "change-this-secret-key";

    private static final long EXPIRATION_MILLIS = 1000L * 60 * 60 * 24;

    private static final Base64.Decoder BASE64_URL_DECODER =
            Base64.getUrlDecoder();

    public String generateToken(UserEntity user) {
        long now = Instant.now().toEpochMilli();
        long expiration = now + EXPIRATION_MILLIS;

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "jwt"
        );

        Map<String, Object> payload = Map.of(
                "sub", user.getEmail(),
                "role", user.getRole(),
                "iat", now,
                "exp", expiration
        );

       String encodedHeader = encodeJson(header);
       String encodedPayload = encodeJson(payload);

       String data = encodedHeader + "." + encodedPayload;
       String signature = sign(data);

       return data + "." + signature;
    }

    public String extractEmail(String token) {
        Map<String, Object> payload = readPayload(token);

        return payload.get("sub").toString();
    }

    public boolean isTokenValid(String token, String email) {
        String[] parts = token.split("\\.");

        if (parts.length != 3) {
            return false;
        }

        String data = parts[0] + "." + parts[1];
        String expectedSignature = sign(data);
        String actualSignature = parts[2];

        Map<String, Object> payload = readPayload(token);
        long expiration = ((Number) payload.get("exp")).longValue();
        String tokenEmail = payload.get("sub").toString();

        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                actualSignature.getBytes(StandardCharsets.UTF_8)
        ) && tokenEmail.equalsIgnoreCase(email)
        && expiration > Instant.now().toEpochMilli();
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(value);

            return BASE64_URL_ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
            
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể tạo JWT", exception);
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKeySpec secrecKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

            mac.init(secrecKey);

            byte[] signatureBytes = mac.doFinal(
                    data.getBytes(StandardCharsets.UTF_8)
            );

            return BASE64_URL_ENCODER.encodeToString(signatureBytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể ký JWT", exception);
        }
    }

    private Map<String, Object> readPayload(String token) {
        try {
            String[] parts = token.split("\\.");

            if (parts.length != 3) {
                throw new IllegalArgumentException("JWT không hợp lệ");
            }

            byte[] payloadBytes = BASE64_URL_DECODER.decode(parts[1]);

            return OBJECT_MAPPER.readValue(payloadBytes, new TypeReference<Map<String, Object>>() {});
        }catch (Exception exception) {
            throw new IllegalArgumentException("Không thể đọc JWT", exception);
        }
    }
}