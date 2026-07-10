/**
 * 工作流 MyBatis Mapper 层。
 *
 * <p>对应 {@code pmis_flow_*} 表族，全部继承 MyBatis-Plus {@code BaseMapper}，
 * 在此之上按业务场景扩展自定义 SQL。所有方法须有清晰的入参 / 出参 Javadoc，
 * 复杂 SQL 必须在 {@code class} 级别注明执行计划与索引命中情况。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>实例 - {@link com.njydsz.pmis.workflow.mapper.FlowInstanceMapper}、
 *       {@link com.njydsz.pmis.workflow.mapper.FlowHisInstanceMapper}</li>
 *   <li>任务 - {@link com.njydsz.pmis.workflow.mapper.FlowRunTaskMapper}、
 *       {@link com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper}、
 *       {@link com.njydsz.pmis.workflow.mapper.FlowCommentMapper}</li>
 *   <li>定义 / 节点 - {@link com.njydsz.pmis.workflow.mapper.FlowDefinitionMapper}、
 *       {@link com.njydsz.pmis.workflow.mapper.FlowNodeMapper}、
 *       {@link com.njydsz.pmis.workflow.mapper.FlowSkipMapper}、
 *       {@link com.njydsz.pmis.workflow.mapper.FlowTemplateMapper}</li>
 *   <li>DMN - {@link com.njydsz.pmis.workflow.mapper.FlowDmnTableMapper}</li>
 *   <li>抄送 / 委派 - {@link com.njydsz.pmis.workflow.mapper.FlowCcMapper}、
 *       {@link com.njydsz.pmis.workflow.mapper.FlowDelegateAuthMapper}</li>
 *   <li>三方对接 - {@link com.njydsz.pmis.workflow.mapper.FlowThirdPartyAccountMapper}、
 *       {@link com.njydsz.pmis.workflow.mapper.FlowThirdPartyLogMapper}</li>
 *   <li>自动化 / 监控 - {@link com.njydsz.pmis.workflow.mapper.FlowAutoTriggerMapper}、
 *       {@link com.njydsz.pmis.workflow.mapper.FlowTimerMapper}、
 *       {@link com.njydsz.pmis.workflow.mapper.FlowEventSubscriptionMapper}、
 *       {@link com.njydsz.pmis.workflow.mapper.FlowAuditLogMapper}、
 *       {@link com.njydsz.pmis.workflow.mapper.FlowUserMapper}</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>Mapper 方法名遵循 {@code selectXxx / updateXxx / insertXxx / deleteXxx / countXxx} 规范。</li>
 *   <li>多表关联查询需评估是否有外键 / 索引支持，禁止出现无索引的全表扫描。</li>
 *   <li>批量操作使用 {@code @Param("list")} 配合 {@code <foreach>}，单批次不超过 500。</li>
 *   <li>所有 Mapper 必须可单元测试（{@code @Sql} 注解或独立 SQL 脚本）。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.workflow.mapper;
