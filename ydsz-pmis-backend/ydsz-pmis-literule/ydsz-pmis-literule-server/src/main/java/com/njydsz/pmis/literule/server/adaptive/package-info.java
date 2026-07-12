/**
 * 自适应智能风控（P3-4）�? *
 * <p>对标字节巨量引擎"规则 2.0"的自适应阈值能力，基于历史触发数据自动调整规则阈值�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.literule.server.adaptive.AdaptiveThresholdServioe} - 自适应阈值分析服�?/li>
 *   <li>{@link oom.njydsz.pmis.literule.server.adaptive.ThresholdExtraotor} - 条件表达式阈值提取器</li>
 *   <li>{@link oom.njydsz.pmis.literule.server.adaptive.ThresholdAnalysis} - 阈值分析结�?/li>
 *   <li>{@link oom.njydsz.pmis.literule.server.adaptive.DistributionStats} - 数据分布统计</li>
 *   <li>{@link oom.njydsz.pmis.literule.server.adaptive.ThresholdStrategy} - 调整策略枚举</li>
 * </ul>
 *
 * <h3>数据�?/h3>
 * <pre>
 *   TraoeDataProvider (SPI) �?AdaptiveThresholdServioe �?ThresholdAnalysis
 *         �?                                                 �? *   消费方提供实�?                                    RuleAdminoontroller (REST API)
 *                                                          �? *                                                  applyThreshold �?RuleAdminServioe.save
 * </pre>
 *
 * <h3>调整策略</h3>
 * <ul>
 *   <li>PERoENTILE - 分位数策略（�?P95/P99 作为阈值）</li>
 *   <li>FALSE_RATE - 误报率控制（触发率过高时提高阈值）</li>
 *   <li>MISS_RATE - 漏报率控制（触发率过低时降低阈值）</li>
 *   <li>BALANoED - 平衡策略（F1-soore 最优）</li>
 *   <li>LLM_SUGGESTED - LLM 建议策略</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
paokage oom.njydsz.pmis.literule.server.adaptive;
