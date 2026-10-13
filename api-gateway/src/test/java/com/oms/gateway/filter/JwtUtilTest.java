package com.oms.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = "my-super-secret-key-for-testing-purposes-at-least-32-bytes";
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET);
    }

    private String generateToken(String userId, String role, Date expiration) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var builder = Jwts.builder()
                .subject(userId)
                .issuedAt(new Date());
        if (role != null) {
            builder.claim("role", role);
        }
        if (expiration != null) {
            builder.expiration(expiration);
        }
        return builder.signWith(key).compact();
    }

    @Test
    void validateToken_validToken_returnsClaims() {
        String token = generateToken("user-1", "ADMIN", new Date(System.currentTimeMillis() + 60_000));

        Claims claims = jwtUtil.validateToken(token);

        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void validateToken_expiredToken_throwsException() {
        String token = generateToken("user-1", "USER", new Date(System.currentTimeMillis() - 60_000));

        assertThatThrownBy(() -> jwtUtil.validateToken(token))
                .isInstanceOf(Exception.class);
    }

    @Test
    void validateToken_wrongSecret_throwsException() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "wrong-secret-key-that-is-also-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user-1")
                .signWith(wrongKey)
                .compact();

        assertThatThrownBy(() -> jwtUtil.validateToken(token))
                .isInstanceOf(Exception.class);
    }
}
