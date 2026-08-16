package com.njydsz.literule.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.literule.api.RuleContext;

/**
 * 模拟模型输入提供者（P3-1 规则+模型融合）
 *
 * <p>用于开发/测试环境，返回固定/可配置的模型输出，避免依赖真实模型服务。
 * 通过配置文件控制输出值，便于模拟不同模型评分场景下的规则触发情况。
 *
 * <h3>配置示例</h3>
 * <pre>
 * ydsz:
 *   literule:
 *     model:
 *       enabled: true
 *       mock-enabled: true
 *       mock-outputs:
 *         riskScore: 0.75
 *         fraudProbability: 0.05
 * </pre>
 *
 * <p>规则表达式可直接引用：{@code model.riskScore > 0.8}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class MockModelInputProvider implements ModelInputProvider {

    /** 默认模型 ID */
    public static final String DEFAULT_MODEL_ID = "mock-model-v1";

    /** 默认输出（用于无配置时） */
    private static final Map<String, Object> DEFAULT_OUTPUTS;

    static {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("riskScore", 0.75);
        defaults.put("fraudProbability", 0.05);
        DEFAULT_OUTPUTS = Collections.unmodifiableMap(defaults);
    }

    private final String modelId;
    private final Map<String, Object> outputs;

    /**
     * 构造 Mock Provider（使用默认模型 ID 与默认输出）
     */
    public MockModelInputProvider() {
        this(DEFAULT_MODEL_ID, DEFAULT_OUTPUTS);
    }

    /**
     * 构造 Mock Provider
     *
     * @param modelId 模型标识
     * @param outputs 模型输出（会被拷贝为只读 Map）
     */
    public MockModelInputProvider(String modelId, Map<String, Object> outputs) {
        this.modelId = modelId != null ? modelId : DEFAULT_MODEL_ID;
        this.outputs = outputs != null && !outputs.isEmpty()
                ? Collections.unmodifiableMap(new LinkedHashMap<>(outputs))
                : DEFAULT_OUTPUTS;
        log.info("[LiteRule-Model] MockModelInputProvider 已初始化: modelId={}, outputs={}",
                this.modelId, this.outputs);
    }

    @Override
    public Map<String, Object> getModelOutput(RuleContext context) {
        // 返回只读副本，避免被调用方修改
        return outputs;
    }

    @Override
    public String getModelId() {
        return modelId;
    }
}
