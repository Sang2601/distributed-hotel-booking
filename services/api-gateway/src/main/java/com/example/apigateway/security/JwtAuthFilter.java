package com.example.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;

/**
 * Chương 5 – Định danh.
 * Mọi request đi qua Gateway đều được kiểm tra JWT tại đây:
 *  - API công khai: không cần token.
 *  - API nội bộ (chỉ service gọi nhau): bị chặn từ bên ngoài.
 *  - API quản trị: token phải có role ADMIN.
 *  - Còn lại: token hợp lệ là đủ.
 * Sau khi xác thực, Gateway gắn X-User-Email / X-User-Role để service phía sau
 * biết ai đang gọi mà không phải tự giải mã token.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private record Rule(HttpMethod method, String pattern) {
        boolean matches(ServerHttpRequest request) {
            return (method == null || method.equals(request.getMethod()))
                    && MATCHER.match(pattern, request.getPath().value());
        }
    }

    private static final List<Rule> PUBLIC = List.of(
            new Rule(null, "/api/auth/**"),
            new Rule(HttpMethod.POST, "/api/users"),
            new Rule(HttpMethod.GET, "/api/rooms/**"),
            new Rule(HttpMethod.GET, "/api/rooms"),
            new Rule(HttpMethod.GET, "/actuator/**")
    );

    private static final List<Rule> INTERNAL_ONLY = List.of(
            new Rule(null, "/api/bookings/*/start-payment"),
            new Rule(null, "/api/bookings/*/fail")
    );

    private static final List<Rule> ADMIN_ONLY = List.of(
            new Rule(HttpMethod.GET, "/api/users"),
            new Rule(HttpMethod.GET, "/api/bookings"),
            new Rule(HttpMethod.GET, "/api/bookings/admin"),
            new Rule(HttpMethod.PUT, "/api/bookings/*/confirm"),
            new Rule(null, "/api/payments/revenue/**")
    );

    private final Key signingKey;

    public JwtAuthFilter(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Không cho client tự gửi header danh tính giả mạo
        ServerHttpRequest cleaned = request.mutate()
                .headers(h -> {
                    h.remove(HEADER_USER_EMAIL);
                    h.remove(HEADER_USER_ROLE);
                })
                .build();

        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange.mutate().request(cleaned).build());
        }

        if (anyMatch(INTERNAL_ONLY, request)) {
            return reject(exchange, HttpStatus.FORBIDDEN, "API nội bộ, không gọi trực tiếp từ bên ngoài");
        }

        boolean isPublic = anyMatch(PUBLIC, request);
        String token = extractToken(request);

        if (token == null) {
            if (isPublic) {
                return chain.filter(exchange.mutate().request(cleaned).build());
            }
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Thiếu token, vui lòng đăng nhập");
        }

        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            if (isPublic) {
                return chain.filter(exchange.mutate().request(cleaned).build());
            }
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Token không hợp lệ hoặc đã hết hạn");
        }

        String email = claims.getSubject();
        String role = String.valueOf(claims.get("role"));

        if (anyMatch(ADMIN_ONLY, request) && !"ADMIN".equals(role)) {
            return reject(exchange, HttpStatus.FORBIDDEN, "Chỉ quản trị viên được phép");
        }

        ServerHttpRequest withIdentity = cleaned.mutate()
                .header(HEADER_USER_EMAIL, email)
                .header(HEADER_USER_ROLE, role)
                .build();

        return chain.filter(exchange.mutate().request(withIdentity).build());
    }

    /**
     * Token lấy từ header Authorization. Riêng kết nối WebSocket (/ws/**): trình duyệt không gắn
     * header tuỳ ý khi mở WebSocket, nên chấp nhận token qua query ?access_token=...
     */
    private static String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        if (MATCHER.match("/ws/**", request.getPath().value())) {
            return request.getQueryParams().getFirst("access_token");
        }
        return null;
    }

    private static boolean anyMatch(List<Rule> rules, ServerHttpRequest request) {
        return rules.stream().anyMatch(r -> r.matches(request));
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":" + status.value() + ",\"message\":\"" + message + "\"}";
        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
