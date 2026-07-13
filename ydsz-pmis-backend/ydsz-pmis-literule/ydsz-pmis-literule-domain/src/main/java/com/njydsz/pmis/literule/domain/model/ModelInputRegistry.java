package com.njydsz.pmis.literule.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.njydsz.pmis.literule.api.RuleContext;

import lombok.extern.slf4j.Slf4j;

/**
 * 模型输入注册中心（P3-1 规则+模型融合）
 *
 * <p>管理所有 {@link ModelInputProvider} 的注册/注销，并对外提供聚合查询能力。
 * 规则引擎在评估前调用 {@link #collectAllModelOutputs} 获取全部模型输出，
 * 合并到 {@link RuleContext} 的 facts 中，使规则表达式可通过 {@code model.<field>} 引用。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>线程安全：使用 {@link CopyOnWriteArrayList} + {@link ConcurrentHashMap}，支持运行时动态注册/注销</li>
 *   <li>超时控制：每个 provider 调用受 {@link #timeoutMs} 限制，避免模型延迟拖垮规则引擎</li>
 *   <li>异常隔离：单个 provider 异常/超时不影响其他 provider，符合"规则兜底模型异常"设计</li>
 *   <li>降级策略：{@link #fallbackOnError} 控制模型异常时的行为（继续 vs 中断）</li>
 * </ul>
 *
 * <h3>key 命名规范</h3>
 * <p>{@link #collectAllModelOutputs} 返回的 Map 的 key 统一带 {@code "model."} 前缀，
 * 例如 {@code "model.riskScore"}、{@code "model.fraudProbability"}。
 * 规则引擎在合并到 facts 时会转换为嵌套结构 {@code {"model": {"riskScore": ...}}}，
 * 以兼容 LiteExpr 表达式 {@code model.riskScore} 的属性访问语法。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Slf4j
public class ModelInputRegistry {

    /** 模型字段 key 前缀 */
    public static final String MODEL_KEY_PREFIX = "model.";

    /** 默认单个模型调用超时（毫秒） */
    public static final long DEFAULT_TIMEOUT_MS = 100L;

    /** 已注册的 provider 列表（线程安全，读多写少） */
    private final CopyOnWriteArrayList<ModelInputProvider> providers = new CopyOnWriteArrayList<>();

    /** 单个 provider 调用超时（毫秒） */
    private final long timeoutMs;

    /** 模型异常时是否降级（true=继续评估，false=抛异常中断） */
    private final boolean fallbackOnError;

    /** 模型调用专用线程池（避免阻塞主线程；cached 可弹性扩容） */
    private final ExecutorService executor;

    /** 是否由本实例管理线程池生命周期（外部传入时不主动关闭） */
    private final boolean ownsExecutor;

    /**
     * 构造注册中心（默认超时 100ms、降级开启）
     */
    public ModelInputRegistry() {
        this(DEFAULT_TIMEOUT_MS, true);
    }

    /**
     * 构造注册中心
     *
     * @param timeoutMs       单个 provider 调用超时（毫秒），<=0 表示不限制
     * @param fallbackOnError 模型异常时是否降级
     */
    public ModelInputRegistry(long timeoutMs, boolean fallbackOnError) {
        this.timeoutMs = timeoutMs;
        this.fallbackOnError = fallbackOnError;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "literule-model-input");
            t.setDaemon(true);
            return t;
        });
        this.ownsExecutor = true;
    }

    /**
     * 构造注册中心（使用外部线程池）
     *
     * @param timeoutMs       单个 provider 调用超时（毫秒）
     * @param fallbackOnError 模型异常时是否降级
     * @param executor        外部线程池（不由本实例管理生命周期）
     */
    public ModelInputRegistry(long timeoutMs, boolean fallbackOnError, ExecutorService executor) {
        this.timeoutMs = timeoutMs;
        this.fallbackOnError = fallbackOnError;
        this.executor = executor;
        this.ownsExecutor = false;
    }

    /**
     * 注册 ModelInputProvider
     *
     * @param provider 模型输入提供者；null 忽略
     */
    public void register(ModelInputProvider provider) {
        if (provider == null) {
            return;
        }
        // 同 modelId 旧 provider 移除（支持热更新覆盖）
        unregister(provider.getModelId());
        providers.add(provider);
        log.info("[LiteRule-Model] 注册 ModelInputProvider: modelId={}, class={}",
                provider.getModelId(), provider.getClass().getSimpleName());
    }

    /**
     * 注销 ModelInputProvider
     *
     * @param provider 待注销的提供者；null 忽略
     */
    public void unregister(ModelInputProvider provider) {
        if (provider == null) {
            return;
        }
        if (providers.remove(provider)) {
            log.info("[LiteRule-Model] 注销 ModelInputProvider: modelId={}", provider.getModelId());
        }
    }

    /**
     * 注销指定 modelId 的 provider
     *
     * @param modelId 模型标识；null 忽略
     */
    public void unregister(String modelId) {
        if (modelId == null) {
            return;
        }
        providers.removeIf(p -> modelId.equals(p.getModelId()));
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
     * 获取指定模型的输出（不带 "model." 前缀）
     *
     * <p>按 modelId 精确匹配，返回该 provider 的原始输出。
     * 超时/异常处理与 {@link #collectAllModelOutputs} 一致。
     *
     * @param modelId 模型标识
     * @param context 规则上下文
     * @return 模型输出 Map；不存在或失败返回空 Map
     */
    public Map<String, Object> getModelOutputs(String modelId, RuleContext context) {
        if (modelId == null) {
            return Collections.emptyMap();
        }
        for (ModelInputProvider provider : providers) {
            if (modelId.equals(provider.getModelId())) {
                if (!provider.isEnabled()) {
                    return Collections.emptyMap();
                }
                return safeInvoke(provider, context);
            }
        }
        return Collections.emptyMap();
    }

    /**
     * 聚合所有已启用 provider 的输出
     *
     * <p>返回的 Map 的 key 统一带 {@code "model."} 前缀，
     * 例如 {@code "model.riskScore"}、{@code "model.fraudProbability"}。
     * 多个 provider 的输出会合并；同名字段后者覆盖前者（按注册顺序）。
     *
     * <p>异常处理：
     * <ul>
     *   <li>provider 抛异常或超时：记录 WARN，跳过该 provider</li>
     *   <li>{@code fallbackOnError=false} 时，任一 provider 失败将抛出
     *       {@link ModelInvocationException} 中断聚合</li>
     *   <li>provider {@link ModelInputProvider#isEnabled()} 返回 false：跳过，不调用</li>
     * </ul>
     *
     * @param context 规则上下文
     * @return 聚合后的 Map（key 带 "model." 前缀）；无 provider 或全部失败返回空 Map
     */
    public Map<String, Object> collectAllModelOutputs(RuleContext context) {
        if (providers.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> aggregated = new LinkedHashMap<>();
        for (ModelInputProvider provider : providers) {
            if (!provider.isEnabled()) {
                if (log.isDebugEnabled()) {
                    log.debug("[LiteRule-Model] Provider {} 已禁用，跳过", provider.getModelId());
                }
                continue;
            }
            Map<String, Object> output = safeInvoke(provider, context);
            if (output != null && !output.isEmpty()) {
                output.forEach((k, v) -> aggregated.put(MODEL_KEY_PREFIX + k, v));
            }
        }
        return aggregated;
    }

    /**
     * 释放资源（关闭内部线程池）
     *
     * <p>若注册中心由外部传入线程池构造，则不主动关闭。
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
            log.info("[LiteRule-Model] 模型调用线程池已关闭");
        }
    }

    /**
     * 安全调用单个 provider（带超时与异常隔离）
     *
     * @param provider 模型提供者
     * @param context  规则上下文
     * @return 模型输出；失败时返回空 Map（fallbackOnError=true）或抛异常（false）
     */
    private Map<String, Object> safeInvoke(ModelInputProvider provider, RuleContext context) {
        Future<Map<String, Object>> future = null;
        try {
            future = executor.submit(() -> provider.getModelOutput(context));
            Map<String, Object> result;
            if (timeoutMs > 0) {
                result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                result = future.get();
            }
            return result == null ? Collections.emptyMap() : result;
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("[LiteRule-Model] Provider {} 调用超时（{}ms），已取消",
                    provider.getModelId(), timeoutMs);
            if (!fallbackOnError) {
                throw new ModelInvocationException(
                        "模型调用超时: " + provider.getModelId() + " (" + timeoutMs + "ms)", e);
            }
            return Collections.emptyMap();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("[LiteRule-Model] Provider {} 调用异常: {}",
                    provider.getModelId(), cause.getMessage());
            if (!fallbackOnError) {
                throw new ModelInvocationException(
                        "模型调用异常: " + provider.getModelId(), cause);
            }
            return Collections.emptyMap();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[LiteRule-Model] Provider {} 调用被中断", provider.getModelId());
            if (!fallbackOnError) {
                throw new ModelInvocationException(
                        "模型调用中断: " + provider.getModelId(), e);
            }
            return Collections.emptyMap();
        }
    }

    /**
     * 获取超时配置
     *
     * @return 单个 provider 调用超时（毫秒）
     */
    public long getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * 是否启用降级
     *
     * @return true=模型异常时降级继续
     */
    public boolean isFallbackOnError() {
        return fallbackOnError;
    }
}
