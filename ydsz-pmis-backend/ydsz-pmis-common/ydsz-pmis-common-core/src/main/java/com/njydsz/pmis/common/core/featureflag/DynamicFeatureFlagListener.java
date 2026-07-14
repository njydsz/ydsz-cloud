package com.njydsz.pmis.common.core.featureflag;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicFeatureFlagListener {
    private static final Logger log = LoggerFactory.getLogger(DynamicFeatureFlagListener.class);
    private final FeatureFlagManager manager;
    private final Map<String, String> configKeys = new ConcurrentHashMap<>();
    public DynamicFeatureFlagListener(FeatureFlagManager manager) { this.manager = manager; }
    public void registerFlag(String flagName, String configKey) { configKeys.put(configKey, flagName); }
    public void onConfigChange(String configKey, String value) {
        String flagName = configKeys.get(configKey);
        if (flagName == null) return;
        boolean enabled = Boolean.parseBoolean(value);
        manager.updateFlag(flagName, enabled);
        log.info("Feature flag {} updated via config: {}={}", flagName, configKey, value);
    }
}