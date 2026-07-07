/**
 * 对账引擎层。
 *
 * <p>通用对账框架：业务方通过实现 {@link com.njydsz.pmis.common.reconcile.ReconcileHandler} 接入，
 * 由 {@link com.njydsz.pmis.common.reconcile.ReconcileEngine} 统一调度执行差异比对、结果落库、告警通知。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.reconcile.ReconcileHandler} - 业务对账处理器 SPI</li>
 *   <li>{@link com.njydsz.pmis.common.reconcile.ReconcileEngine}  - 对账引擎（并行调度 / 失败重试）</li>
 *   <li>{@link com.njydsz.pmis.common.reconcile.ReconcileResult}  - 对账结果（差异明细 / 差异金额 / 差错率）</li>
 * </ul>
 *
 * <h3>典型场景</h3>
 * <ul>
 *   <li>支付对账：内部系统 vs 银行流水</li>
 *   <li>订单对账：业务订单 vs 第三方支付订单</li>
 *   <li>项目对账：内部台账 vs ERP / 财务系统</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.reconcile;
