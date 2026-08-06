package com.oms.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = "test_secret_key_at_least_32_characters_long_1234567890";
    private JwtUtil jwtUtil;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET);
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void validateToken_withValidToken_returnsCorrectClaims() {
        String token = Jwts.builder()
                .subject("user-123")
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        Claims claims = jwtUtil.validateToken(token);

        assertEquals("user-123", claims.getSubject());
        assertEquals("USER", claims.get("role", String.class));
    }

    @Test
    void validateToken_withAdminRole_returnsAdminClaim() {
        String token = Jwts.builder()
                .subject("admin-1")
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        Claims claims = jwtUtil.validateToken(token);

        assertEquals("ADMIN", claims.get("role", String.class));
    }

    @Test
    void validateToken_withExpiredToken_throwsException() {
        String expired = Jwts.builder()
                .subject("user-123")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key)
                .compact();

        assertThrows(Exception.class, () -> jwtUtil.validateToken(expired));
    }

    @Test
    void validateToken_withTamperedToken_throwsException() {
        String token = Jwts.builder()
                .subject("user-123")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThrows(Exception.class, () -> jwtUtil.validateToken(tampered));
    }

    @Test
    void validateToken_withWrongSigningKey_throwsException() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "a_completely_different_secret_key_32_chars_min".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user-123")
                .signWith(wrongKey)
                .compact();

        assertThrows(Exception.class, () -> jwtUtil.validateToken(token));
    }
}
