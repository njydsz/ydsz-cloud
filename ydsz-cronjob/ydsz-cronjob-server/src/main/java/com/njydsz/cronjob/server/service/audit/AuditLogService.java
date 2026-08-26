package com.njydsz.cronjob.server.service.audit;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.cronjob.domain.vo.AuditLogVO;

/**
 * 审计日志服务接口（P1-14 操作审计视图）。
 *
 * <p>提供 cronjob 模块操作审计日志的查询能力，支持分页、时间范围、操作类型过滤。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AuditLogService {

  /**
   * 分页查询 cronjob 模块的操作审计日志。
   *
   * @param pageNum 页码（从 1 开始）
   * @param size 每页条数
   * @param action 操作行为编码（可选）
   * @param operatorName 操作人姓名（可选）
   * @param startTime 开始时间（可选）
   * @param endTime 结束时间（可选）
   * @return 分页结果
   */
  PageResponse<List<AuditLogVO>> page(
      int pageNum,
      int size,
      Integer action,
      String operatorName,
      LocalDateTime startTime,
      LocalDateTime endTime);
}
