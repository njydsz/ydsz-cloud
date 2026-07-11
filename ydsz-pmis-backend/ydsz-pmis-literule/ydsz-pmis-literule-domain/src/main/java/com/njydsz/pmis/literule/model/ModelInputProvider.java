package com.njydsz.pmis.literule.domain.model;

import com.njydsz.pmis.literule.api.RuleContext;

import java.util.Map;

/**
 * 模型输入提供者 SPI（P3-1 规则+模型融合）
 *
 * <p>实现本接口可将外部模型（如风控评分模型、欺诈检测模型）的输出注入规则上下文，
 * 供规则表达式引用。在表达式中通过 {@code model.<fieldName>} 访问模型输出，
 * 例如 {@code model.riskScore > 0.8}、{@code model.fraudProbability < 0.1}。
 *
 * <p>对标滴滴 Newton、字节风控的"规则+模型融合"能力：
 * <ul>
 *   <li>规则兜底模型异常：模型不可用时降级为纯规则评估</li>
 *   <li>模型输出触发规则：模型输出作为规则条件输入，组合决策</li>
 * </ul>
 *
 * <h3>实现约束</h3>
 * <ul>
 *   <li>实现必须是线程安全的（多线程并发调用 {@link #getModelOutput}）</li>
 *   <li>单次调用应在配置的超时时间内完成（默认 100ms），超时将被丢弃</li>
 *   <li>抛出的异常将被 {@link ModelInputRegistry} 捕获，不影响其他 provider</li>
 *   <li>返回 {@code null} 或空 Map 视为该模型无输出</li>
 * </ul>
 *
 * <h3>注册方式</h3>
 * <ul>
 *   <li>Spring Bean：实现类标注 {@code @Component}，Spring 自动注入到 {@link ModelInputRegistry}</li>
 *   <li>手动注册：调用 {@link ModelInputRegistry#register}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
public interface ModelInputProvider {

    /**
     * 根据规则上下文获取模型输出
     *
     * <p>实现方可从 {@link RuleContext#getFacts()} 中读取特征数据，调用模型服务，
     * 返回模型输出。返回的 Map 的 key 为模型字段名（如 "riskScore"），
     * value 为数值/字符串/布尔等。
     *
     * <p>注意：返回的 key 不应包含 "model." 前缀，前缀由
     * {@link ModelInputRegistry#collectAllModelOutputs} 统一添加。
     *
     * @param context 规则上下文（含 facts）
     * @return 模型输出 Map；null 或空 Map 表示无输出
     */
    Map<String, Object> getModelOutput(RuleContext context);

    /**
     * 模型标识（如 "risk-model-v1"、"fraud-detection-v2"）
     *
     * <p>用于：
     * <ul>
     *   <li>通过 {@link ModelInputRegistry#getModelOutputs(String, RuleContext)} 定点获取</li>
     *   <li>日志与监控中区分不同模型的调用情况</li>
     * </ul>
     *
     * @return 模型标识；全局唯一
     */
    String getModelId();

    /**
     * 是否启用（可选，默认 true）
     *
     * <p>返回 false 时，{@link ModelInputRegistry} 将跳过该 provider，
     * 不调用 {@link #getModelOutput}。用于运行时灰度控制或临时禁用某模型。
     *
     * @return true=启用；false=禁用
     */
    default boolean isEnabled() {
        return true;
    }
}
