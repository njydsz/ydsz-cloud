package com.njydsz.system.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ConfigClient fallback.
 *
 * <p>Returns safe default values instead of null to prevent NPE in callers.
 *
 * @author ydsz-team
 */
@Slf4j
@Component
public class ConfigClientFallback implements ConfigClient {

    @Override
    public String getConfig(String key) {
        log.warn("ConfigClient fallback: getConfig={}", key);
        return "";
    }

    @Override
    public String getDictItem(String typeCode, String itemCode) {
        log.warn("ConfigClient fallback: getDictItem={},{}", typeCode, itemCode);
        return "{}";
    }
}
