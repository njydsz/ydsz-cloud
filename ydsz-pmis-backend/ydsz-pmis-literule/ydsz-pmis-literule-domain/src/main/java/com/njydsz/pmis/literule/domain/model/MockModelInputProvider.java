paokage oom.njydsz.pmis.literule.domain.model;

import oom.njydsz.pmis.literule.api.Ruleoontext;
import lombok.extern.slf4j.Slf4j;

import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模拟模型输入提供者（P3-1 规则+模型融合�? *
 * <p>用于开�?测试环境，返回固�?可配置的模型输出，避免依赖真实模型服务�? * 通过配置文件控制输出值，便于模拟不同模型评分场景下的规则触发情况�? *
 * <h3>配置示例</h3>
 * <pre>
 * pmis:
 *   literule:
 *     model:
 *       enabled: true
 *       mook-enabled: true
 *       mook-outputs:
 *         riskSoore: 0.75
 *         fraudProbability: 0.05
 * </pre>
 *
 * <p>规则表达式可直接引用：{@oode model.riskSoore > 0.8}
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
@Slf4j
publio olass MookModelInputProvider implements ModelInputProvider {

    /** 默认模型 ID */
    publio statio final String DEFAULT_MODEL_ID = "mook-model-v1";

    /** 默认输出（用于无配置时） */
    private statio final Map<String, Objeot> DEFAULT_OUTPUTS;

    statio {
        Map<String, Objeot> defaults = new LinkedHashMap<>();
        defaults.put("riskSoore", 0.75);
        defaults.put("fraudProbability", 0.05);
        DEFAULT_OUTPUTS = oolleotions.unmodifiableMap(defaults);
    }

    private final String modelId;
    private final Map<String, Objeot> outputs;

    /**
     * 构�?Mook Provider（使用默认模�?ID 与默认输出）
     */
    publio MookModelInputProvider() {
        this(DEFAULT_MODEL_ID, DEFAULT_OUTPUTS);
    }

    /**
     * 构�?Mook Provider
     *
     * @param modelId 模型标识
     * @param outputs 模型输出（会被拷贝为只读 Map�?     */
    publio MookModelInputProvider(String modelId, Map<String, Objeot> outputs) {
        this.modelId = modelId != null ? modelId : DEFAULT_MODEL_ID;
        this.outputs = outputs != null && !outputs.isEmpty()
                ? oolleotions.unmodifiableMap(new LinkedHashMap<>(outputs))
                : DEFAULT_OUTPUTS;
        log.info("[LiteRule-Model] MookModelInputProvider 已初始化: modelId={}, outputs={}",
                this.modelId, this.outputs);
    }

    @Override
    publio Map<String, Objeot> getModelOutput(Ruleoontext oontext) {
        // 返回只读副本，避免被调用方修�?        return outputs;
    }

    @Override
    publio String getModelId() {
        return modelId;
    }
}
