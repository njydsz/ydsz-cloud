package com.njydsz.common.util.id;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;

import com.njydsz.common.util.id.SnowflakeProperties.WorkerIdSource;
import com.njydsz.common.util.security.DigestUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Snowflake ID 生成器自动配置类
 *
 * <p>根据配置自动初始化 SnowflakeUtils，支持多种 workerId 来源策略。
 *
 * <p><b>初始化优先级：</b>
 * <ol>
 *   <li>优先使用 Spring 容器中的配置</li>
 *   <li>若未显式调用 init，则自动从配置解析</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.util.snowflake", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SnowflakeProperties.class)
public class SnowflakeAutoConfiguration {

    private static final long MAX_WORKER_ID = SnowflakeUtils.MAX_WORKER_ID_PUBLIC;
    private static final long MAX_DATACENTER_ID = SnowflakeUtils.MAX_DATACENTER_ID_PUBLIC;

    /**
     * 自动配置 Snowflake ID 生成器
     *
     * <p>根据配置自动解析 workerId 和 datacenterId，并初始化 SnowflakeUtils。
     * 支持多种 workerId 来源策略：分布式注册中心 > 环境变量 > 配置文件 > 实例索引。
     *
     * @param properties Snowflake 配置属性
     * @param environment Spring 环境
     * @param workerIdRegistryProvider WorkerId 注册中心（可选）
     */
    public SnowflakeAutoConfiguration(SnowflakeProperties properties, Environment environment,
                                      ObjectProvider<WorkerIdRegistry> workerIdRegistryProvider) {
        try {
            WorkerIdRegistry registry = workerIdRegistryProvider.getIfAvailable();
            long workerId = resolveWorkerId(properties, environment, registry);
            long datacenterId = resolveDatacenterId(properties, environment);
            SnowflakeUtils.init(workerId, datacenterId);
            log.info("SnowflakeUtils auto-configured. Worker ID: {}, Datacenter ID: {}, Source: {}, Registry: {}",
                    workerId, datacenterId, properties.getWorkerIdSource(),
                    registry != null ? registry.type() : "none");
        } catch (IllegalStateException e) {
            log.debug("SnowflakeUtils already initialized manually, skipping auto-configuration: {}", e.getMessage());
        }
    }

    /**
     * 解析 workerId
     */
    private long resolveWorkerId(SnowflakeProperties properties, Environment environment, WorkerIdRegistry registry) {
        WorkerIdSource source = properties.getWorkerIdSource();

        // 优先使用分布式注册中心获取 workerId
        if (registry != null) {
            try {
                String nodeIp = InetAddress.getLocalHost().getHostAddress();
                long workerId = registry.acquire(nodeIp, 300_000L);
                log.info("WorkerId {} acquired from registry {} for node {}", workerId, registry.type(), nodeIp);
                return validateWorkerId(workerId);
            } catch (Exception e) {
                log.warn("Failed to acquire workerId from registry {}, falling back to configured strategy: {}",
                        registry.type(), e.getMessage());
            }
        }

        return switch (source) {
            case CONFIG -> {
                Long workerId = properties.getWorkerId();
                if (workerId == null) {
                    log.warn("workerIdSource is CONFIG but workerId not configured, falling back to environment variable");
                    yield resolveFromEnv(properties);
                }
                yield validateWorkerId(workerId);
            }
            case ENVIRONMENT_VARIABLE -> resolveFromEnv(properties);
            case INSTANCE_INDEX -> resolveFromInstanceIndex(environment);
        };
    }

    /**
     * 从环境变量解析 workerId
     */
    private long resolveFromEnv(SnowflakeProperties properties) {
        String envVarName = properties.getEnvironmentVariableName();
        String value = System.getenv(envVarName);
        if (value != null && !value.isEmpty()) {
            try {
                long workerId = Long.parseLong(value);
                return validateWorkerId(workerId);
            } catch (NumberFormatException e) {
                log.warn("Invalid environment variable {}={}, falling back to auto-calculate", envVarName, value);
            }
        }
        log.debug("Environment variable {} not set, falling back to auto-calculate", envVarName);
        return resolveAutoWorkerId();
    }

    /**
     * 从实例索引解析 workerId
     */
    private long resolveFromInstanceIndex(Environment environment) {
        String indexStr = environment.getProperty("spring.cloud.instance.index");
        if (indexStr != null && !indexStr.isEmpty()) {
            try {
                long index = Long.parseLong(indexStr);
                return validateWorkerId(index);
            } catch (NumberFormatException e) {
                log.warn("Invalid spring.cloud.instance.index={}, falling back to auto-calculate", indexStr);
            }
        }
        log.debug("spring.cloud.instance.index not set, falling back to auto-calculate");
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

        String dcEnv = System.getenv("SNOWFLAKE_DATACENTER_ID");
        if (dcEnv != null && !dcEnv.isEmpty()) {
            try {
                long id = Long.parseLong(dcEnv);
                if (id >= 0 && id <= MAX_DATACENTER_ID) {
                    return id;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        String dcProp = environment.getProperty("ydsz.snowflake.datacenterId");
        if (dcProp != null && !dcProp.isEmpty()) {
            try {
                long id = Long.parseLong(dcProp);
                if (id >= 0 && id <= MAX_DATACENTER_ID) {
                    return id;
                }
            } catch (NumberFormatException ignored) {
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
            if (Long.parseLong(hash.substring(0, 5), 16) % 32 > MAX_DATACENTER_ID) {
                return MAX_DATACENTER_ID;
            }
            return Long.parseLong(hash.substring(0, 5), 16) % 32;
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
