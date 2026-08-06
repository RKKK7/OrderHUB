package com.oms.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    private static final String SECRET = "test_secret_key_at_least_32_characters_long_1234567890";
    private JwtAuthFilter filter;
    private GatewayFilterChain chain;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        JwtUtil jwtUtil = new JwtUtil(SECRET);
        filter = new JwtAuthFilter(jwtUtil);
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String validToken(String userId, String role) {
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    // ---------- PUBLIC PATHS ----------

    @Test
    void publicPath_register_passesThroughWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/users/register").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, times(1)).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void publicPath_login_passesThroughWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/users/login").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, times(1)).filter(any());
    }

    @Test
    void publicPath_browseProducts_passesThroughWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, times(1)).filter(any());
    }

    @Test
    void publicPath_productDetail_passesThroughWithoutToken() {
        // /api/products/{id} starts with /api/products so it matches
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/some-uuid").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, times(1)).filter(any());
    }

    @Test
    void publicPath_categories_passesThroughWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/categories").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, times(1)).filter(any());
    }

    // ---------- PROTECTED PATHS ----------

    @Test
    void protectedPath_withNoAuthHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPath_withMalformedHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders")
                .header("Authorization", "NotBearerFormat")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPath_withInvalidToken_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders")
                .header("Authorization", "Bearer garbage.invalid.token")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    // ---------- VALID TOKEN — HEADER INJECTION ----------

    @Test
    void protectedPath_withValidUserToken_injectsUserIdAndUserRole() {
        String token = validToken("user-42", "USER");
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertNull(exchange.getResponse().getStatusCode());
        verify(chain, times(1)).filter(any());
    }

    @Test
    void protectedPath_withValidAdminToken_injectsAdminRole() {
        String token = validToken("admin-1", "ADMIN");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/stats")
                .header("Authorization", "Bearer " + token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertNull(exchange.getResponse().getStatusCode());
        verify(chain, times(1)).filter(any());
    }

    @Test
    void protectedPath_notificationsRequireAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/notifications").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPath_adminRoutesRequireAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/orders").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void getOrder_returnsHighPriorityValue() {
        assertEquals(-1, filter.getOrder());
    }
}
