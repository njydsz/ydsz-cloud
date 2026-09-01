package com.njydsz.literule.server.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.server.model.ModelInputRegistry;
import com.njydsz.literule.domain.model.ModelInvocationException;
import com.njydsz.literule.server.spi.FactCollectionException;
import com.njydsz.literule.server.spi.FactProviderRegistry;

/**
 * 事实数据与模型输出注入服务
 *
 * <p>负责评估前的数据准备：
 *
 * <ul>
 *   <li>动态事实采集：从外部数据源（DB/Redis/HTTP）采集事实数据
 *   <li>模型输出注入：调用模型服务获取预测结果
 *   <li>并行优化：事实采集与模型调用并行执行，降低注入耗时
 * </ul>
 *
 * <p>降级策略：
 *
 * <ul>
 *   <li>注册表为空：返回原 context，不影响评估
 *   <li>数据为空：返回原 context
 *   <li>采集/调用失败（fallbackOnError=false）：异常向上传播中断评估
 * </ul>
 *
 * @since 1.4.0
 * @author ydsz-team
 */
@Slf4j
public class FactInjectionService {

    /** 模型输入注册表 */
    private final ModelInputRegistry modelInputRegistry;

    /** 事实数据提供者注册表 */
    private final FactProviderRegistry factProviderRegistry;

    /** 并行注入专用线程池 */
    private final ExecutorService injectionExecutor;

    /**
     * 构造事实注入服务
     *
     * @param modelInputRegistry 模型输入注册表（可为 null）
     * @param factProviderRegistry 事实数据提供者注册表（可为 null）
     * @param injectionExecutor 并行注入线程池
     */
    public FactInjectionService(
            ModelInputRegistry modelInputRegistry,
            FactProviderRegistry factProviderRegistry,
            ExecutorService injectionExecutor) {
        this.modelInputRegistry = modelInputRegistry;
        this.factProviderRegistry = factProviderRegistry;
        this.injectionExecutor = injectionExecutor;
    }

    /**
     * 并行注入事实数据与模型输出到上下文
     *
     * <p>当事实注册表和模型注册表均注册了 provider 时，使用 {@link CompletableFuture}
     * 并行执行两者，将注入耗时从 T_fact + T_model 降至 max(T_fact, T_model)。
     * 仅一方有 provider 时走串行路径（避免线程切换开销）。
     *
     * <p>注意：模型注入使用原始上下文（不依赖事实采集结果），两者结果独立合并。
     * 若业务上模型需要读取事实采集结果，应在 FactProviderRegistry 的 provider 内部调用模型服务。
     *
     * @param context 原始评估上下文
     * @return 合并后的上下文
     */
    public RuleContextVO injectDataInParallel(RuleContextVO context) {
        boolean hasFacts = factProviderRegistry != null && factProviderRegistry.hasProviders();
        boolean hasModels = modelInputRegistry != null && modelInputRegistry.hasProviders();

        // 两者都为空，直接返回
        if (!hasFacts && !hasModels) {
            return context;
        }

        // 仅事实注入，走串行（避免线程切换开销）
        if (hasFacts && !hasModels) {
            return injectFactsIfNeeded(context);
        }

        // 仅模型注入，走串行
        if (!hasFacts && hasModels) {
            return injectModelOutputsIfNeeded(context);
        }

        // 两者都有，并行执行
        CompletableFuture<Map<String, Object>> factsFuture =
                CompletableFuture.supplyAsync(
                        () -> collectFactsSafely(context), injectionExecutor);
        CompletableFuture<Map<String, Object>> modelFuture =
                CompletableFuture.supplyAsync(
                        () -> collectModelsSafely(context), injectionExecutor);

        // 等待两者完成（异常隔离：各自已在内部处理）
        Map<String, Object> externalFacts;
        Map<String, Object> modelOutputs;
        try {
            externalFacts = factsFuture.get();
            modelOutputs = modelFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[LiteRule] 并行注入被中断，降级为串行");
            return injectFactsThenModel(context);
        } catch (ExecutionException e) {
            log.warn("[LiteRule] 并行注入异常，降级为串行: {}", e.getMessage());
            return injectFactsThenModel(context);
        }

        return mergeInjectedData(context, externalFacts, modelOutputs);
    }

