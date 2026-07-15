package com.njydsz.pmis.literule.server.spi;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.njydsz.pmis.literule.api.RuleContext;

import lombok.extern.slf4j.Slf4j;

/**
 * 事实数据提供者注册中心（P0-2 动态事实采集管道）
 *
 * <p>管理所有 {@link FactProvider} 的注册/注销，并对外提供聚合查询能力。
 * 规则引擎在评估前调用 {@link #collectAllFacts} 获取全部事实数据，
 * 合并到 {@link RuleContext} 的 facts 中，使规则表达式可直接引用。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>线程安全：使用 {@link CopyOnWriteArrayList}，支持运行时动态注册/注销</li>
 *   <li>超时控制：每个 provider 调用受 {@link #timeoutMs} 限制，避免数据源延迟拖垮规则引擎</li>
 *   <li>异常隔离：单个 provider 异常/超时不影响其他 provider</li>
 *   <li>降级策略：{@link #fallbackOnError} 控制异常时的行为（继续 vs 中断）</li>
 *   <li>优先级排序：按 {@link FactProvider#getOrder()} 排序执行，前者输出可被后者读取</li>
 * </ul>
 *
 * <h3>与 ModelInputRegistry 的区别</h3>
 * <ul>
 *   <li>本注册中心采集业务事实数据，直接合并到 facts（无前缀）</li>
 *   <li>{@link com.njydsz.pmis.literule.domain.model.ModelInputRegistry} 采集模型输出，以 {@code model.} 前缀注入</li>
 *   <li>本注册中心在模型注入之前执行，采集的事实可供模型 provider 使用</li>
 * </ul>
 *
 * @since 2.1.0
 */
@Slf4j
public class FactProviderRegistry {

    /** 默认单个 provider 调用超时（毫秒） */
    public static final long DEFAULT_TIMEOUT_MS = 200L;

    /** 已注册的 provider 列表（线程安全，读多写少） */
    private final CopyOnWriteArrayList<FactProvider> providers = new CopyOnWriteArrayList<>();

    /** 单个 provider 调用超时（毫秒） */
    private final long timeoutMs;

    /** provider 异常时是否降级（true=继续评估，false=抛异常中断） */
    private final boolean fallbackOnError;

    /** 事实采集专用线程池 */
    private final ExecutorService executor;

    /** 是否由本实例管理线程池生命周期 */
    private final boolean ownsExecutor;

    /**
     * 构造注册中心（默认超时 200ms、降级开启）
     */
    public FactProviderRegistry() {
        this(DEFAULT_TIMEOUT_MS, true);
    }

