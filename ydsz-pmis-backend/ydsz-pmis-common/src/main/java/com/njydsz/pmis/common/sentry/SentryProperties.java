package com.njydsz.pmis.common.sentry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sentry 初始化配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentryProperties {
    private String dsn;
    private String environment;
    private String release;
    private String serverName;
    private String activeProfiles;
    private Double tracesSampleRate;
    private Double sampleRate;
}