    /**
     * 串行降级路径：先事实后模型
     *
     * @param context 原始上下文
     * @return 合并后的上下文
     */
    private RuleContextVO injectFactsThenModel(RuleContextVO context) {
        RuleContextVO enriched = injectFactsIfNeeded(context);
        return injectModelOutputsIfNeeded(enriched);
    }

    /**
     * 安全采集事实数据（异常隔离）
     *
     * @param context 上下文
     * @return 事实数据 Map；异常时返回空 Map
     */
    private Map<String, Object> collectFactsSafely(RuleContextVO context) {
        try {
            return factProviderRegistry.collectAllFacts(context);
        } catch (FactCollectionException e) {
            log.warn("[LiteRule-Fact] 事实采集失败（fallbackOnError=false），中断评估: {}", e.getMessage());
            throw RuleEvaluationException.evaluationError("fact-collection", e);
        }
    }

    /**
     * 安全采集模型输出（异常隔离）
     *
     * @param context 上下文
     * @return 模型输出 Map；异常时返回空 Map
     */
    private Map<String, Object> collectModelsSafely(RuleContextVO context) {
        try {
            return modelInputRegistry.collectAllModelOutputs(context);
        } catch (ModelInvocationException e) {
            log.warn("[LiteRule-Model] 模型调用失败（fallbackOnError=false），中断评估: {}", e.getMessage());
            throw RuleEvaluationException.evaluationError("model-invocation", e);
        }
    }

