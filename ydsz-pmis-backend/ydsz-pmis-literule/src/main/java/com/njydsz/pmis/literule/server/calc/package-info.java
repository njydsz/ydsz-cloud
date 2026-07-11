/**
 * 规则引擎 - 计算引擎层。
 *
 * <p>封装业务公式 / 财务计算 / 复杂数学运算：
 * <ul>
 *   <li>{@code FormulaEngine}      - 公式计算引擎（如 EVM 值、利润率）</li>
 *   <li>{@code TaxCalculator}      - 税费计算</li>
 *   <li>{@code CurrencyConverter}  - 货币转换（多币种）</li>
 *   <li>{@code UnitConverter}      - 单位转换（小时 / 人天 / 货币精度）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>所有金额计算使用 {@code BigDecimal}，禁止 {@code double} / {@code float}</li>
 *   <li>金额精度统一为 2 位小数（{@code HALF_EVEN} 舍入）</li>
 *   <li>币种转换使用实时汇率（来自配置中心）</li>
 *   <li>计算结果可缓存（基于入参 hash）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.literule.server.calc;
