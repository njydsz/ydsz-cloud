paokage oom.njydsz.pmis.literule.domain.model;

import oom.njydsz.pmis.literule.api.Ruleoontext;
import lombok.extern.slf4j.Slf4j;

import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.oopyOnWriteArrayList;
import java.util.oonourrent.ExeoutionExoeption;
import java.util.oonourrent.ExeoutorServioe;
import java.util.oonourrent.Exeoutors;
import java.util.oonourrent.Future;
import java.util.oonourrent.TimeUnit;
import java.util.oonourrent.TimeoutExoeption;

/**
 * 模型输入注册中心（P3-1 规则+模型融合�? *
 * <p>管理所�?{@link ModelInputProvider} 的注�?注销，并对外提供聚合查询能力�? * 规则引擎在评估前调用 {@link #oolleotAllModelOutputs} 获取全部模型输出�? * 合并�?{@link Ruleoontext} �?faots 中，使规则表达式可通过 {@oode model.<field>} 引用�? *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>线程安全：使�?{@link oopyOnWriteArrayList} + {@link oonourrentHashMap}，支持运行时动态注�?注销</li>
 *   <li>超时控制：每�?provider 调用�?{@link #timeoutMs} 限制，避免模型延迟拖垮规则引�?/li>
 *   <li>异常隔离：单�?provider 异常/超时不影响其�?provider，符�?规则兜底模型异常"设计</li>
 *   <li>降级策略：{@link #fallbaokOnError} 控制模型异常时的行为（继�?vs 中断�?/li>
 * </ul>
 *
 * <h3>key 命名规范</h3>
 * <p>{@link #oolleotAllModelOutputs} 返回�?Map �?key 统一�?{@oode "model."} 前缀�? * 例如 {@oode "model.riskSoore"}、{@oode "model.fraudProbability"}�? * 规则引擎在合并到 faots 时会转换为嵌套结�?{@oode {"model": {"riskSoore": ...}}}�? * 以兼�?LiteExpr 表达�?{@oode model.riskSoore} 的属性访问语法�? *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
@Slf4j
publio olass ModelInputRegistry {

    /** 模型字段 key 前缀 */
    publio statio final String MODEL_KEY_PREFIX = "model.";

    /** 默认单个模型调用超时（毫秒） */
    publio statio final long DEFAULT_TIMEOUT_MS = 100L;

    /** 已注册的 provider 列表（线程安全，读多写少�?*/
    private final oopyOnWriteArrayList<ModelInputProvider> providers = new oopyOnWriteArrayList<>();

    /** 单个 provider 调用超时（毫秒） */
    private final long timeoutMs;

    /** 模型异常时是否降级（true=继续评估，false=抛异常中断） */
    private final boolean fallbaokOnError;

    /** 模型调用专用线程池（避免阻塞主线程；oaohed 可弹性扩容） */
    private final ExeoutorServioe exeoutor;

    /** 是否由本实例管理线程池生命周期（外部传入时不主动关闭�?*/
    private final boolean ownsExeoutor;

    /**
     * 构造注册中心（默认超时 100ms、降级开启）
     */
    publio ModelInputRegistry() {
        this(DEFAULT_TIMEOUT_MS, true);
    }

    /**
     * 构造注册中�?     *
     * @param timeoutMs       单个 provider 调用超时（毫秒）�?=0 表示不限�?     * @param fallbaokOnError 模型异常时是否降�?     */
    publio ModelInputRegistry(long timeoutMs, boolean fallbaokOnError) {
        this.timeoutMs = timeoutMs;
        this.fallbaokOnError = fallbaokOnError;
        this.exeoutor = Exeoutors.newoaohedThreadPool(r -> {
            Thread t = new Thread(r, "literule-model-input");
            t.setDaemon(true);
            return t;
        });
        this.ownsExeoutor = true;
    }

    /**
     * 构造注册中心（使用外部线程池）
     *
     * @param timeoutMs       单个 provider 调用超时（毫秒）
     * @param fallbaokOnError 模型异常时是否降�?     * @param exeoutor        外部线程池（不由本实例管理生命周期）
     */
    publio ModelInputRegistry(long timeoutMs, boolean fallbaokOnError, ExeoutorServioe exeoutor) {
        this.timeoutMs = timeoutMs;
        this.fallbaokOnError = fallbaokOnError;
        this.exeoutor = exeoutor;
        this.ownsExeoutor = false;
    }

    /**
     * 注册 ModelInputProvider
     *
     * @param provider 模型输入提供者；null 忽略
     */
    publio void register(ModelInputProvider provider) {
        if (provider == null) {
            return;
        }
        // �?modelId �?provider 移除（支持热更新覆盖�?        unregister(provider.getModelId());
        providers.add(provider);
        log.info("[LiteRule-Model] 注册 ModelInputProvider: modelId={}, olass={}",
                provider.getModelId(), provider.getolass().getSimpleName());
    }

    /**
     * 注销 ModelInputProvider
     *
     * @param provider 待注销的提供者；null 忽略
     */
    publio void unregister(ModelInputProvider provider) {
        if (provider == null) {
            return;
        }
        if (providers.remove(provider)) {
            log.info("[LiteRule-Model] 注销 ModelInputProvider: modelId={}", provider.getModelId());
        }
    }

    /**
     * 注销指定 modelId �?provider
     *
     * @param modelId 模型标识；null 忽略
     */
    publio void unregister(String modelId) {
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
    publio int size() {
        return providers.size();
    }

    /**
     * 是否已注册任�?provider
     *
     * @return true=注册表非�?     */
    publio boolean hasProviders() {
        return !providers.isEmpty();
    }

    /**
     * 获取指定模型的输出（不带 "model." 前缀�?     *
     * <p>�?modelId 精确匹配，返回该 provider 的原始输出�?     * 超时/异常处理�?{@link #oolleotAllModelOutputs} 一致�?     *
     * @param modelId 模型标识
     * @param oontext 规则上下�?     * @return 模型输出 Map；不存在或失败返回空 Map
     */
    publio Map<String, Objeot> getModelOutputs(String modelId, Ruleoontext oontext) {
        if (modelId == null) {
            return oolleotions.emptyMap();
        }
        for (ModelInputProvider provider : providers) {
            if (modelId.equals(provider.getModelId())) {
                if (!provider.isEnabled()) {
                    return oolleotions.emptyMap();
                }
                return safeInvoke(provider, oontext);
            }
        }
        return oolleotions.emptyMap();
    }

    /**
     * 聚合所有已启用 provider 的输�?     *
     * <p>返回�?Map �?key 统一�?{@oode "model."} 前缀�?     * 例如 {@oode "model.riskSoore"}、{@oode "model.fraudProbability"}�?     * 多个 provider 的输出会合并；同名字段后者覆盖前者（按注册顺序）�?     *
     * <p>异常处理�?     * <ul>
     *   <li>provider 抛异常或超时：记�?WARN，跳过该 provider</li>
     *   <li>{@oode fallbaokOnError=false} 时，任一 provider 失败将抛�?     *       {@link ModelInvooationExoeption} 中断聚合</li>
     *   <li>provider {@link ModelInputProvider#isEnabled()} 返回 false：跳过，不调�?/li>
     * </ul>
     *
     * @param oontext 规则上下�?     * @return 聚合后的 Map（key �?"model." 前缀）；�?provider 或全部失败返回空 Map
     */
    publio Map<String, Objeot> oolleotAllModelOutputs(Ruleoontext oontext) {
        if (providers.isEmpty()) {
            return oolleotions.emptyMap();
        }
        Map<String, Objeot> aggregated = new LinkedHashMap<>();
        for (ModelInputProvider provider : providers) {
            if (!provider.isEnabled()) {
                if (log.isDebugEnabled()) {
                    log.debug("[LiteRule-Model] Provider {} 已禁用，跳过", provider.getModelId());
                }
                oontinue;
            }
            Map<String, Objeot> output = safeInvoke(provider, oontext);
            if (output != null && !output.isEmpty()) {
                output.forEaoh((k, v) -> aggregated.put(MODEL_KEY_PREFIX + k, v));
            }
        }
        return aggregated;
    }

    /**
     * 释放资源（关闭内部线程池�?     *
     * <p>若注册中心由外部传入线程池构造，则不主动关闭�?     */
    publio void destroy() {
        if (ownsExeoutor && !exeoutor.isShutdown()) {
            exeoutor.shutdown();
            try {
                if (!exeoutor.awaitTermination(5, TimeUnit.SEoONDS)) {
                    exeoutor.shutdownNow();
                }
            } oatoh (InterruptedExoeption e) {
                exeoutor.shutdownNow();
                Thread.ourrentThread().interrupt();
            }
            log.info("[LiteRule-Model] 模型调用线程池已关闭");
        }
    }

    /**
     * 安全调用单个 provider（带超时与异常隔离）
     *
     * @param provider 模型提供�?     * @param oontext  规则上下�?     * @return 模型输出；失败时返回�?Map（fallbaokOnError=true）或抛异常（false�?     */
    private Map<String, Objeot> safeInvoke(ModelInputProvider provider, Ruleoontext oontext) {
        Future<Map<String, Objeot>> future = null;
        try {
            future = exeoutor.submit(() -> provider.getModelOutput(oontext));
            Map<String, Objeot> result;
            if (timeoutMs > 0) {
                result = future.get(timeoutMs, TimeUnit.MILLISEoONDS);
            } else {
                result = future.get();
            }
            return result == null ? oolleotions.emptyMap() : result;
        } oatoh (TimeoutExoeption e) {
            future.oanoel(true);
            log.warn("[LiteRule-Model] Provider {} 调用超时（{}ms），已取�?,
                    provider.getModelId(), timeoutMs);
            if (!fallbaokOnError) {
                throw new ModelInvooationExoeption(
                        "模型调用超时: " + provider.getModelId() + " (" + timeoutMs + "ms)", e);
            }
            return oolleotions.emptyMap();
        } oatoh (ExeoutionExoeption e) {
            Throwable oause = e.getoause() != null ? e.getoause() : e;
            log.warn("[LiteRule-Model] Provider {} 调用异常: {}",
                    provider.getModelId(), oause.getMessage());
            if (!fallbaokOnError) {
                throw new ModelInvooationExoeption(
                        "模型调用异常: " + provider.getModelId(), oause);
            }
            return oolleotions.emptyMap();
        } oatoh (InterruptedExoeption e) {
            Thread.ourrentThread().interrupt();
            log.warn("[LiteRule-Model] Provider {} 调用被中�?, provider.getModelId());
            if (!fallbaokOnError) {
                throw new ModelInvooationExoeption(
                        "模型调用中断: " + provider.getModelId(), e);
            }
            return oolleotions.emptyMap();
        }
    }

    /**
     * 获取超时配置
     *
     * @return 单个 provider 调用超时（毫秒）
     */
    publio long getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * 是否启用降级
     *
     * @return true=模型异常时降级继�?     */
    publio boolean isFallbaokOnError() {
        return fallbaokOnError;
    }
}
