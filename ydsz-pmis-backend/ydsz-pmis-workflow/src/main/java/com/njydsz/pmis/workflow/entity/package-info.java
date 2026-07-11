/**
 * 工作流持久化实体（Data Object）。
 *
 * <p>与 {@code pmis_flow_*} 表族一一对应，继承 {@code BaseDO} 统一审计字段。
 * 实体仅承载数据，不包含业务行为；DO 变更会同步影响表结构，请走 Flyway / Liquibase 脚本。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>实例 / 任务 - {@link com.njydsz.pmis.workflow.entity.FlowInstanceDO}、
 *       {@link com.njydsz.pmis.workflow.entity.FlowRunTaskDO}、
 *       {@link com.njydsz.pmis.workflow.entity.FlowCommentDO}</li>
 *   <li>定义 / 节点 - {@link com.njydsz.pmis.workflow.entity.FlowDefinitionDO}、
 *       {@link com.njydsz.pmis.workflow.entity.FlowNodeDO}、
 *       {@link com.njydsz.pmis.workflow.entity.FlowSkipDO}、
 *       {@link com.njydsz.pmis.workflow.entity.FlowTemplateDO}</li>
 *   <li>历史归档 - {@link com.njydsz.pmis.workflow.entity.FlowHisInstanceDO}、
 *       {@link com.njydsz.pmis.workflow.entity.FlowHisTaskDO}</li>
 *   <li>抄送 / 委派 - {@link com.njydsz.pmis.workflow.entity.FlowCcDO}、
 *       {@link com.njydsz.pmis.workflow.entity.FlowCcRuleDO}、
 *       {@link com.njydsz.pmis.workflow.entity.FlowDelegateAuthDO}</li>
 *   <li>三方对接 - {@link com.njydsz.pmis.workflow.entity.FlowThirdPartyAccountDO}、
 *       {@link com.njydsz.pmis.workflow.entity.FlowThirdPartyLogDO}</li>
 *   <li>自动化 / 监控 - {@link com.njydsz.pmis.workflow.entity.FlowAutoTriggerDO}、
 *       {@link com.njydsz.pmis.workflow.entity.FlowTimerDO}、
 *       {@link com.njydsz.pmis.workflow.entity.FlowEventSubscriptionDO}、
 *       {@link com.njydsz.pmis.workflow.entity.FlowAuditLogDO}、
 *       {@link com.njydsz.pmis.workflow.entity.FlowUserDO}</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>主键统一为 19 位 Snowflake ID（{@code ASSIGN_ID}），便于分库分表路由。</li>
 *   <li>软删除字段（{@code deleted}）由 MyBatis-Plus 全局配置统一处理，业务代码无需关注。</li>
 *   <li>乐观锁（{@code @Version}）用于实例状态、任务状态等高并发更新场景。</li>
 *   <li>JSON 字段（如 {@code variables} / {@code sla_config}）以字符串形态存储，
 *       由 Service 层负责序列化。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.workflow.entity;
