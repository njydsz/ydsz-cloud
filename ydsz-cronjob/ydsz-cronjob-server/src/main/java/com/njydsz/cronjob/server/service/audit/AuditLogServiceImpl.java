package com.njydsz.cronjob.server.service.audit;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.response.PageResponse;
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
  public PageResponse<List<AuditLogVO>> page(
      int pageNum,
      int size,
      Integer action,
      String operatorName,
      LocalDateTime startTime,
      LocalDateTime endTime) {
    // 计算偏移量
    int offset = (Math.max(pageNum, 1) - 1) * size;
    // 查询总数
    long total = auditLogRepository.countCronjobAudit(action, operatorName, startTime, endTime);
    if (total == 0) {
      return PageResponse.empty((long) pageNum, (long) size);
    }
    // 查询分页数据
    List<AuditLogVO> records =
        auditLogRepository.selectCronjobAuditPage(
            action, operatorName, startTime, endTime, size, offset);
    return PageResponse.success((long) total, (long) pageNum, (long) size, records);
  }
}
