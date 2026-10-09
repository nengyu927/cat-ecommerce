package com.catshop.security;

import com.catshop.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/** Single-node MVP limiter. Uses the socket address, never untrusted forwarded headers. */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private record Window(long start, int count) {}
    private final Map<String, Window> windows = new LinkedHashMap<>();
    private final ObjectMapper mapper;
    private final int limit;
    private final long windowMs;
    private final Clock clock;

    @Autowired
    public LoginRateLimitFilter(ObjectMapper mapper,
            @Value("${app.auth.login-limit:10}") int limit,
            @Value("${app.auth.login-window-ms:60000}") long windowMs) {
        this(mapper, limit, windowMs, Clock.systemUTC());
    }

    LoginRateLimitFilter(ObjectMapper mapper, int limit, long windowMs, Clock clock) {
        this.mapper = mapper; this.limit = limit; this.windowMs = windowMs; this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !java.util.Set.of("/api/auth/login","/api/auth/forgot-password","/api/auth/reset-password").contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        long retryMs = 0;
        synchronized (windows) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().start() >= windowMs);
            String address = request.getRemoteAddr()+":"+request.getServletPath();
            Window window = windows.getOrDefault(address, new Window(now, 0));
            if (window.count() >= limit) retryMs = windowMs - (now - window.start());
            else if (!windows.containsKey(address) && windows.size() >= 10000) retryMs = windowMs;
            else windows.put(address, new Window(window.start(), window.count() + 1));
        }
        if (retryMs > 0) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(Math.max(1, (retryMs + 999) / 1000)));
            mapper.writeValue(response.getWriter(), ApiResponse.error("RATE_LIMITED", "操作過於頻繁，請稍後再試"));
            return;
        }
        chain.doFilter(request, response);
    }
}
