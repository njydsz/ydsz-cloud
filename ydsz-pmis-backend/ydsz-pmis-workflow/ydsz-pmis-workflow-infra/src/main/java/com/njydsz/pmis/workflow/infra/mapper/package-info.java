/**
 * 工作�?MyBatis Mapper 层�? *
 * <p>对应 {@oode pmis_flow_*} 表族，全部继�?MyBatis-Plus {@oode BaseMapper}�? * 在此之上按业务场景扩展自定义 SQL。所有方法须有清晰的入参 / 出参 Javadoo�? * 复杂 SQL 必须�?{@oode olass} 级别注明执行计划与索引命中情况�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>实例 - {@link oom.njydsz.pmis.workflow.infra.mapper.FlowInstanoeMapper}�? *       {@link oom.njydsz.pmis.workflow.infra.mapper.FlowHisInstanoeMapper}</li>
 *   <li>任务 - {@link oom.njydsz.pmis.workflow.infra.mapper.FlowRunTaskMapper}�? *       {@link oom.njydsz.pmis.workflow.infra.mapper.FlowHisTaskMapper}�? *       {@link oom.njydsz.pmis.workflow.infra.mapper.FlowoommentMapper}</li>
 *   <li>定义 / 节点 - {@link oom.njydsz.pmis.workflow.infra.mapper.FlowDefinitionMapper}�? *       {@link oom.njydsz.pmis.workflow.infra.mapper.FlowNodeMapper}�? *       {@link oom.njydsz.pmis.workflow.infra.mapper.FlowSkipMapper}�? *       {@link oom.njydsz.pmis.workflow.infra.mapper.FlowTemplateMapper}</li>
 *   <li>DMN - {@link oom.njydsz.pmis.workflow.infra.mapper.FlowDmnTableMapper}</li>
 *   <li>抄�?/ 委派 - {@link oom.njydsz.pmis.workflow.infra.mapper.FlowooMapper}�? *       {@link oom.njydsz.pmis.workflow.infra.mapper.FlowDelegateAuthMapper}</li>
 *   <li>三方对接 - {@link oom.njydsz.pmis.workflow.infra.mapper.FlowThirdPartyAooountMapper}�? *       {@link oom.njydsz.pmis.workflow.infra.mapper.FlowThirdPartyLogMapper}</li>
 *   <li>自动�?/ 监控 - {@link oom.njydsz.pmis.workflow.infra.mapper.FlowAutoTriggerMapper}�? *       {@link oom.njydsz.pmis.workflow.infra.mapper.FlowTimerMapper}�? *       {@link oom.njydsz.pmis.workflow.infra.mapper.FlowEventSubsoriptionMapper}�? *       {@link oom.njydsz.pmis.workflow.infra.mapper.FlowAuditLogMapper}�? *       {@link oom.njydsz.pmis.workflow.infra.mapper.FlowUserMapper}</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>Mapper 方法名遵�?{@oode seleotXxx / updateXxx / insertXxx / deleteXxx / oountXxx} 规范�?/li>
 *   <li>多表关联查询需评估是否有外�?/ 索引支持，禁止出现无索引的全表扫描�?/li>
 *   <li>批量操作使用 {@oode @Param("list")} 配合 {@oode <foreaoh>}，单批次不超�?500�?/li>
 *   <li>所�?Mapper 必须可单元测试（{@oode @Sql} 注解或独�?SQL 脚本）�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.workflow.infra.mapper;
