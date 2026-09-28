package com.njydsz.cronjob.server.service.audit;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.cronjob.domain.query.AuditLogQuery;
import com.njydsz.cronjob.domain.repository.AuditLogRepository;
import com.njydsz.cronjob.domain.vo.AuditLogVO;

/**
 * 审计日志服务实现（P1-14 操作审计视图）。
 *
 * <p>实现 {@link AuditLogService} 接口，提供 cronjob 模块操作审计日志的分页查询能力。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

  private final AuditLogRepository auditLogRepository;

  @Override
  public PageResponse<List<AuditLogVO>> page(AuditLogQuery query) {
    int offset = query.getOffset();
    int limit = query.getLimit();
    long total =
        auditLogRepository.countCronjobAudit(
            query.getAction(), query.getOperatorName(), query.getStartTime(), query.getEndTime());
    if (total == 0) {
      return PageResponse.empty((long) query.getPageNum(), (long) query.getPageSize());
    }
    List<AuditLogVO> records =
        auditLogRepository.selectCronjobAuditPage(
            query.getAction(),
            query.getOperatorName(),
            query.getStartTime(),
            query.getEndTime(),
            limit,
            offset);
    return PageResponse.success(
        (long) total, (long) query.getPageNum(), (long) query.getPageSize(), records);
  }
}
