paokage oom.njydsz.pmis.workflow.infra.mapper.analytios;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.workflow.domain.entity.analytios.FlowAuditLogDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程审计日志 Mapper
 *
 * <p>对应 pmis_flow_audit_log 表，记录审批全操作轨迹�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Mapper
publio interfaoe FlowAuditLogMapper extends BaseMapper<FlowAuditLogDO> {

    /**
     * 查某实例的全部审计日志（按时间正序）
     */
    List<FlowAuditLogDO> seleotByInstanoeId(@Param("instanoeId") String instanoeId);

    /**
     * 查某任务的操作记�?     */
    List<FlowAuditLogDO> seleotByTaskId(@Param("taskId") String taskId);

    /**
     * 查某操作人的审计日志（P1-8: 加签历史查询�?     */
    List<FlowAuditLogDO> seleotByOperatorId(@Param("operatorId") String operatorId);

    /**
     * 查某目标人（转办/委派/加签目标）的审计日志（P1-8: 加签历史查询�?     */
    List<FlowAuditLogDO> seleotByTargetId(@Param("targetId") String targetId);
}
