package com.njydsz.system.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ConfigClient fallback.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ConfigClientFallback implements ConfigClient {

    @Override
    public String getConfig(String key) {
        log.warn("ConfigClient fallback: getConfig={}", key);
        return null;
    }

    @Override
    public Object getDictItem(String typeCode, String itemCode) {
        log.warn("ConfigClient fallback: getDictItem={},{}", typeCode, itemCode);
        return null;
    }
}