    /**
     * 构造注册中心
     *
     * @param timeoutMs       单个 provider 调用超时（毫秒），<=0 表示不限制
     * @param fallbackOnError provider 异常时是否降级
     */
    public FactProviderRegistry(long timeoutMs, boolean fallbackOnError) {
        this.timeoutMs = timeoutMs;
        this.fallbackOnError = fallbackOnError;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "literule-fact-provider");
            t.setDaemon(true);
            return t;
        });
        this.ownsExecutor = true;
    }

    /**
     * 构造注册中心（使用外部线程池）
     *
     * @param timeoutMs       单个 provider 调用超时（毫秒）
     * @param fallbackOnError provider 异常时是否降级
     * @param executor        外部线程池
     */
    public FactProviderRegistry(long timeoutMs, boolean fallbackOnError, ExecutorService executor) {
        this.timeoutMs = timeoutMs;
        this.fallbackOnError = fallbackOnError;
        this.executor = executor;
        this.ownsExecutor = false;
    }

    /**
     * 注册 FactProvider
     *
     * @param provider 事实数据提供者；null 忽略
     */
    public void register(FactProvider provider) {
        if (provider == null) {
            return;
        }
        unregister(provider.getProviderId());
        providers.add(provider);
        log.info("[LiteRule-Fact] 注册 FactProvider: providerId={}, class={}, order={}",
                provider.getProviderId(), provider.getClass().getSimpleName(), provider.getOrder());
    }

    /**
     * 注销 FactProvider
     *
     * @param provider 待注销的提供者；null 忽略
     */
    public void unregister(FactProvider provider) {
        if (provider == null) {
            return;
        }
        if (providers.remove(provider)) {
            log.info("[LiteRule-Fact] 注销 FactProvider: providerId={}", provider.getProviderId());
        }
    }

    /**
     * 注销指定 providerId 的 provider
     *
     * @param providerId 提供者标识；null 忽略
     */
    public void unregister(String providerId) {
        if (providerId == null) {
            return;
        }
        providers.removeIf(p -> providerId.equals(p.getProviderId()));
    }

    /**
     * 获取已注册的 provider 数量
     *
     * @return provider 数量
     */
    public int size() {
        return providers.size();
    }

    /**
     * 是否已注册任何 provider
     *
     * @return true=注册表非空
     */
    public boolean hasProviders() {
        return !providers.isEmpty();
    }

    /**
     * 聚合所有已启用 provider 的事实数据
     *
     * <p>按 {@link FactProvider#getOrder()} 排序执行，前者输出会合并到上下文中
     * 供后续 provider 读取。同名字段后者覆盖前者。
     *
     * <p>异常处理：
     * <ul>
     *   <li>provider 抛异常或超时：记录 WARN，跳过该 provider</li>
     *   <li>{@code fallbackOnError=false} 时，任一 provider 失败将抛出 {@link FactCollectionException}</li>
     *   <li>provider {@link FactProvider#isEnabled()} 返回 false：跳过，不调用</li>
     * </ul>
     *
     * @param context 规则上下文（含已有 facts，provider 可读取）
     * @return 聚合后的事实 Map；无 provider 或全部失败返回空 Map
     */
    public Map<String, Object> collectAllFacts(RuleContext context) {
        if (providers.isEmpty()) {
            return Collections.emptyMap();
        }
        // 按 order 排序（不修改原列表）
        List<FactProvider> sorted = providers.stream()
                .sorted(Comparator.comparingInt(FactProvider::getOrder))
                .collect(Collectors.toList());

        Map<String, Object> aggregated = new LinkedHashMap<>();
        // 构建逐步增强的上下文（前一个 provider 的输出可供后续 provider 读取）
        Map<String, Object> progressiveFacts = new LinkedHashMap<>(context.getFacts());

        for (FactProvider provider : sorted) {
            if (!provider.isEnabled()) {
                if (log.isDebugEnabled()) {
                    log.debug("[LiteRule-Fact] Provider {} 已禁用，跳过", provider.getProviderId());
                }
                continue;
            }
            // 构建包含已采集事实的临时上下文
            RuleContext progressiveContext = RuleContext.of(
                    progressiveFacts,
                    context.getScenario(),
                    context.getSource(),
                    context.getTraceId(),
                    context.getTenantId(),
                    context.getEnvironment());

            Map<String, Object> output = safeInvoke(provider, progressiveContext);
            if (output != null && !output.isEmpty()) {
                aggregated.putAll(output);
                progressiveFacts.putAll(output);
            }
        }
        return aggregated;
    }

    /**
     * 获取指定 provider 的事实数据
     *
     * @param providerId 提供者标识
     * @param context    规则上下文
     * @return 事实数据 Map；不存在或失败返回空 Map
     */
    public Map<String, Object> getFacts(String providerId, RuleContext context) {
        if (providerId == null) {
            return Collections.emptyMap();
        }
        for (FactProvider provider : providers) {
            if (providerId.equals(provider.getProviderId())) {
                if (!provider.isEnabled()) {
                    return Collections.emptyMap();
                }
                return safeInvoke(provider, context);
            }
        }
        return Collections.emptyMap();
    }

    /**
     * 释放资源（关闭内部线程池）
     */
    public void destroy() {
        if (ownsExecutor && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("[LiteRule-Fact] 事实采集线程池已关闭");
        }
    }

    /**
     * 安全调用单个 provider（带超时与异常隔离）
     */
    private Map<String, Object> safeInvoke(FactProvider provider, RuleContext context) {
        Future<Map<String, Object>> future = null;
        try {
            future = executor.submit(() -> provider.getFacts(context));
            Map<String, Object> result;
            if (timeoutMs > 0) {
                result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                result = future.get();
            }
            return result == null ? Collections.emptyMap() : result;
        } catch (TimeoutException e) {
            if (future != null) {
                future.cancel(true);
            }
            log.warn("[LiteRule-Fact] Provider {} 调用超时（{}ms），已取消",
                    provider.getProviderId(), timeoutMs);
            if (!fallbackOnError) {
                throw new FactCollectionException(
                        "事实采集超时: " + provider.getProviderId() + " (" + timeoutMs + "ms)", e);
            }
            return Collections.emptyMap();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("[LiteRule-Fact] Provider {} 调用异常: {}",
                    provider.getProviderId(), cause.getMessage());
            if (!fallbackOnError) {
                throw new FactCollectionException(
                        "事实采集异常: " + provider.getProviderId(), cause);
            }
            return Collections.emptyMap();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[LiteRule-Fact] Provider {} 调用被中断", provider.getProviderId());
            if (!fallbackOnError) {
                throw new FactCollectionException(
                        "事实采集中断: " + provider.getProviderId(), e);
            }
            return Collections.emptyMap();
        }
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public boolean isFallbackOnError() {
        return fallbackOnError;
    }
}
