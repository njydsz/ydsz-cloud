package com.njydsz.workflow.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.workflow.domain.entity.FlowAuditLog;

/**
 * 流程审计日志 Mapper
 *
 * <p>对应 ydsz_flow_audit_log 表，记录审批全操作轨迹。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface FlowAuditLogMapper extends BaseMapper<FlowAuditLog> {

    /**
     * 查某实例的全部审计日志（按时间正序）
     */
    List<FlowAuditLog> selectByInstanceId(@Param("instanceId") String instanceId);

    /**
     * 查某任务的操作记录
     */
    List<FlowAuditLog> selectByTaskId(@Param("taskId") String taskId);

    /**
     * 查某操作人的审计日志（P1-8: 加签历史查询）
     */
    List<FlowAuditLog> selectByOperatorId(@Param("operatorId") String operatorId);

    /**
     * 查某目标人（转办/委派/加签目标）的审计日志（P1-8: 加签历史查询）
     */
    List<FlowAuditLog> selectByTargetId(@Param("targetId") String targetId);
}
