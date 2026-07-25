package com.njydsz.system.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AppInfoClient fallback.
 *
 * @author ydsz-team
 */
@Slf4j
@Component
public class AppInfoClientFallback implements AppInfoClient {

    @Override
    public boolean validateClient(String appKey, String appSecret) {
        log.warn("AppInfoClient fallback: validateClient={}", appKey);
        return false;
    }
}
