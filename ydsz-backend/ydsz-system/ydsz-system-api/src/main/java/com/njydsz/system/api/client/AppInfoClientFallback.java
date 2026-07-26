package com.njydsz.system.api.client;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * AppInfoClient fallback.
 *
 * <p>Returns false on service failure (fail-closed for security).
 *
 * @author ydsz-team
 */
@Slf4j
@Component
public class AppInfoClientFallback implements AppInfoClient {

    @Override
    public boolean validateClient(Map<String, String> request) {
        log.warn("AppInfoClient fallback: validateClient appKey={}", request.get("appKey"));
        return false;
    }
}
