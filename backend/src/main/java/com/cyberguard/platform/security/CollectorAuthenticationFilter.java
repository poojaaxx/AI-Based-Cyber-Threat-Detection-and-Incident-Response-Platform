package com.cyberguard.platform.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;
/** Dedicated loopback-only ingest identity; never accepts or creates a user JWT. */
public class CollectorAuthenticationFilter extends OncePerRequestFilter {
    private final boolean enabled;
    private final byte[] credential;
    public CollectorAuthenticationFilter(boolean enabled, String credential) {
        this.enabled = enabled;
        if (enabled && credential.length() < 32) throw new IllegalStateException("COLLECTOR_INGEST_KEY must contain at least 32 characters when enabled");
        this.credential = credential.getBytes(StandardCharsets.UTF_8);
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/collector/");
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
        String key = req.getHeader("X-Collector-Key");
        if (!enabled || !Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(req.getRemoteAddr())
                || req.getHeader("Forwarded") != null || req.getHeader("X-Forwarded-For") != null
                || key == null || key.length() > 256
                || !MessageDigest.isEqual(credential, key.getBytes(StandardCharsets.UTF_8))) {
            res.setStatus(401); return;
        }
        if (!"POST".equals(req.getMethod())) { res.setStatus(405); return; }
        if (req.getContentLengthLong() < 0 || req.getContentLengthLong() > 262144) { res.setStatus(413); return; }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "windows-collector", null, List.of(new SimpleGrantedAuthority("COLLECTOR_INGEST"))));
        chain.doFilter(req, res);
    }
}
