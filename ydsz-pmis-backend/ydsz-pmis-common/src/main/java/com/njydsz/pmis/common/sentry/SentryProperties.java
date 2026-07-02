package com.njydsz.pmis.common.sentry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sentry 初始化配置
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentryProperties {
    /** Sentry DSN 地址 */
    private String dsn;
    /** 环境标识，如 dev / test / prod */
    private String environment;
    /** 发布版本号 */
    private String release;
    /** 服务名称 */
    private String serverName;
    /** 激活的 Spring profiles 列表 */
    private String activeProfiles;
    /** 性能采样率（0.0 ~ 1.0） */
    private Double tracesSampleRate;
    /** 错误采样率（0.0 ~ 1.0） */
    private Double sampleRate;
}
