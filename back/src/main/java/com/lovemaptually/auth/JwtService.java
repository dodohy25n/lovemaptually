package com.lovemaptually.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class JwtService {
    private final byte[] secret;
    private final long expirationSeconds;
    private final ObjectMapper mapper;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-seconds}") long expirationSeconds,
                      ObjectMapper mapper) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
        this.mapper = mapper;
    }

    public String issue(long userId) {
        try {
            String header = encode(mapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            long now = Instant.now().getEpochSecond();
            String payload = encode(mapper.writeValueAsBytes(Map.of("sub", Long.toString(userId), "iat", now, "exp", now + expirationSeconds)));
            String unsigned = header + "." + payload;
            return unsigned + "." + encode(sign(unsigned));
        } catch (Exception e) { throw new IllegalStateException("JWT 발급에 실패했습니다", e); }
    }

    public long verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException();
            if (!java.security.MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), Base64.getUrlDecoder().decode(parts[2]))) throw new IllegalArgumentException();
            @SuppressWarnings("unchecked") Map<String, Object> payload = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            if (((Number) payload.get("exp")).longValue() < Instant.now().getEpochSecond()) throw new IllegalArgumentException();
            return Long.parseLong((String) payload.get("sub"));
        } catch (Exception e) { throw new IllegalArgumentException("invalid token"); }
    }

    public long expirationSeconds() { return expirationSeconds; }
    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
    private String encode(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
}
