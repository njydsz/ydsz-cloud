package com.njydsz.userinfo.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.alert.SecurityAlert;
import com.njydsz.userinfo.domain.alert.SecurityAlertRepository;
import com.njydsz.userinfo.domain.converter.SecurityAlertConverter;
import com.njydsz.userinfo.domain.entity.SecurityAlertEntity;
import com.njydsz.userinfo.domain.query.SecurityAlertPageQuery;
import com.njydsz.userinfo.infra.mapper.SecurityAlertMapper;

/**
 * 安全告警 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 实现 domain 层 {@link SecurityAlertRepository} 接口。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class SecurityAlertRepositoryImpl implements SecurityAlertRepository {

  private final SecurityAlertMapper securityAlertMapper;
  private final SecurityAlertConverter converter;

  @Override
  public SecurityAlert save(SecurityAlert alert) {
    SecurityAlertEntity entity = converter.domainToEntity(alert);
    if (alert.id() == null) {
      securityAlertMapper.insert(entity);
    } else {
      securityAlertMapper.updateById(entity);
    }
    return converter.entityToDomain(entity);
  }

  @Override
  public Optional<SecurityAlert> findById(String id) {
    SecurityAlertEntity entity = securityAlertMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToDomain);
  }

  @Override
  public PageResponse<List<SecurityAlert>> page(SecurityAlertPageQuery query) {
    int pageNum = query.getPageNum();
    int pageSize = query.getPageSize();
    Page<SecurityAlertEntity> page = new Page<>(pageNum, pageSize);
    LambdaQueryWrapper<SecurityAlertEntity> wrapper = new LambdaQueryWrapper<>();
    if (query.getAlertStatus() != null) {
      wrapper.eq(SecurityAlertEntity::getStatus, query.getAlertStatus().name());
    }
    if (query.getRiskLevel() != null) {
      wrapper.eq(SecurityAlertEntity::getRiskLevel, query.getRiskLevel().name());
    }
    if (query.effectiveStartTime() != null) {
      wrapper.ge(SecurityAlertEntity::getCreatedAt, query.effectiveStartTime());
    }
    if (query.effectiveEndTime() != null) {
      wrapper.le(SecurityAlertEntity::getCreatedAt, query.effectiveEndTime());
    }
    wrapper.orderByDesc(SecurityAlertEntity::getCreatedAt);
    Page<SecurityAlertEntity> result = securityAlertMapper.selectPage(page, wrapper);
    List<SecurityAlert> alerts = result.getRecords().stream()
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
      SecurityAlert.AlertType alertType,
      String userId,
      String sourceIp,
      LocalDateTime since) {
    LambdaQueryWrapper<SecurityAlertEntity> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(SecurityAlertEntity::getAlertType, alertType.name());
    if (userId != null) {
      wrapper.eq(SecurityAlertEntity::getUserId, userId);
    }
    if (sourceIp != null) {
      wrapper.eq(SecurityAlertEntity::getSourceIp, sourceIp);
    }
    if (since != null) {
      wrapper.ge(SecurityAlertEntity::getCreatedAt, since);
    }
    return securityAlertMapper.selectCount(wrapper);
  }

  @Override
  public boolean updateStatus(
      String id,
      SecurityAlert.AlertStatus status,
      String handlerNote) {
    SecurityAlertEntity entity = new SecurityAlertEntity();
    entity.setId(id);
    entity.setStatus(status.name());
    entity.setHandledAt(LocalDateTime.now());
    entity.setHandlerNote(handlerNote);
    return securityAlertMapper.updateById(entity) > 0;
  }

  @Override
  public List<SecurityAlert> findPendingAlerts(SecurityAlert.RiskLevel riskLevel, int limit) {
    LambdaQueryWrapper<SecurityAlertEntity> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(SecurityAlertEntity::getStatus, SecurityAlert.AlertStatus.PENDING.name());
    if (riskLevel != null) {
      wrapper.eq(SecurityAlertEntity::getRiskLevel, riskLevel.name());
    }
    wrapper.orderByDesc(SecurityAlertEntity::getCreatedAt);
    wrapper.last("LIMIT " + limit);
    List<SecurityAlertEntity> entities = securityAlertMapper.selectList(wrapper);
    return entities.stream()
        .map(converter::entityToDomain)
        .toList();
  }
}
