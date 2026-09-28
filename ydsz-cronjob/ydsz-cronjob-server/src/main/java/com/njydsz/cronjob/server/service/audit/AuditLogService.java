package com.njydsz.cronjob.server.service.audit;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.cronjob.domain.query.AuditLogQuery;
import com.njydsz.cronjob.domain.vo.AuditLogVO;

/**
 * 审计日志服务接口（P1-14 操作审计视图）。
 *
 * <p>提供 cronjob 模块操作审计日志的查询能力，支持分页、时间范围、操作类型过滤。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface AuditLogService {

  /**
   * 分页查询 cronjob 模块的操作审计日志。
   *
   * @param query 分页查询参数（含 pageNum/pageSize/过滤条件）
   * @return 分页结果
   */
  PageResponse<List<AuditLogVO>> page(AuditLogQuery query);
}
