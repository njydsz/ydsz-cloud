package com.njydsz.cronjob.server.core.config;

import java.util.concurrent.ThreadPoolExecutor;

import com.njydsz.common.json.Json;

import org.springframework.stereotype.Component;

import com.alibaba.nacos.api.config.annotation.NacosConfigListener;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher;
import com.njydsz.cronjob.server.core.executor.TenantAwareExecutorPool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 线程池热更新监听器（P0-4）。
 *
 * <p>监听 Nacos 配置变更，动态调整任务执行线程池的核心参数：
 * <ul>
 *   <li>{@code executor.maxConcurrent}：最大并发数（调整 corePoolSize / maxPoolSize）</li>
 *   <li>{@code executor.queueCapacity}：队列容量</li>
 *   <li>{@code executor.isolationStrategy}：租户隔离策略（none / tenant / job_group）</li>
 *   <li>{@code executor.tenantPoolSize}：租户隔离线程池大小</li>
 *   <li>{@code executor.tenantPoolQueueCapacity}：租户隔离队列容量</li>
 * </ul>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>通过 {@link ThreadPoolExecutor#setCorePoolSize} / {@link ThreadPoolExecutor#setMaximumPoolSize}
 *       实现线程池参数的运行时调整，无需重启</li>
 *   <li>队列容量无法动态调整（BlockingQueue 不支持 resize），仅记录新值，下次创建线程池时生效</li>
 *   <li>隔离策略变更时，清空旧的租户线程池缓存，下次 getExecutor 时按新策略创建</li>
 *   <li>使用 try-catch 包裹，确保配置解析异常不影响应用启动</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThreadPoolHotUpdateListener {

    private final CronjobProperties cronjobProperties;
    private final DefaultTaskDispatcher defaultTaskDispatcher;
    private final TenantAwareExecutorPool tenantAwareExecutorPool;

    /**
     * Nacos 配置变更监听。
     *
     * <p>监听 {@code pmis-cronjob.yml}（或对应的 dataId），当检测到 executor 相关配置变更时，
     * 解析新配置并应用到运行中的线程池。
     *
     * @param configInfo Nacos 下发的配置内容（YAML 或 JSON）
     */
    @NacosConfigListener(dataId = "${ydsz.cronjob.config-data-id:pmis-cronjob.yml}", timeout = 5000)
    public void onConfigChange(String configInfo) {
        if (configInfo == null || configInfo.isBlank()) {
            return;
        }
        log.info("[ThreadPoolHotUpdate] 收到配置变更通知, 开始解析...");
        try {
            JSONObject config = parseConfig(configInfo);
            if (config == null) {
                return;
            }
            applyExecutorConfigChanges(config);
        } catch (Exception e) {
            log.error("[ThreadPoolHotUpdate] 配置热更新失败: reason={}", e.getMessage(), e);
        }
    }

    /**
     * 解析配置内容（支持 JSON 格式）。
     *
     * <p>YAML 格式由 Nacos 客户端自动转换为 JSON，此处统一按 JSON 解析。
     * 兼容两层嵌套：顶层 ydsz.cronjob.executor 或直接 executor。
     *
     * @param configInfo 配置内容
     * @return executor 配置 JSON 对象；解析失败返回 null
     */
    private JSONObject parseConfig(String configInfo) {
        try {
            JSONObject root = JSONObject.from(Json.parseMap(configInfo));
            // 尝试 ydsz.cronjob.executor 路径
            JSONObject pmis = root.getJSONObject("pmis");
            if (pmis != null) {
                JSONObject cronjob = ydsz.getJSONObject("cronjob");
                if (cronjob != null) {
                    JSONObject executor = cronjob.getJSONObject("executor");
                    if (executor != null) {
                        return executor;
                    }
                }
            }
            // 尝试直接 cronjob.executor 路径
            JSONObject cronjobDirect = root.getJSONObject("cronjob");
            if (cronjobDirect != null) {
                JSONObject executor = cronjobDirect.getJSONObject("executor");
                if (executor != null) {
                    return executor;
                }
            }
            // 尝试直接 executor 路径
            JSONObject executorDirect = root.getJSONObject("executor");
            if (executorDirect != null) {
                return executorDirect;
            }
            log.debug("[ThreadPoolHotUpdate] 配置中未找到 executor 节点, 跳过");
            return null;
        } catch (Exception e) {
            log.warn("[ThreadPoolHotUpdate] 配置解析失败: reason={}", e.getMessage());
            return null;
        }
    }

    /**
     * 应用 executor 配置变更到运行中的线程池。
     *
     * @param executorConfig executor 配置 JSON
     */
    private void applyExecutorConfigChanges(JSONObject executorConfig) {
        boolean changed = false;

        // 1. maxConcurrent — 动态调整全局线程池
        Integer newMaxConcurrent = executorConfig.getInteger("maxConcurrent");
        if (newMaxConcurrent != null && newMaxConcurrent > 0) {
            int oldMax = cronjobProperties.getExecutor().getMaxConcurrent();
            if (newMaxConcurrent != oldMax) {
                cronjobProperties.getExecutor().setMaxConcurrent(newMaxConcurrent);
                resizeGlobalThreadPool(newMaxConcurrent);
                changed = true;
                log.info("[ThreadPoolHotUpdate] maxConcurrent: {} -> {}", oldMax, newMaxConcurrent);
            }
        }

        // 2. queueCapacity — 记录新值（队列无法 resize，下次创建时生效）
        Integer newQueueCapacity = executorConfig.getInteger("queueCapacity");
        if (newQueueCapacity != null && newQueueCapacity >= 0) {
            int oldQueue = cronjobProperties.getExecutor().getQueueCapacity();
            if (newQueueCapacity != oldQueue) {
                cronjobProperties.getExecutor().setQueueCapacity(newQueueCapacity);
                changed = true;
                log.info("[ThreadPoolHotUpdate] queueCapacity: {} -> {} (下次创建线程池时生效)", oldQueue, newQueueCapacity);
            }
        }

        // 3. isolationStrategy — 变更时清空租户线程池缓存
        String newStrategy = executorConfig.getString("isolationStrategy");
        if (newStrategy != null && !newStrategy.isBlank()) {
            String oldStrategy = cronjobProperties.getExecutor().getIsolationStrategy();
            if (!newStrategy.equalsIgnoreCase(oldStrategy)) {
                cronjobProperties.getExecutor().setIsolationStrategy(newStrategy);
                tenantAwareExecutorPool.evictAllPools();
                changed = true;
                log.info("[ThreadPoolHotUpdate] isolationStrategy: {} -> {} (已清空旧隔离池)", oldStrategy, newStrategy);
            }
        }

        // 4. tenantPoolSize — 记录新值（已创建的隔离池需 evict 后重建）
        Integer newTenantPoolSize = executorConfig.getInteger("tenantPoolSize");
        if (newTenantPoolSize != null && newTenantPoolSize > 0) {
            int oldSize = cronjobProperties.getExecutor().getTenantPoolSize();
            if (newTenantPoolSize != oldSize) {
                cronjobProperties.getExecutor().setTenantPoolSize(newTenantPoolSize);
                tenantAwareExecutorPool.evictAllPools();
                changed = true;
                log.info("[ThreadPoolHotUpdate] tenantPoolSize: {} -> {} (已清空旧隔离池)", oldSize, newTenantPoolSize);
            }
        }

        // 5. tenantPoolQueueCapacity — 记录新值
        Integer newTenantQueueCap = executorConfig.getInteger("tenantPoolQueueCapacity");
        if (newTenantQueueCap != null && newTenantQueueCap >= 0) {
            int oldCap = cronjobProperties.getExecutor().getTenantPoolQueueCapacity();
            if (newTenantQueueCap != oldCap) {
                cronjobProperties.getExecutor().setTenantPoolQueueCapacity(newTenantQueueCap);
                tenantAwareExecutorPool.evictAllPools();
                changed = true;
                log.info("[ThreadPoolHotUpdate] tenantPoolQueueCapacity: {} -> {} (已清空旧隔离池)", oldCap, newTenantQueueCap);
            }
        }

        if (!changed) {
            log.debug("[ThreadPoolHotUpdate] 配置无变化, 跳过");
        } else {
            log.info("[ThreadPoolHotUpdate] 线程池热更新完成");
        }
    }

    /**
     * 动态调整全局执行线程池大小。
     *
     * <p>通过反射获取 DefaultTaskDispatcher 的 taskExecutorPool 字段，
     * 调用 setCorePoolSize / setMaximumPoolSize 实现运行时调整。
     *
     * @param newMaxConcurrent 新的最大并发数
     */
    private void resizeGlobalThreadPool(int newMaxConcurrent) {
        try {
            ThreadPoolExecutor pool = defaultTaskDispatcher.getTaskExecutorPool();
            if (pool == null) {
                log.warn("[ThreadPoolHotUpdate] 全局线程池未初始化, 跳过");
                return;
            }
            int newCore = Math.max(1, newMaxConcurrent);
            // 先扩大 max，再调整 core（避免 core > max 抛异常）
            if (newCore > pool.getMaximumPoolSize()) {
                pool.setMaximumPoolSize(newCore);
                pool.setCorePoolSize(newCore);
            } else {
                pool.setCorePoolSize(newCore);
                pool.setMaximumPoolSize(newCore);
            }
            log.info("[ThreadPoolHotUpdate] 全局线程池已调整: core={} max={} active={} queue={}",
                    pool.getCorePoolSize(), pool.getMaximumPoolSize(),
                    pool.getActiveCount(), pool.getQueue().size());
        } catch (Exception e) {
            log.error("[ThreadPoolHotUpdate] 调整全局线程池失败: reason={}", e.getMessage(), e);
        }
    }
}
