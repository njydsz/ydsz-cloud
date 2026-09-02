package com.njydsz.cronjob.infra.mapper.job;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.cronjob.infra.entity.job.SysAuditLog;

/**
 * 操作审计日志 Mapper（P1-14 操作审计视图）。
 *
 * <p>对应 <code>ydsz_job_audit_log</code> 表（由 common-audit 管理）。
 * 本 Mapper 仅提供查询能力，写入由 common-audit 模块完成。
 *
 * <p>cronjob 模块仅关注 {@code module = 'cronjob'} 的记录，所有查询自动过滤。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {

  /** cronjob 模块标识 */
  String MODULE_CRONJOB = "cronjob";

  /**
   * 分页查询 cronjob 模块的操作审计日志。
   *
   * <p>支持按操作行为编码、操作人、时间范围过滤，按操作时间降序排列。
   *
   * @param module 模块名称（固定为 'cronjob'）
   * @param action 操作行为编码（null 表示不限）
   * @param operatorName 操作人姓名（null 表示不限）
   * @param startTime 开始时间（含，null 表示不限）
   * @param endTime 结束时间（含，null 表示不限）
   * @param limit 每页条数
   * @param offset 偏移量
   * @return 审计日志列表
   */
  @Select(
      "<script>"
          + "SELECT id, audit_type, action, status, module, content, business_no, "
          + "       operator_id, operator_name, operation_time, ip_address, "
          + "       request_params, response_result, errorMessage, cost_time, "
          + "       app_key, tenant_id, trace_id, created_at "
          + "FROM ydsz_job_audit_log "
          + "WHERE module = #{module} "
          + "<if test=\"action != null\"> AND action = #{action} </if> "
          + "<if test=\"operatorName != null and operatorName != ''\"> AND operator_name = #{operatorName} </if> "
          + "<if test=\"startTime != null\"> AND operation_time &gt;= #{startTime} </if> "
          + "<if test=\"endTime != null\"> AND operation_time &lt;= #{endTime} </if> "
          + "ORDER BY operation_time DESC "
          + "LIMIT #{limit} OFFSET #{offset}"
          + "</script>")
  List<SysAuditLog> selectCronjobAuditPage(
      @Param("module") String module,
      @Param("action") Integer action,
      @Param("operatorName") String operatorName,
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime,
      @Param("limit") int limit,
      @Param("offset") int offset);

  /**
   * 统计 cronjob 模块的操作审计日志总数（与 {@link #selectCronjobAuditPage} 同过滤条件）。
   *
   * @param module 模块名称（固定为 'cronjob'）
   * @param action 操作行为编码（null 表示不限）
   * @param operatorName 操作人姓名（null 表示不限）
   * @param startTime 开始时间（含，null 表示不限）
   * @param endTime 结束时间（含，null 表示不限）
   * @return 总条数
   */
  @Select(
      "<script>"
          + "SELECT COUNT(*) FROM ydsz_job_audit_log "
          + "WHERE module = #{module} "
          + "<if test=\"action != null\"> AND action = #{action} </if> "
          + "<if test=\"operatorName != null and operatorName != ''\"> AND operator_name = #{operatorName} </if> "
          + "<if test=\"startTime != null\"> AND operation_time &gt;= #{startTime} </if> "
          + "<if test=\"endTime != null\"> AND operation_time &lt;= #{endTime} </if> "
          + "</script>")
  long countCronjobAudit(
      @Param("module") String module,
      @Param("action") Integer action,
      @Param("operatorName") String operatorName,
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime);
}
