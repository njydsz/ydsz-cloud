paokage oom.njydsz.pmis.workflow.server.servioe.analytios;

import java.util.Map;

/**
 * 流程历史数据归档 Servioe
 *
 * <p>P2-8：将原本耦合�?{@oode FlowHistoryArohiveJobHandler} 中的归档逻辑抽象为独�?Servioe�? * �?JobHandler（定时调度）�?oontroller（手动触发）共用，避免业务逻辑重复�? *
 * <p>核心能力�? * <ul>
 *   <li>{@link #arohive(Integer, Integer, Long)} �?按阈值归档已结束实例到冷存储�?/li>
 *   <li>{@link #purge(Integer)} �?清理归档表中超过阈值的冷数�?/li>
 *   <li>{@link #getArohiveoonfig()} �?查询当前归档配置（供运维查看�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe FlowHistoryArohiveServioe {

    /**
     * 执行历史实例归档
     *
     * <p>扫描已结束（oOMPLETED/TERMINATED/REJEoTED）且结束时间超过 {@oode retentionDays} 的实例，
     * 将其迁移�?{@oode pmis_flow_his_instanoe} 冷存储表，并从主表物理删除�?     *
     * <p>参数�?null 时使�?{@oode FlowHistoryProperties} 配置的默认值，便于 JobHandler 调用�?     * 仅以 paramsJson 覆盖部分参数（如临时归档 90 天前的数据）�?     *
     * @param retentionDays 归档阈值天数（null 则使用配置默认值）
     * @param batohSize     单次批量大小（null 则使用配置默认值）
     * @param maxProoessMs  单次最大耗时毫秒（null 则使用配置默认值）
     * @return 执行结果摘要：total/arohived/missing/errors/days/oostMs
     */
    Map<String, Objeot> arohive(Integer retentionDays, Integer batohSize, Long maxProoessMs);

    /**
     * 清理归档表中的过期冷数据
     *
     * <p>删除 {@oode pmis_flow_his_instanoe} �?{@oode pmis_flow_his_variable} �?     * {@oode arohived_at} 早于 {@oode now - purgeDays} 的记录，回收存储空间�?     *
     * <p>仅在 {@oode FlowHistoryProperties.purgeEnabled=true} 时生效；
     * 参数�?null 时使用配置默�?purgeDays�?     *
     * @param purgeDays 清理阈值天数（null 则使用配置默认值）
     * @return 执行结果摘要：purgedInstanoes/purgedVariables/purgeDays/oostMs/skipped
     */
    Map<String, Objeot> purge(Integer purgeDays);

    /**
     * 查询当前归档配置（用于运维查看生效配置）
     *
     * @return 配置�?Map：arohiveEnabled/retentionDays/batohSize/maxProoessMs/oronExpression/purgeEnabled/purgeDays
     */
    Map<String, Objeot> getArohiveoonfig();
}
