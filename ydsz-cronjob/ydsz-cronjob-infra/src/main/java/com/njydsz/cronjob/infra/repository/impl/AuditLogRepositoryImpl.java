package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.job.SysAuditLog;
import com.njydsz.cronjob.domain.repository.AuditLogRepository;
import com.njydsz.cronjob.domain.vo.AuditLogVO;
import com.njydsz.cronjob.infra.mapper.job.SysAuditLogMapper;

/**
 * 审计日志 Repository 实现（P1-14 操作审计视图）。
 *
 * <p>实现 {@link AuditLogRepository} 接口，封装 ydsz_job_audit_log 表的查询操作。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class AuditLogRepositoryImpl implements AuditLogRepository {

  private final SysAuditLogMapper auditLogMapper;

  @Override
  public List<AuditLogVO> selectCronjobAuditPage(
      Integer action,
      String operatorName,
      LocalDateTime startTime,
      LocalDateTime endTime,
      int limit,
      int offset) {
    List<SysAuditLog> entities =
        auditLogMapper.selectCronjobAuditPage(
            SysAuditLogMapper.MODULE_CRONJOB, action, operatorName, startTime, endTime, limit, offset);
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }
    return entities.stream().map(this::entityToVo).toList();
  }

  @Override
  public long countCronjobAudit(
      Integer action,
      String operatorName,
      LocalDateTime startTime,
      LocalDateTime endTime) {
    return auditLogMapper.countCronjobAudit(
        SysAuditLogMapper.MODULE_CRONJOB, action, operatorName, startTime, endTime);
  }

  private AuditLogVO entityToVo(SysAuditLog entity) {
    AuditLogVO vo = new AuditLogVO();
    vo.setId(entity.getId());
    vo.setAuditType(entity.getAuditType());
    vo.setAction(entity.getAction());
    vo.setModule(entity.getModule());
    vo.setContent(entity.getContent());
    vo.setBusinessNo(entity.getBusinessNo());
    vo.setOperatorName(entity.getOperatorName());
    vo.setOperationTime(entity.getOperationTime());
    vo.setIpAddress(entity.getIpAddress());
    vo.setCostTime(entity.getCostTime());
    vo.setTraceId(entity.getTraceId());
    return vo;
  }
}
