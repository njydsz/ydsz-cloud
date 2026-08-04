package com.remisoft.common.util.id;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
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
 *   <li>优先使用 Spring 容器中的配置</li>
 *   <li>若未显式调用 init，则自动从配置解析</li>
 * </ol>
 *
 * @author remi-team
 * @since 1.0.0
 * 
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "remi.util.snowflake", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SnowflakeProperties.class)
public class SnowflakeAutoConfiguration implements DisposableBean {

    private static final long MAX_WORKER_ID = SnowflakeUtils.getMaxWorkerId();
    private static final long MAX_DATACENTER_ID = SnowflakeUtils.getMaxDatacenterId();

    /** 数据中心环境变量名（与 workerId 命名风格统一：REMI_SNOWFLAKE_*） */
    private static final String DATACENTER_ENV_VAR = "REMI_SNOWFLAKE_DATACENTER_ID";

    /** 注册中心句柄，应用关闭时用于释放 WorkerId（可能为 null） */
    private WorkerIdRegistry registry;
    /** 当前节点 IP，用于注册中心标识 */
    private String registryNodeIp;
    /** 从注册中心获取的 WorkerId，应用关闭时用于释放 */
    private long registryWorkerId;
    /** 心跳调度器，应用关闭时需 shutdown */
    private ScheduledExecutorService heartbeatScheduler;

    /**
     * 自动配置 Snowflake ID 生成器
     *
     * <p>根据配置自动解析 workerId 和 datacenterId，并初始化 SnowflakeUtils。
     * 支持多种 workerId 来源策略：分布式注册中心 > 环境变量 > 配置文件 > 实例索引。
     *
     * <p>当使用分布式注册中心获取 WorkerId 时，会自动启动心跳续约任务，
     * 避免租约过期导致 WorkerId 被回收引发 ID 冲突。
     *
     * @param properties Snowflake 配置属性
     * @param environment Spring 环境
     * @param workerIdRegistryProvider WorkerId 注册中心（可选）
     */
    public SnowflakeAutoConfiguration(SnowflakeProperties properties, Environment environment,
                                      ObjectProvider<WorkerIdRegistry> workerIdRegistryProvider) {
        try {
            registry = workerIdRegistryProvider.getIfAvailable();
            long workerId = resolveWorkerId(properties, environment, registry);
            long datacenterId = resolveDatacenterId(properties, environment);
            SnowflakeUtils.init(workerId, datacenterId);
            log.info("SnowflakeUtils auto-configured. Worker ID: {}, Datacenter ID: {}, Source: {}, Registry: {}",
                    workerId, datacenterId, properties.getWorkerIdSource(),
                    registry != null ? registry.type() : "none");
        } catch (IllegalStateException e) {
            log.warn("SnowflakeUtils 已被提前初始化，配置无效，请检查是否存在 Bean 初始化顺序问题: {}", e.getMessage());
        }
    }

    /**
     * 应用关闭时释放注册中心资源
     *
     * <p>停止心跳调度器并通知注册中心释放 WorkerId，避免租约残留。
     */
    @Override
    public void destroy() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
            log.info("Snowflake heartbeat scheduler shut down for workerId {}", registryWorkerId);
        }
        if (registry != null && registryNodeIp != null) {
            try {
                registry.release(registryWorkerId, registryNodeIp);
                log.info("WorkerId {} released from registry {} for node {}",
                        registryWorkerId, registry.type(), registryNodeIp);
            } catch (Exception e) {
                log.warn("Failed to release workerId {} from registry: {}",
                        registryWorkerId, e.getMessage());
            }
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
                long leaseMillis = properties.getLeaseMillis();
                long workerId = registry.acquire(nodeIp, leaseMillis);
                // 启动心跳续约，避免租约过期导致 WorkerId 被回收引发 ID 冲突
                registryNodeIp = nodeIp;
                registryWorkerId = workerId;
                heartbeatScheduler = registry.startHeartbeat(workerId, nodeIp, leaseMillis);
                log.info("WorkerId {} acquired from registry {} for node {}, heartbeat started (lease={}ms)",
                        workerId, registry.type(), nodeIp, leaseMillis);
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
