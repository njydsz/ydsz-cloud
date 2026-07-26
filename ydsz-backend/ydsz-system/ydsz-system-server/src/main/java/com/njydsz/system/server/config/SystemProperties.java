package com.njydsz.system.server.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 系统模块配置属性。
 *
 * <p>对应配置前缀 {@code ydsz.system}，所有配置项均可通过 Nacos 动态调整。
 *
 * @author ydsz-team
 */
@Data
@ConfigurationProperties(prefix = "ydsz.system")
public class SystemProperties {

    /** 是否启用系统模块健康检查。 */
    private boolean healthEnabled = true;

    /** 配置缓存相关。 */
    private ConfigCache config = new ConfigCache();

    /** 字典缓存相关。 */
    private DictCache dict = new DictCache();

    /** 系统变量缓存相关。 */
    private VariableCache variable = new VariableCache();

    /** 应用密钥相关。 */
    private App app = new App();

    /** 内部 API IP 白名单（空列表=不限制）。 */
    private List<String> internalApiIpWhitelist = new ArrayList<>();

    /**
     * 配置缓存配置。
     */
    @Data
    public static class ConfigCache {
        /** 是否启用配置缓存。 */
        private boolean enabled = true;

        /** 配置缓存 TTL（分钟）。 */
        private int cacheTtlMinutes = 5;
    }

    /**
     * 字典缓存配置。
     */
    @Data
    public static class DictCache {
        /** 是否启用字典缓存。 */
        private boolean enabled = true;

        /** 字典缓存 TTL（分钟）。 */
        private int cacheTtlMinutes = 10;
    }

    /**
     * 系统变量缓存配置。
     */
    @Data
    public static class VariableCache {
        /** 是否启用系统变量缓存。 */
        private boolean enabled = true;

        /** 系统变量缓存 TTL（分钟）。 */
        private int cacheTtlMinutes = 5;
    }

    /**
     * 应用密钥配置。
     */
    @Data
    public static class App {
        /** BCrypt 加密强度（4-31）。 */
        private int bcryptStrength = 10;
    }
}
