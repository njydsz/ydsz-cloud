package com.njydsz.userinfo.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.alert.SecurityAlertRepository;
import com.njydsz.userinfo.domain.query.SecurityAlertPageQuery;
import com.njydsz.userinfo.infra.converter.SecurityAlertConverter;
import com.njydsz.userinfo.infra.mapper.SecurityAlertMapper;

/**
 * 安全告警 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 实现 domain 层 {@link SecurityAlertRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class SecurityAlertRepositoryImpl implements SecurityAlertRepository {

  private final SecurityAlertMapper securityAlertMapper;
  private final SecurityAlertConverter converter;

  @Override
  public com.njydsz.userinfo.domain.alert.SecurityAlert save(com.njydsz.userinfo.domain.alert.SecurityAlert alert) {
    com.njydsz.userinfo.infra.entity.SecurityAlert entity = converter.domainToEntity(alert);
    if (alert.id() == null) {
      securityAlertMapper.insert(entity);
    } else {
      securityAlertMapper.updateById(entity);
    }
    return converter.entityToDomain(entity);
  }

  @Override
  public Optional<com.njydsz.userinfo.domain.alert.SecurityAlert> findById(String id) {
    com.njydsz.userinfo.infra.entity.SecurityAlert entity = securityAlertMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToDomain);
  }

  @Override
  public PageResponse<List<com.njydsz.userinfo.domain.alert.SecurityAlert>> page(SecurityAlertPageQuery query) {
    int pageNum = query.getPageNum();
    int pageSize = query.getPageSize();
    Page<com.njydsz.userinfo.infra.entity.SecurityAlert> page = new Page<>(pageNum, pageSize);
    LambdaQueryWrapper<com.njydsz.userinfo.infra.entity.SecurityAlert> wrapper = new LambdaQueryWrapper<>();
    if (query.getAlertStatus() != null) {
      wrapper.eq(com.njydsz.userinfo.infra.entity.SecurityAlert::getStatus, query.getAlertStatus().name());
    }
    if (query.getRiskLevel() != null) {
      wrapper.eq(com.njydsz.userinfo.infra.entity.SecurityAlert::getRiskLevel, query.getRiskLevel().name());
    }
    if (query.effectiveStartTime() != null) {
      wrapper.ge(com.njydsz.userinfo.infra.entity.SecurityAlert::getCreatedAt, query.effectiveStartTime());
    }
    if (query.effectiveEndTime() != null) {
      wrapper.le(com.njydsz.userinfo.infra.entity.SecurityAlert::getCreatedAt, query.effectiveEndTime());
    }
    wrapper.orderByDesc(com.njydsz.userinfo.infra.entity.SecurityAlert::getCreatedAt);
    Page<com.njydsz.userinfo.infra.entity.SecurityAlert> result = securityAlertMapper.selectPage(page, wrapper);
    List<com.njydsz.userinfo.domain.alert.SecurityAlert> alerts = result.getRecords().stream()
        .map(converter::entityToDomain)
        .toList();
    return PageResponse.success(
        result.getTotal(),
        (long) pageNum,
        (long) pageSize,
        alerts);
  }

  @Override
  public long countRecentAlerts(
      com.njydsz.userinfo.domain.alert.SecurityAlert.AlertType alertType,
      String userId,
      String sourceIp,
      LocalDateTime since) {
    LambdaQueryWrapper<com.njydsz.userinfo.infra.entity.SecurityAlert> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(com.njydsz.userinfo.infra.entity.SecurityAlert::getAlertType, alertType.name());
    if (userId != null) {
      wrapper.eq(com.njydsz.userinfo.infra.entity.SecurityAlert::getUserId, userId);
    }
    if (sourceIp != null) {
      wrapper.eq(com.njydsz.userinfo.infra.entity.SecurityAlert::getSourceIp, sourceIp);
    }
    if (since != null) {
      wrapper.ge(com.njydsz.userinfo.infra.entity.SecurityAlert::getCreatedAt, since);
    }
    return securityAlertMapper.selectCount(wrapper);
  }

  @Override
  public boolean updateStatus(String id, com.njydsz.userinfo.domain.alert.SecurityAlert.AlertStatus status, String handlerNote) {
    com.njydsz.userinfo.infra.entity.SecurityAlert entity = new com.njydsz.userinfo.infra.entity.SecurityAlert();
    entity.setId(id);
    entity.setStatus(status.name());
    entity.setHandledAt(LocalDateTime.now());
    entity.setHandlerNote(handlerNote);
    return securityAlertMapper.updateById(entity) > 0;
  }

  @Override
  public List<com.njydsz.userinfo.domain.alert.SecurityAlert> findPendingAlerts(
      com.njydsz.userinfo.domain.alert.SecurityAlert.RiskLevel riskLevel, int limit) {
    LambdaQueryWrapper<com.njydsz.userinfo.infra.entity.SecurityAlert> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(
        com.njydsz.userinfo.infra.entity.SecurityAlert::getStatus,
        com.njydsz.userinfo.domain.alert.SecurityAlert.AlertStatus.PENDING.name());
    if (riskLevel != null) {
      wrapper.eq(com.njydsz.userinfo.infra.entity.SecurityAlert::getRiskLevel, riskLevel.name());
    }
    wrapper.orderByDesc(com.njydsz.userinfo.infra.entity.SecurityAlert::getCreatedAt);
    wrapper.last("LIMIT " + limit);
    List<com.njydsz.userinfo.infra.entity.SecurityAlert> entities = securityAlertMapper.selectList(wrapper);
    return entities.stream()
        .map(converter::entityToDomain)
        .toList();
  }
}
