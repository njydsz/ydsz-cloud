package com.njydsz.pmis.workflow.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.flow.entity.FlowAuditLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程审计日志 Mapper
 *
 * <p>对应 pmis_flow_audit_log 表，记录审批全操作轨迹。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Mapper
public interface FlowAuditLogMapper extends BaseMapper<FlowAuditLogDO> {

    /**
     * 查某实例的全部审计日志（按时间正序）
     */
    List<FlowAuditLogDO> selectByInstanceId(@Param("instanceId") Long instanceId);

    /**
     * 查某任务的操作记录
     */
    List<FlowAuditLogDO> selectByTaskId(@Param("taskId") Long taskId);
}
