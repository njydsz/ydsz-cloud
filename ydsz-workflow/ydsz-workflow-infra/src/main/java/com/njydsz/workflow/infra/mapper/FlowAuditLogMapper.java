package com.njydsz.workflow.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.workflow.infra.entity.FlowAuditLogDO;

/**
 * 流程审计日志 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_audit_log</code>，记录审批全操作轨迹。
 *
 * <p>审计日志是「不可变」的事实表（仅插入不更新/删除），用于安全审计/合规追溯/异常排查。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>idx_instance_id — 流程实例维度查询索引
 *   <li>idx_audit_at — 操作时间排序索引（按时间范围查询）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.infra.entity.FlowAuditLogDO 审计日志实体
 * @see com.njydsz.workflow.server.service.FlowAuditService 审计 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowAuditLogMapper extends BaseMapper<FlowAuditLogDO> {

  /** 查某实例的全部审计日志（按时间正序） */
  List<FlowAuditLogDO> selectByInstanceId(@Param("instanceId") String instanceId);

  /** 查某任务的操作记录 */
  List<FlowAuditLogDO> selectByTaskId(@Param("taskId") String taskId);

  /** 查某操作人的审计日志（P1-8: 加签历史查询） */
  List<FlowAuditLogDO> selectByOperatorId(@Param("operatorId") String operatorId);

  /** 查某目标人（转办/委派/加签目标）的审计日志（P1-8: 加签历史查询） */
  List<FlowAuditLogDO> selectByTargetId(@Param("targetId") String targetId);
}
