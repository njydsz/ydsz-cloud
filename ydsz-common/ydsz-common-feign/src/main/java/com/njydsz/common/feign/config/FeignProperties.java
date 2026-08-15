package com.njydsz.common.feign.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import feign.Logger;
import lombok.Getter;
import lombok.Setter;

/**
 * YdszFeign 模块核心配置属性类
 * 
 * <p>配置前缀：ydsz.feign，仅保留高频使用的核心配置项，其他能力使用Spring Cloud原生配置即可。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ydsz.feign")
public class FeignProperties {

    /** 模块总开关，默认true */
    private boolean enabled = true;

    /** Feign日志级别，默认BASIC，可选NONE/HEADERS/FULL */
    private String loggerLevel = "BASIC";

    /** 核心请求头透传配置 */
    private final Propagation propagation = new Propagation();

    /** 请求重试配置 */
    private final Retry retry = new Retry();

    /** 超时配置（毫秒） */
    private final Timeout timeout = new Timeout();

    /** 链路追踪配置 */
    private final Trace trace = new Trace();

    /** 监控指标配置 */
    private final Metrics metrics = new Metrics();

    /** 熔断器开关能力，具体熔断规则使用Resilience4j原生配置 */
    private final CircuitBreaker circuitBreaker = new CircuitBreaker();

    /**
     * 解析日志级别为Feign枚举值
     */
    public Logger.Level resolvedLoggerLevel() {
        if (loggerLevel == null || loggerLevel.isBlank()) {
            return Logger.Level.BASIC;
        }
        try {
            return Logger.Level.valueOf(loggerLevel.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return Logger.Level.BASIC;
        }
    }

    /**
     * 请求头透传配置
     */
    @Getter
    @Setter
    public static class Propagation {
        /** 是否启用请求头透传，默认true */
        private boolean enabled = true;

        /** 默认透传的13个核心业务头：覆盖链路追踪、身份鉴权、权限校验、租户隔离等所有业务场景 */
        private Set<String> headers = new LinkedHashSet<>(Arrays.asList(
                "traceparent", // W3C链路追踪头
                "X-Tenant-Id", // 租户ID
                "X-Access-Token", // 访问令牌
                "X-Request-Id", // 请求唯一ID
                "X-User-Userid", // 当前用户ID
                "X-User-Username", // 当前用户名
                "X-User-Locale", // 用户语言环境（国际化）
                "X-Request-Source", // 请求来源标识
                "X-Company-Ids", // 公司ID集合（权限校验）
                "X-Data-Scope", // 数据权限范围类型
                "X-Unique-Id", // 用户登录唯一ID
                "X-Dept-Ids", // 部门ID集合（权限校验）
                "X-Service-Type" // 服务类型标识
        ));
    }

    /**
     * 请求重试配置
     */
    @Getter
    @Setter
    public static class Retry {
        /** 是否启用重试，默认true */
        private boolean enabled = true;

        /** 最大重试次数（包含首次调用），默认3 */
        private int maxAttempts = 3;
    }

    /**
     * 超时配置
     */
    @Getter
    @Setter
    public static class Timeout {
        /** 连接超时时间（毫秒），默认5000 */
        private long connect = 5000;

        /** 读取超时时间（毫秒），默认10000 */
        private long read = 10000;
    }

    /**
     * 链路追踪配置
     */
    @Getter
    @Setter
    public static class Trace {
        /** 是否启用W3C traceparent协议头透传，默认true */
        private boolean enabled = true;
    }

    /**
     * 监控指标配置
     */
    @Getter
    @Setter
    public static class Metrics {
        /** 是否启用Feign调用指标采集，默认true */
        private boolean enabled = true;
    }

    /**
     * 熔断器开关配置
     */
    @Getter
    @Setter
    public static class CircuitBreaker {
        /** 是否启用Resilience4j熔断能力，默认false */
        private boolean enabled = false;
    }
}