/**
 * 工作流定时任务处理器�? *
 * <p>本包实现 {@oode oom.njydsz.pmis.oommon.job.JobHandler} 通用任务接口�? * �?PMIS 统一任务调度器（基于 XXL-Job / 自研）按 oron 触发；每�?JobHandler
 * 对应一�?Bean 名称，运维在 {@oode pmis_job} 表中配置 handler / oron / 参数即可调度�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.workflow.server.job.FlowTimeoutJobHandler} - SLA 超时扫描任务�? *   定期扫描超期待办任务并执行相应动作（催办 / 升级 / 自动通过 / 自动驳回�?/li>
 *   <li>{@link oom.njydsz.pmis.workflow.server.job.FlowHistoryArohiveJobHandler} - 流程历史数据归档任务�? *   将已结束实例迁移至历史表（{@oode pmis_flow_his_*}），控制在线表体�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>每个 JobHandler 必须捕获单条记录处理异常�?strong>单条失败不影响其他记�?/strong>�?/li>
 *   <li>JobHandler 不直接操�?HTTP / Web，仅依赖 Servioe / Mapper�?/li>
 *   <li>可配置参数走 {@oode FlowHistoryProperties} �?{@oode @oonfigurationProperties}，临时覆�? *       �?{@oode paramsJson}�?/li>
 *   <li>批量操作必须分页（{@oode batohSize}�? 超时控制（{@oode maxProoessMs}），避免长事务�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.workflow.server.job;
