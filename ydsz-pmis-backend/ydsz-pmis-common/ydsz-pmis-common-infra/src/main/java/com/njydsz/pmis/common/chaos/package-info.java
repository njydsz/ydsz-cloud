/**
 * 混沌工程层。
 *
 * <p>提供运行时注入故障（延迟 / 异常 / 熔断 / 资源占用等）的能力，用于在线上 / 预发环境演练
 * 系统韧性。本能力需在配置中心显式开启，关闭后所有切面为 no-op，对业务零开销。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.chaos.ChaosService}    - 故障注入服务（API）</li>
 *   <li>{@link com.njydsz.pmis.common.chaos.ChaosExperiment} - 故障实验定义（方法 / 延时 / 异常率）</li>
 *   <li>{@link com.njydsz.pmis.common.chaos.ChaosOutcome}    - 故障结果（命中 / 跳过 / 错误码）</li>
 *   <li>{@link com.njydsz.pmis.common.chaos.ChaosAutoConfiguration} - 自动配置（默认关闭，需显式启用）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.chaos;
