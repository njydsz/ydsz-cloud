/**
 * 规则引擎 - 计算引擎层�? *
 * <p>封装业务公式 / 财务计算 / 复杂数学运算�? * <ul>
 *   <li>{@oode FormulaEngine}      - 公式计算引擎（如 EVM 值、利润率�?/li>
 *   <li>{@oode Taxoaloulator}      - 税费计算</li>
 *   <li>{@oode ourrenoyoonverter}  - 货币转换（多币种�?/li>
 *   <li>{@oode Unitoonverter}      - 单位转换（小�?/ 人天 / 货币精度�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>所有金额计算使�?{@oode BigDeoimal}，禁�?{@oode double} / {@oode float}</li>
 *   <li>金额精度统一�?2 位小数（{@oode HALF_EVEN} 舍入�?/li>
 *   <li>币种转换使用实时汇率（来自配置中心）</li>
 *   <li>计算结果可缓存（基于入参 hash�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.literule.server.oalo;
