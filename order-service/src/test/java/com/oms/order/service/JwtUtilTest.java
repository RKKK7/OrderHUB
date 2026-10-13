package com.oms.order.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    // 32+ chars required for HMAC-SHA256
    private static final String SECRET = "this-is-a-test-secret-key-with-enough-length-for-hmac";
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRATION_MS);
    }

    @Test
    void sign_producesNonNullToken() {
        String token = jwtUtil.sign("user-1", "USER");
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void sign_tokenContainsSubjectAndRoleClaim() {
        String token = jwtUtil.sign("user-1", "ADMIN");

        // Parse with the same key to verify claims
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();

        assertEquals("user-1", claims.getSubject());
        assertEquals("ADMIN", claims.get("role", String.class));
    }

    @Test
    void sign_tokenHasExpirationInFuture() {
        String token = jwtUtil.sign("user-1", "USER");

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();

        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().getTime() > System.currentTimeMillis());
    }

    @Test
    void sign_differentUsersGetDifferentTokens() {
        String token1 = jwtUtil.sign("user-1", "USER");
        String token2 = jwtUtil.sign("user-2", "USER");
        assertNotEquals(token1, token2);
    }
}
