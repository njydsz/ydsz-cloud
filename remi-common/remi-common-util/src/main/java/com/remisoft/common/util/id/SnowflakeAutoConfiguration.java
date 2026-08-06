package com.remisoft.common.util.id;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;

import com.remisoft.common.util.id.SnowflakeProperties.WorkerIdSource;
import com.remisoft.common.util.security.DigestUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Snowflake ID 生成器自动配置类
 *
 * <p>根据配置自动初始化 SnowflakeUtils，支持多种 workerId 来源策略。
 *
 * <p><b>初始化优先级：</b>
 * <ol>
 *   <li>优先使用 Spring 容器中的 {@link SnowflakeIdGenerator} Bean（推荐方式）</li>
 *   <li>若未使用 SnowflakeIdGenerator Bean，则通过本配置类初始化静态兼容层</li>
 * </ol>
 *
 * <p>WorkerId 解析顺序：分布式注册中心 > 配置文件 > 环境变量 > 自动计算。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @deprecated 自 2.0.0 起简化，v3.0 移除。
 *             WorkerId 解析现在由 {@link SnowflakeIdGenerator} 完成（Spring Bean 方式），
 *             本配置类仅做兼容性过渡。
 */
@Deprecated(since = "2.0.0", forRemoval = false)
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "remi.util.snowflake", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SnowflakeProperties.class)
public class SnowflakeAutoConfiguration {

    private static final long MAX_WORKER_ID = SnowflakeUtils.getMaxWorkerId();
    private static final long MAX_DATACENTER_ID = SnowflakeUtils.getMaxDatacenterId();

    /** 数据中心环境变量名（与 workerId 命名风格统一：REMI_SNOWFLAKE_*） */
    private static final String DATACENTER_ENV_VAR = "REMI_SNOWFLAKE_DATACENTER_ID";

    /**
     * 自动配置 Snowflake ID 生成器（简化版）
     *
     * <p>仅作为兼容性过渡，推荐直接使用 {@link SnowflakeIdGenerator} Bean 方式。
     *
     * @param properties Snowflake 配置属性
     * @param environment Spring 环境
     */
    public SnowflakeAutoConfiguration(SnowflakeProperties properties, Environment environment) {
        // 兼容旧版：通过静态方式初始化（如果 SnowflakeIdGenerator Bean 不存在）
        try {
            long workerId = resolveWorkerId(properties, environment);
            long datacenterId = resolveDatacenterId(properties, environment);
            SnowflakeUtils.init(workerId, datacenterId);
            log.info("SnowflakeUtils auto-configured (legacy mode). Worker ID: {}, Datacenter ID: {}, Source: {}",
                    workerId, datacenterId, properties.getWorkerIdSource());
        } catch (IllegalStateException e) {
            log.warn("SnowflakeUtils 已被提前初始化，配置无效: {}", e.getMessage());
        }
    }

    /**
     * 解析 workerId（不再依赖注册中心心跳机制）
     */
    private long resolveWorkerId(SnowflakeProperties properties, Environment environment) {
        WorkerIdSource source = properties.getWorkerIdSource();

        return switch (source) {
            case CONFIG -> {
                Long workerId = properties.getWorkerId();
                if (workerId == null) {
                    log.warn("workerIdSource is CONFIG but workerId not configured, falling back to environment variable");
                    yield resolveFromEnv();
                }
                yield validateWorkerId(workerId);
            }
            case ENVIRONMENT_VARIABLE -> resolveFromEnv();
        };
    }

    /**
     * 从环境变量解析 workerId
     */
    private long resolveFromEnv() {
        String value = System.getenv(SnowflakeProperties.WORKER_ID_ENV_VAR);
        if (value != null && !value.isEmpty()) {
            try {
                long workerId = Long.parseLong(value);
                return validateWorkerId(workerId);
            } catch (NumberFormatException e) {
                log.warn("Invalid environment variable {}={}, falling back to auto-calculate",
                        SnowflakeProperties.WORKER_ID_ENV_VAR, value);
            }
        }
        log.debug("Environment variable {} not set, falling back to auto-calculate",
                SnowflakeProperties.WORKER_ID_ENV_VAR);
        return resolveAutoWorkerId();
    }

    /**
     * 自动计算 workerId（基于 IP 地址哈希）
     *
     * <p>使用 SHA-256 哈希算法，与 {@link SnowflakeUtils#computeWorkerId()} 保持一致，
     * 避免不同路径产生不同 workerId 导致集群冲突。
     */
    private long resolveAutoWorkerId() {
        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            String hash = DigestUtils.sha256Hex(hostAddress);
            return validateWorkerId(Long.parseLong(hash.substring(0, 5), 16) % 32);
        } catch (UnknownHostException e) {
            return validateWorkerId(ThreadLocalRandom.current().nextLong(32));
        }
    }

    /**
     * 解析 datacenterId
     */
    private long resolveDatacenterId(SnowflakeProperties properties, Environment environment) {
        Long datacenterId = properties.getDatacenterId();
        if (datacenterId != null) {
            if (datacenterId > MAX_DATACENTER_ID) {
                log.warn("Configured datacenterId {} exceeds max {}, falling back to auto-calculate",
                        datacenterId, MAX_DATACENTER_ID);
                return resolveAutoDatacenterId(environment);
            }
            return datacenterId;
        }

        String dcEnv = System.getenv(DATACENTER_ENV_VAR);
        if (dcEnv != null && !dcEnv.isEmpty()) {
            try {
                long id = Long.parseLong(dcEnv);
                if (id >= 0 && id <= MAX_DATACENTER_ID) {
                    return id;
                }
            } catch (NumberFormatException ignored) {
                log.debug("Caught exception (ignored): {}", ignored.getMessage());
            }
        }

        String dcProp = environment.getProperty("remi.util.snowflake.datacenter-id");
        if (dcProp != null && !dcProp.isEmpty()) {
            try {
                long id = Long.parseLong(dcProp);
                if (id >= 0 && id <= MAX_DATACENTER_ID) {
                    return id;
                }
            } catch (NumberFormatException ignored) {
                log.debug("Caught exception (ignored): {}", ignored.getMessage());
            }
        }

        return resolveAutoDatacenterId(environment);
    }

    /**
     * 自动计算 datacenterId（基于主机名哈希）
     *
     * <p>使用 SHA-256 哈希算法，与 {@link SnowflakeUtils#getDataCenterId()} 保持一致。
     */
    private long resolveAutoDatacenterId(Environment environment) {
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            String hash = DigestUtils.sha256Hex(hostName);
            long id = Long.parseLong(hash.substring(0, 5), 16) % 32;
            return Math.min(id, MAX_DATACENTER_ID);
        } catch (UnknownHostException e) {
            return ThreadLocalRandom.current().nextLong(MAX_DATACENTER_ID + 1);
        }
    }

    /**
     * 验证 workerId 范围
     */
    private long validateWorkerId(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            log.warn("WorkerId {} out of range [0, {}], falling back to auto-calculate",
                    workerId, MAX_WORKER_ID);
            return resolveAutoWorkerId();
        }
        return workerId;
    }
}