    /**
     * 合并事实数据与模型输出到上下文
     *
     * @param context 原始上下文
     * @param externalFacts 外部事实数据
     * @param modelOutputs 模型输出
     * @return 合并后的上下文
     */
    private RuleContextVO mergeInjectedData(
            RuleContextVO context,
            Map<String, Object> externalFacts,
            Map<String, Object> modelOutputs) {
        boolean hasFacts = externalFacts != null && !externalFacts.isEmpty();
        boolean hasModels = modelOutputs != null && !modelOutputs.isEmpty();

        if (!hasFacts && !hasModels) {
            return context;
        }

        Map<String, Object> mergedFacts = new LinkedHashMap<>(context.getFacts());
        if (hasFacts) {
            mergedFacts.putAll(externalFacts);
        }
        if (hasModels) {
            // 扁平 key（"model.score"）转换为嵌套结构（{"model": {"score": ...}}）
            Map<String, Object> nestedModel = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : modelOutputs.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(ModelInputRegistry.MODEL_KEY_PREFIX)) {
                    nestedModel.put(
                            key.substring(ModelInputRegistry.MODEL_KEY_PREFIX.length()), entry.getValue());
                } else {
                    nestedModel.put(key, entry.getValue());
                }
            }
            if (!nestedModel.isEmpty()) {
                mergedFacts.put("model", nestedModel);
            }
        }

        return RuleContextVO.of(
                mergedFacts,
                context.getScenario(),
                context.getSource(),
                context.getTraceId(),
                context.getTenantId(),
                context.getEnvironment());
    }

    /**
     * 动态事实采集：评估前注入外部数据源事实
     *
     * <p>当事实注册表非 null 且已注册 provider 时：
     *
     * <ol>
     *   <li>调用 {@link FactProviderRegistry#collectAllFacts} 获取外部数据源事实
     *   <li>合并到 facts 中，构建新的 {@link RuleContextVO}
     * </ol>
     *
     * @param context 原始上下文
     * @return 包含外部事实的新上下文；无需注入时返回原 context
     */
    public RuleContextVO injectFactsIfNeeded(RuleContextVO context) {
        if (factProviderRegistry == null || !factProviderRegistry.hasProviders()) {
            return context;
        }
        Map<String, Object> externalFacts;
        try {
            externalFacts = factProviderRegistry.collectAllFacts(context);
        } catch (FactCollectionException e) {
            log.warn("[LiteRule-Fact] 事实采集失败（fallbackOnError=false），中断评估: {}", e.getMessage());
            throw e;
        }
        if (externalFacts == null || externalFacts.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("[LiteRule-Fact] 外部事实数据为空，使用原 context 评估");
            }
            return context;
        }
        // 合并到新 facts（原 facts + 外部事实，后者覆盖前者）
        Map<String, Object> mergedFacts = new LinkedHashMap<>(context.getFacts());
        mergedFacts.putAll(externalFacts);
        RuleContextVO enriched =
                RuleContextVO.of(
                        mergedFacts,
                        context.getScenario(),
                        context.getSource(),
                        context.getTraceId(),
                        context.getTenantId(),
                        context.getEnvironment());
        if (log.isDebugEnabled()) {
            log.debug(
                    "[LiteRule-Fact] 外部事实已注入: {} 条，合并后 facts 共 {} 条",
                    externalFacts.size(),
                    mergedFacts.size());
        }
        return enriched;
    }

    /**
     * 规则+模型融合：评估前注入模型输出
     *
     * <p>当模型注册表非 null 且已注册 provider 时：
     *
     * <ol>
     *   <li>调用 {@link ModelInputRegistry#collectAllModelOutputs} 获取模型输出
     *   <li>将扁平 key 转换为嵌套结构 {@code {"model": {"score": ..., ...}}}
     *   <li>合并到 facts 中，构建新的 {@link RuleContextVO}
     * </ol>
     *
     * @param context 原始上下文
     * @return 包含模型输出的新上下文；无需注入时返回原 context
     */
    public RuleContextVO injectModelOutputsIfNeeded(RuleContextVO context) {
        if (modelInputRegistry == null || !modelInputRegistry.hasProviders()) {
            return context;
        }
        Map<String, Object> modelOutputs;
        try {
            modelOutputs = modelInputRegistry.collectAllModelOutputs(context);
        } catch (ModelInvocationException e) {
            log.warn("[LiteRule-Model] 模型调用失败（fallbackOnError=false），中断评估: {}", e.getMessage());
            throw e;
        }
        if (modelOutputs == null || modelOutputs.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("[LiteRule-Model] 模型输出为空，降级为纯规则评估");
            }
            return context;
        }
        // 扁平 key（"model.score"）转换为嵌套结构（{"model": {"score": ...}}）
        Map<String, Object> nestedModel = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : modelOutputs.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(ModelInputRegistry.MODEL_KEY_PREFIX)) {
                nestedModel.put(
                        key.substring(ModelInputRegistry.MODEL_KEY_PREFIX.length()), entry.getValue());
            } else {
                // 非 "model." 前缀的 key 直接保留（兼容扩展场景）
                nestedModel.put(key, entry.getValue());
            }
        }
        if (nestedModel.isEmpty()) {
            return context;
        }
        // 合并到新 facts（保留原 facts + 添加 model 嵌套 Map）
        Map<String, Object> mergedFacts = new LinkedHashMap<>(context.getFacts());
        mergedFacts.put("model", nestedModel);
        RuleContextVO enriched =
                RuleContextVO.of(
                        mergedFacts,
                        context.getScenario(),
                        context.getSource(),
                        context.getTraceId(),
                        context.getTenantId(),
                        context.getEnvironment());
        if (log.isDebugEnabled()) {
            log.debug("[LiteRule-Model] 模型输出已注入: fields={}", nestedModel.keySet());
        }
        return enriched;
    }

    /**
     * 销毁服务，释放资源
     */
    public void destroy() {
        if (modelInputRegistry != null) {
            modelInputRegistry.destroy();
        }
        if (factProviderRegistry != null) {
            factProviderRegistry.destroy();
        }
    }
}
