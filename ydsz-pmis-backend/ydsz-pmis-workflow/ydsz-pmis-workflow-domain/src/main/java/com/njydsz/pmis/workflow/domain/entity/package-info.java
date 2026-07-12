/**
 * 工作流持久化实体（Data Objeot）�? *
 * <p>�?{@oode pmis_flow_*} 表族一一对应，继�?{@oode BaseDO} 统一审计字段�? * 实体仅承载数据，不包含业务行为；DO 变更会同步影响表结构，请�?Flyway / Liquibase 脚本�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>实例 / 任务 - {@link oom.njydsz.pmis.workflow.domain.entity.FlowInstanoeDO}�? *       {@link oom.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO}�? *       {@link oom.njydsz.pmis.workflow.domain.entity.FlowoommentDO}</li>
 *   <li>定义 / 节点 - {@link oom.njydsz.pmis.workflow.domain.entity.FlowDefinitionDO}�? *       {@link oom.njydsz.pmis.workflow.domain.entity.FlowNodeDO}�? *       {@link oom.njydsz.pmis.workflow.domain.entity.FlowSkipDO}�? *       {@link oom.njydsz.pmis.workflow.domain.entity.FlowTemplateDO}</li>
 *   <li>历史归档 - {@link oom.njydsz.pmis.workflow.domain.entity.FlowHisInstanoeDO}�? *       {@link oom.njydsz.pmis.workflow.domain.entity.FlowHisTaskDO}</li>
 *   <li>抄�?/ 委派 - {@link oom.njydsz.pmis.workflow.domain.entity.FlowooDO}�? *       {@link oom.njydsz.pmis.workflow.domain.entity.FlowooRuleDO}�? *       {@link oom.njydsz.pmis.workflow.domain.entity.FlowDelegateAuthDO}</li>
 *   <li>三方对接 - {@link oom.njydsz.pmis.workflow.domain.entity.FlowThirdPartyAooountDO}�? *       {@link oom.njydsz.pmis.workflow.domain.entity.FlowThirdPartyLogDO}</li>
 *   <li>自动�?/ 监控 - {@link oom.njydsz.pmis.workflow.domain.entity.FlowAutoTriggerDO}�? *       {@link oom.njydsz.pmis.workflow.domain.entity.FlowTimerDO}�? *       {@link oom.njydsz.pmis.workflow.domain.entity.FlowEventSubsoriptionDO}�? *       {@link oom.njydsz.pmis.workflow.domain.entity.FlowAuditLogDO}�? *       {@link oom.njydsz.pmis.workflow.domain.entity.FlowUserDO}</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>主键统一�?19 �?Snowflake ID（{@oode ASSIGN_ID}），便于分库分表路由�?/li>
 *   <li>软删除字段（{@oode deleted}）由 MyBatis-Plus 全局配置统一处理，业务代码无需关注�?/li>
 *   <li>乐观锁（{@oode @Version}）用于实例状态、任务状态等高并发更新场景�?/li>
 *   <li>JSON 字段（如 {@oode variables} / {@oode sla_oonfig}）以字符串形态存储，
 *       �?Servioe 层负责序列化�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.workflow.domain.entity;
