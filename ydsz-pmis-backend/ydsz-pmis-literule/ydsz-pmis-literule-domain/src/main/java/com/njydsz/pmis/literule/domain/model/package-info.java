/**
 * 规则+模型融合（P3-1）。
 *
 * <p>提供模型输出注入规则上下文的 SPI 与注册中心，使规则表达式可引用模型输出，
 * 实现规则兜底模型异常、模型输出触发规则的能力。对标滴滴 Newton、字节风控。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.literule.domain.model.ModelInputProvider} - 模型输入提供者 SPI</li>
 *   <li>{@link com.njydsz.pmis.literule.domain.model.ModelInputRegistry} - 模型注册中心（聚合/超时/降级）</li>
 *   <li>{@link com.njydsz.pmis.literule.domain.model.MockModelInputProvider} - 测试用 Mock 实现</li>
 *   <li>{@link com.njydsz.pmis.literule.domain.model.ModelInvocationException} - 模型调用异常</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <p>规则表达式中通过 {@code model.<field>} 引用模型输出：
 * <pre>
 *   model.riskScore &gt; 0.8           // 风险分高于 0.8 触发
 *   model.fraudProbability &lt; 0.1    // 欺诈概率低于 0.1 触发
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
package com.njydsz.pmis.literule.domain.model;
