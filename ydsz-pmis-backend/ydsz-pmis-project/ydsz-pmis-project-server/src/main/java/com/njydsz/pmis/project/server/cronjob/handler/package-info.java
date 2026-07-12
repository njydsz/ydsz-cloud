/**
 * 项目业务模块内置 JobHandler 实现。
 *
 * <p>本子包落地"项目模块特有"的 JobHandler 类，必须实现 {@code com.njydsz.pmis.common.job.JobHandler}
 * 接口，Bean 名称 = {@code pmis_job.handler} 字段值，供调度平台反射调度。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.server.cronjob.handler.BillableUtilizationJobHandler} - 可计费利用率重算（每日 02:30）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>入参可序列化</b>：接收的 params 必须是 JSON 字符串，便于跨节点透传</li>
 *   <li><b>可独立重跑</b>：提供 {@code recomputeAll} 等强制重算参数以支持运维手工补数</li>
 *   <li><b>结果可观测</b>：执行结果写入日志、调用次数埋点至 Micrometer，失败时触发 RED 告警</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>JobHandler 中禁止调用 Thread.sleep 等阻塞操作</li>
 *   <li>长任务必须分批处理并记录进度，避免单次执行超过 10 分钟</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.server.cronjob.handler;
