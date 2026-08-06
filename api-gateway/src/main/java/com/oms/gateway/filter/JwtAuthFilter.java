package com.oms.gateway.filter;

import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Global filter that runs on every request.
 * - Public paths pass through without a token.
 * - Protected paths must have a valid JWT.
 * - On success, adds BOTH X-User-Id AND X-User-Role headers
 *   so downstream services never need an extra Feign call for authorization.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    /** Paths that never require authentication */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/users/register",
            "/api/users/login"
    );

    private static final List<String> PUBLIC_GET_PATHS = List.of(
            "/api/products"
    );

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

// REPLACE the entire filter method with this:

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        // If token is present, ALWAYS extract and forward — even on public paths.
        // This lets admins access admin endpoints under /api/products/manage
        // while unauthenticated users still browse /api/products publicly.
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtUtil.validateToken(token);
                String userId = claims.getSubject();
                String role = claims.get("role", String.class);

                ServerHttpRequest modifiedRequest = exchange.getRequest()
                        .mutate()
                        .header("X-User-Id", userId)
                        .header("X-User-Role", role != null ? role : "USER")
                        .build();

                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            } catch (Exception e) {
                // Invalid token on a public path → let it through without headers
                // Invalid token on a protected path → 401
                if (!isPublic(path, method)) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }
                return chain.filter(exchange);
            }
        }

        // No token at all
        if (isPublic(path, method)) {
            return chain.filter(exchange);
        }

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private boolean isPublic(String path, String method) {
        for (String pub : PUBLIC_PATHS) {
            if (path.startsWith(pub)) return true;
        }
        if ("GET".equalsIgnoreCase(method)) {
            for (String pub : PUBLIC_GET_PATHS) {
                if (path.startsWith(pub)) return true;
            }
        }
        if (path.startsWith("/actuator")) return true;
        return false;
    }

    @Override
    public int getOrder() {
        return -1; // run before routing filters
    }
}
