package com.njydsz.common.core.featureflag;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * 特性开关配置属性
 *
 * <p>支持通过 Spring 配置文件预置特性开关的启用状态与灰度比例，运行时可通过
 * {@link FeatureFlagService#setEnabled} 动态调整。当 {@link NacosFeatureFlagService}
 * 可用时，Nacos 配置变更会覆盖本配置中的初始值。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * ydsz:
 *   feature-flag:
 *     enabled: true
 *     nacos:
 *       enabled: true
 *       server-addr: ${spring.cloud.nacos.config.server-addr}
 *       data-id: ydsz-feature-flags.json
 *       group: DEFAULT_GROUP
 *     flags:
 *       NEW_DASHBOARD:
 *         enabled: true
 *         rollout: 50
 *       BATCH_EXPORT:
 *         enabled: false
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.feature-flag")
public class FeatureFlagProperties {

    /** 模块总开关，关闭后 FeatureFlagService 仍可用但所有非强制开关返回 false */
    private boolean enabled = true;

    /** 各特性开关的初始配置，key 为 {@link FeatureFlag#name()} */
    private Map<String, FlagConfig> flags = new LinkedHashMap<>();

    /** Nacos 动态配置源（可选） */
    private NacosConfig nacos = new NacosConfig();

    /**
     * 单个特性开关配置
     */
    @Data
    public static class FlagConfig {

        /** 是否启用，null 表示未显式配置（回退到 false） */
        private Boolean enabled;

        /** 灰度发布百分比 0-100，null 表示未设置（按 enabled 全量生效） */
        private Integer rollout;
    }

    /**
     * Nacos 动态配置源属性
     *
     * <p>当 {@code enabled=true} 且 classpath 上存在 {@code com.alibaba.nacos.api.config.ConfigService}
     * 时，{@link NacosFeatureFlagService} 会自动注册并监听配置变更。
     */
    @Data
    public static class NacosConfig {

        /** 是否启用 Nacos 动态配置源（默认 false） */
        private boolean enabled = false;

        /** Nacos 服务地址，未配置时回退到 {@code spring.cloud.nacos.config.server-addr} */
        private String serverAddr;

        /** 配置 Data ID，默认 {@code ydsz-feature-flags.json} */
        private String dataId = "ydsz-feature-flags.json";

        /** 配置 Group，默认 {@code DEFAULT_GROUP} */
        private String group = "DEFAULT_GROUP";

        /** 拉取配置超时时间（毫秒），默认 5000ms */
        private long timeoutMs = 5000L;
    }
}
