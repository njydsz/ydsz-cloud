package com.njydsz.system.api.client;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * ConfigClient fallback.
 *
 * <p>Returns null instead of empty string to allow callers to distinguish
 * "service unavailable" from "config exists but value is empty".
 *
 * @author ydsz-team
 */
@Slf4j
@Component
public class ConfigClientFallback implements ConfigClient {

    @Override
    public String getConfig(Map<String, String> request) {
        log.warn("ConfigClient fallback: getConfig key={}", request.get("key"));
        return null;
    }

    @Override
    public String getDictItem(Map<String, String> request) {
        log.warn("ConfigClient fallback: getDictItem typeCode={}, itemCode={}",
                request.get("typeCode"), request.get("itemCode"));
        return null;
    }
}
