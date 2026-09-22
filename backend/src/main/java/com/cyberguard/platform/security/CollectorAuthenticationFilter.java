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
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated collector ingest identity; never accepts or creates a user JWT.
 * LOCAL path (unchanged Phase 3B behavior): loopback-only, rejects any proxy-forwarded request.
 * REMOTE path (opt-in via app.collector.remote-enabled): lets a laptop-hosted collector reach a
 * cloud-hosted backend over HTTPS. Remote-addr equality is meaningless once traffic crosses a
 * platform load balancer, so remote requests are instead bound by HTTPS-only transport, a
 * timestamp window against replay, and a per-key rate limit, on top of the same shared key check.
 */
public class CollectorAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> LOOPBACK_ADDRESSES = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    private final boolean enabled;
    private final boolean remoteEnabled;
    private final byte[] credential;
    private final long maxClockSkewMillis;
    private final int rateLimitPerMinute;
    private final ConcurrentHashMap<String, RateWindow> rateWindows = new ConcurrentHashMap<>();

    public CollectorAuthenticationFilter(boolean enabled, String credential, boolean remoteEnabled,
                                          long maxClockSkewSeconds, int rateLimitPerMinute) {
        this.enabled = enabled;
        if (enabled && credential.length() < 32) throw new IllegalStateException("COLLECTOR_INGEST_KEY must contain at least 32 characters when enabled");
        this.credential = credential.getBytes(StandardCharsets.UTF_8);
        this.remoteEnabled = remoteEnabled;
        this.maxClockSkewMillis = maxClockSkewSeconds * 1000L;
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/collector/");
    }

    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
        if (!enabled) { res.setStatus(401); return; }

        String key = req.getHeader("X-Collector-Key");
        if (key == null || key.length() > 256 || !MessageDigest.isEqual(credential, key.getBytes(StandardCharsets.UTF_8))) {
            res.setStatus(401); return;
        }

        boolean loopback = LOOPBACK_ADDRESSES.contains(req.getRemoteAddr());
        boolean forwarded = req.getHeader("Forwarded") != null || req.getHeader("X-Forwarded-For") != null;

        if (loopback && !forwarded) {
            // LOCAL path: identical to the original Phase 3B check, no timestamp/rate-limit requirement.
        } else if (remoteEnabled && !loopback) {
            if (!isHttps(req)) { res.setStatus(401); return; }
            if (!withinClockSkew(req)) { res.setStatus(401); return; }
            if (!allowedByRateLimit(key)) { res.setStatus(429); return; }
        } else {
            res.setStatus(401); return;
        }

        if (!"POST".equals(req.getMethod())) { res.setStatus(405); return; }
        if (req.getContentLengthLong() < 0 || req.getContentLengthLong() > 262144) { res.setStatus(413); return; }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "windows-collector", null, List.of(new SimpleGrantedAuthority("COLLECTOR_INGEST"))));
        chain.doFilter(req, res);
    }

    private boolean isHttps(HttpServletRequest req) {
        String proto = req.getHeader("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(proto) || req.isSecure();
    }

    private boolean withinClockSkew(HttpServletRequest req) {
        String header = req.getHeader("X-Collector-Timestamp");
        if (header == null) return false;
        try {
            long requestMillis = Long.parseLong(header.trim());
            return Math.abs(Instant.now().toEpochMilli() - requestMillis) <= maxClockSkewMillis;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean allowedByRateLimit(String key) {
        if (rateLimitPerMinute <= 0) return true;
        String bucketKey = Integer.toHexString(key.hashCode());
        long nowMinute = Instant.now().getEpochSecond() / 60;
        RateWindow window = rateWindows.compute(bucketKey, (k, existing) ->
                (existing == null || existing.minute != nowMinute) ? new RateWindow(nowMinute) : existing);
        return window.count.incrementAndGet() <= rateLimitPerMinute;
    }

    private static final class RateWindow {
        final long minute;
        final AtomicInteger count = new AtomicInteger(0);
        RateWindow(long minute) { this.minute = minute; }
    }
}
