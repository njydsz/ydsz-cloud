package com.njydsz.pmis.common.search.controller;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.pmis.common.search.config.SearchProperties;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

"/** In-memory rate limiter for search API (P1-9) */"
@Slf4j
@Component
public class SearchRateLimiter {
    private static final int WINDOW_MS = 60000;
    private static final int DEFAULT_LIMIT = 60;
    private final ConcurrentHashMap<String, AtomicLong[]> windows = new ConcurrentHashMap<>();
    private final int maxRequests;

    public SearchRateLimiter(SearchProperties properties) {
        this.maxRequests = (properties != null && properties.getRateLimitPerMinute() > 0) ? properties.getRateLimitPerMinute() : DEFAULT_LIMIT;
    }

    public boolean tryAcquire() {
        String ip = getClientIp();
        long now = System.currentTimeMillis();
        AtomicLong[] state = windows.computeIfAbsent(ip, k -> new AtomicLong[]{new AtomicLong(now), new AtomicLong(0)});
        synchronized (state) {
            if (now - state[0].get() > WINDOW_MS) {
                state[0].set(now); state[1].set(1); return true;
            }
            if (state[1].get() >= maxRequests) { return false; }
            state[1].incrementAndGet(); return true;
        }
    }

    private String getClientIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest req = attrs.getRequest();
            String ip = req.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
            return req.getRemoteAddr();
        } catch (Exception e) { return "unknown"; }
    }
}
