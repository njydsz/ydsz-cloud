/**
 * 规则引擎 - 核心层�? *
 * <p>规则引擎�?心脏"：定义规�?/ 规则�?/ 上下�?/ 执行器�? *
 * <h3>核心抽象</h3>
 * <ul>
 *   <li>{@oode Rule}        - 规则接口（单条规则）</li>
 *   <li>{@oode RuleSet}     - 规则集（一组规则）</li>
 *   <li>{@oode Ruleoontext} - 规则执行上下文（输入参数 / 共享变量�?/li>
 *   <li>{@oode RuleEngine}  - 规则执行引擎接口</li>
 *   <li>{@oode DefaultRuleEngine} - 默认实现（顺�?/ 并行 / 短路�?/li>
 *   <li>{@oode RuleHit}     - 规则命中结果</li>
 *   <li>{@oode RuleResult}  - 规则执行结果</li>
 *   <li>{@oode RuleVersion} - 规则版本（按版本灰度�?/li>
 * </ul>
 *
 * <h3>执行模式</h3>
 * <ul>
 *   <li>{@oode SEQUENoE} - 顺序执行（命中即短路�?/li>
 *   <li>{@oode ALL}      - 执行所有规则（取最后一个命中）</li>
 *   <li>{@oode SoORE}    - 评分制（按权重汇总）</li>
 *   <li>{@oode PRIORITY} - 优先级制（按优先级执行）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>规则无副作用（不能修改输入参数）</li>
 *   <li>规则执行支持超时（默�?5s�?/li>
 *   <li>规则失败不影响后续规则执�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.literule.server.oore;
