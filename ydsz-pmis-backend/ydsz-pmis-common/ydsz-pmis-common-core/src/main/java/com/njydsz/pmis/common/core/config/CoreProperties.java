package com.njydsz.pmis.common.core.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * 核心配置属性
 *
 * <p><b>配置前缀规范：</b>
 * <ul>
 *   <li>所有 ydsz-pmis-common 模块的配置前缀统一使用 {@code ydsz.*} 格式</li>
 *   <li>当前版本保留历史前缀作为兼容，下个大版本统一迁移</li>
 *   <li>模块前缀映射：
 *     <ul>
 *       <li>{@code ydsz.core} → 核心配置</li>
 *       <li>{@code ydsz.jdbc} → 数据库配置</li>
 *       <li>{@code ydsz.redis} → 缓存配置</li>
 *       <li>{@code ydsz.lock} → 分布式锁配置</li>
 *       <li>{@code ydsz.auth} → 权限配置</li>
 *       <li>{@code ydsz.safe} → 安全配置</li>
 *       <li>{@code ydsz.file} → 文件配置</li>
 *       <li>{@code ydsz.notify} → 通知配置</li>
 *       <li>{@code ydsz.audit} → 审计配置</li>
 *       <li>{@code ydsz.queue} → 队列配置</li>
 *       <li>{@code ydsz.feign} → Feign配置</li>
 *       <li>{@code ydsz.job} → 任务配置</li>
 *       <li>{@code ydsz.gateway} → 网关配置</li>
 *       <li>{@code ydsz.doc} → 文档配置</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Data
@ConfigurationProperties(prefix = "ydsz.core")
@Validated
public class CoreProperties {

    /**
     * 是否启用核心模块自动配置（默认 true）
     *
     * <p>关闭后，{@code CoreAutoConfiguration} 不会自动注册核心 Bean。
     * 注意：核心模块提供分页常量、国际化等基础能力，关闭可能导致其他模块异常，请谨慎操作。
     */
    private boolean enabled = true;

    /** 最大每页记录数上限 */
    @Min(1)
    @Max(5000)
    private int maxPageSize = 1000;

    /** 默认每页记录数 */
    private int defaultPageSize = 10;

    /** 缓存过期时间配置 */
    private CacheProperties cache = new CacheProperties();

    /** 缓存过期时间配置内部类 */
    @Data
    public static class CacheProperties {
        /** 默认缓存过期时间（秒），1小时 */
        private long defaultExpireSeconds = 3600;
        /** 短效缓存过期时间（秒），5分钟 */
        private long shortExpireSeconds = 300;
        /** 中效缓存过期时间（秒），30分钟 */
        private long mediumExpireSeconds = 1800;
        /** 长效缓存过期时间（秒），1天 */
        private long longExpireSeconds = 86400;
    }

    /**
     * I18n 国际化配置
     */
    @Data
    public static class I18n {
        /**
         * 是否启用 i18n
         */
        private boolean enabled = true;

        /**
         * 默认语言区域
         */
        private String defaultLocale = "zh_CN";

        /**
         * 额外的 i18n 文件路径
         * <p>支持 classpath: 和 classpath*: 前缀
         * <p>例如：classpath:i18n/custom/exception
         */
        private List<String> additionalLocations = new ArrayList<>();
    }

    /** 国际化配置 */
    private I18n i18n = new I18n();

    /** Token 配置 */
    private TokenProperties token = new TokenProperties();

    /**
     * Token 配置属性内部类
     */
    @Data
    public static class TokenProperties {
        /** AccessToken 过期时间（毫秒），默认 24 小时 */
        private long accessTokenExpireTime = 24 * 60 * 60 * 1000;
        /** RefreshToken 过期时间（毫秒），默认 7 天 */
        private long refreshTokenExpireTime = 7 * 24 * 60 * 60 * 1000;
    }

    /** 安全配置 */
    private SecurityProperties security = new SecurityProperties();

    /**
     * 安全配置属性内部类
     */
    @Data
    public static class SecurityProperties {
        /** 最大登录尝试次数，超过后锁定账户 */
        private int maxLoginAttempts = 5;
        /** 登录锁定时长（毫秒），默认 30 分钟 */
        private long loginLockoutDuration = 30 * 60 * 1000;
    }
}
