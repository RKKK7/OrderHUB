package com.oms.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GatewayFilterChain chain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtUtil);
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void publicPath_noToken_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/users/login")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void publicGetPath_products_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/products")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void protectedPath_noToken_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/orders")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPath_validToken_injectsHeadersAndPassesThrough() {
        Claims claims = new DefaultClaims(Map.of("sub", "user-42", "role", "ADMIN"));
        when(jwtUtil.validateToken("valid-token")).thenReturn(claims);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/orders")
                .header("Authorization", "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            var mutatedExchange = (org.springframework.web.server.ServerWebExchange) ex;
            String userId = mutatedExchange.getRequest().getHeaders().getFirst("X-User-Id");
            String role = mutatedExchange.getRequest().getHeaders().getFirst("X-User-Role");
            return "user-42".equals(userId) && "ADMIN".equals(role);
        }));
    }

    @Test
    void protectedPath_invalidToken_returns401() {
        when(jwtUtil.validateToken("bad-token")).thenThrow(new RuntimeException("Invalid token"));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/orders")
                .header("Authorization", "Bearer bad-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void publicPath_invalidToken_passesThrough() {
        when(jwtUtil.validateToken("bad-token")).thenThrow(new RuntimeException("Invalid token"));

        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/users/register")
                .header("Authorization", "Bearer bad-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void validToken_nullRole_defaultsToUser() {
        Claims claims = new DefaultClaims(Map.of("sub", "user-7"));
        when(jwtUtil.validateToken("token-no-role")).thenReturn(claims);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/orders")
                .header("Authorization", "Bearer token-no-role")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            var mutated = (org.springframework.web.server.ServerWebExchange) ex;
            return "USER".equals(mutated.getRequest().getHeaders().getFirst("X-User-Role"));
        }));
    }

    @Test
    void actuatorPath_passesWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void getOrder_returnsNegativeOne() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }
}
