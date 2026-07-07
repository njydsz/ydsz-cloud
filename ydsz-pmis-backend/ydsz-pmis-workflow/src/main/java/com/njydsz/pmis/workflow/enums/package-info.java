/**
 * 工作流领域枚举。
 *
 * <p>统一管理流程引擎运行期所需的全部枚举值，包括状态、类型、策略等。
 * 所有枚举均提供 {@code of(...)} 静态方法做安全转换，避免业务层散落魔数。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.workflow.enums.FlowInstanceStatus} - 流程实例状态（运行中 / 挂起 / 完成 / 终止 / 驳回 / 异常 / 回滚）</li>
 *   <li>{@link com.njydsz.pmis.workflow.enums.FlowTaskStatus} - 任务状态（待办 / 已签收 / 已完成 / 已驳回 / 已转办 / 超时）</li>
 *   <li>{@link com.njydsz.pmis.workflow.enums.FlowNodeType} - 节点类型（开始 / 结束 / 用户任务 / 服务任务 / 排他网关 / 并行网关 / 包容网关 / 子流程）</li>
 *   <li>{@link com.njydsz.pmis.workflow.enums.FlowPerformType} - 会签类型（OR / SEQUENTIAL / PARALLEL / VOTE）</li>
 *   <li>{@link com.njydsz.pmis.workflow.enums.FlowAssigneeType} - 办理人类型（用户 / 部门 / 角色 / 变量 / 表单字段 / 自选）</li>
 *   <li>{@link com.njydsz.pmis.workflow.enums.FlowSignType} - 签收类型（自动签收 / 手动签收）</li>
 *   <li>{@link com.njydsz.pmis.workflow.enums.FlowSkipType} - 跳转类型（同意 / 驳回 / 跳转 / 终止）</li>
 *   <li>{@link com.njydsz.pmis.workflow.enums.FlowSlaAction} - SLA 超时动作（提醒 / 升级 / 自动通过 / 自动驳回）</li>
 *   <li>{@link com.njydsz.pmis.workflow.enums.CanaryStatus} / {@link com.njydsz.pmis.workflow.enums.CanaryStrategy} - 灰度状态与策略</li>
 *   <li>{@link com.njydsz.pmis.workflow.enums.ThirdPartyPlatform} - 三方平台（企微 / 钉钉 / 飞书）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>枚举值与数据库 {@code VARCHAR} 字段直接对应，新增值必须走 Flyway 兼容性评估。</li>
 *   <li>提供 {@code isFinished()} / {@code isTerminal()} 等业务判定方法，避免业务层散落 if/else。</li>
 *   <li>对外 i18n Key 由消息中心统一管理，本包不直接持有文案。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.workflow.enums;
