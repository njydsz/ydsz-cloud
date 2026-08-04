package com.remisoft.literule.api.spi;

import java.util.List;
import java.util.Map;

/**
 * DMN 决策表评估提供者 SPI
 *
 * <p>由消费方（如 project 模块）提供实现，按决策表编码加载并评估决策表，
 * 返回命中行的动作值列表。将原有 {@code DecisionTableEvalService} 的能力抽象为 SPI，
 * 避免 literule 模块直接依赖 project 模块。
 *
 * @since 1.0.0
 * @author remi-team
 */
public interface DecisionTableEvalProvider {

    /**
     * 评估决策表
     *
     * @param tableCode 决策表编码
     * @param facts     事实数据（变量名 -> 值）
     * @return 命中行的动作值列表；无匹配时返回默认动作或空列表
     */
    List<Map<String, Object>> evaluate(String tableCode, Map<String, Object> facts);
}
