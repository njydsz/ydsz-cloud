/**
 * 规则引擎 - 内置实现层。
 *
 * <p>提供 literule 模块所有"内置默认实现"，业务方无感知地复用：
 * <ul>
 *   <li>内置函数（{@code now()} / {@code dateAdd()} / {@code round()} / {@code contains()} 等）</li>
 *   <li>内置操作符（IN / BETWEEN / LIKE / REGEX）</li>
 *   <li>内置规则模板（预算告警模板 / 合同金额校验模板）</li>
 *   <li>内置规则集（按业务域预置）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>内置实现遵循与业务实现相同的 SPI 契约</li>
 *   <li>内置函数 / 模板可通过 {@code spring.factories} 或 {@code META-INF/spring/...} 注册</li>
 *   <li>业务方可覆盖内置实现（通过 {@code @Primary} 或自定义 SPI）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.literule.impl;
