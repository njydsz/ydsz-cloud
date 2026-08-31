package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.vo.AuditLogVO;

/**
 * 审计日志 Repository 接口（P1-14 操作审计视图）。
 *
 * <p>封装 ydsz_job_audit_log 表的查询操作。仅提供读取能力，写入由 common-audit 模块完成。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AuditLogRepository {

  /**
   * 分页查询 cronjob 模块的审计日志。
   *
   * @param action 操作行为编码（null 表示不限）
   * @param operatorName 操作人姓名（null 表示不限）
   * @param startTime 开始时间（含，null 表示不限）
   * @param endTime 结束时间（含，null 表示不限）
   * @param limit 每页条数
   * @param offset 偏移量
   * @return 审计日志 VO 列表
   */
  List<AuditLogVO> selectCronjobAuditPage(
      Integer action,
      String operatorName,
      LocalDateTime startTime,
      LocalDateTime endTime,
      int limit,
      int offset);

  /**
   * 统计 cronjob 模块的审计日志总数。
   *
   * @param action 操作行为编码（null 表示不限）
   * @param operatorName 操作人姓名（null 表示不限）
   * @param startTime 开始时间（含，null 表示不限）
   * @param endTime 结束时间（含，null 表示不限）
   * @return 总条数
   */
  long countCronjobAudit(
      Integer action,
      String operatorName,
      LocalDateTime startTime,
      LocalDateTime endTime);
}
