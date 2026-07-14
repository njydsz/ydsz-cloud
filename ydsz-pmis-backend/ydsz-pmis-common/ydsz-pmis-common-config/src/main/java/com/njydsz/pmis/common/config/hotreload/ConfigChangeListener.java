package com.njydsz.pmis.common.config.hotreload;

public interface ConfigChangeListener {
    void onChange(String key, String oldValue, String newValue);
}