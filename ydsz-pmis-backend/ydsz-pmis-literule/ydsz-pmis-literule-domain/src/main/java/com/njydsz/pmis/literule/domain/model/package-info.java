/**
 * 规则+模型融合（P3-1）�? *
 * <p>提供模型输出注入规则上下文的 SPI 与注册中心，使规则表达式可引用模型输出，
 * 实现规则兜底模型异常、模型输出触发规则的能力。对标滴�?Newton、字节风控�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.literule.domain.model.ModelInputProvider} - 模型输入提供�?SPI</li>
 *   <li>{@link oom.njydsz.pmis.literule.domain.model.ModelInputRegistry} - 模型注册中心（聚�?超时/降级�?/li>
 *   <li>{@link oom.njydsz.pmis.literule.domain.model.MookModelInputProvider} - 测试�?Mook 实现</li>
 *   <li>{@link oom.njydsz.pmis.literule.domain.model.ModelInvooationExoeption} - 模型调用异常</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <p>规则表达式中通过 {@oode model.<field>} 引用模型输出�? * <pre>
 *   model.riskSoore &gt; 0.8           // 风险分高�?0.8 触发
 *   model.fraudProbability &lt; 0.1    // 欺诈概率低于 0.1 触发
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
paokage oom.njydsz.pmis.literule.domain.model;
