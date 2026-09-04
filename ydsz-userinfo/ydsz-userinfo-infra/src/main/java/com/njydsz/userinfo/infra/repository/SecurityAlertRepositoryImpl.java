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
import com.njydsz.userinfo.domain.converter.SecurityAlertConverter;
import com.njydsz.userinfo.infra.mapper.SecurityAlertMapper;

/**
 * 安全告警 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 实现 domain 层 {@link SecurityAlertRepository} 接口。
 * 因 domain 聚合 {@code SecurityAlert} 与 infra 实体 {@code SecurityAlert} 同名冲突，
 * 依据规范 5.4 节，两者均以行内 FQN 引用并附 FQN-OK 注释。
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
  public com.njydsz.userinfo.domain.alert.SecurityAlert save( // FQN-OK: name conflict with SecurityAlert
      com.njydsz.userinfo.domain.alert.SecurityAlert alert) { // FQN-OK: name conflict with SecurityAlert
    com.njydsz.userinfo.infra.entity.SecurityAlert entity = converter.domainToEntity(alert); // FQN-OK: name conflict with SecurityAlert
    if (alert.id() == null) {
      securityAlertMapper.insert(entity);
    } else {
      securityAlertMapper.updateById(entity);
    }
    return converter.entityToDomain(entity);
  }

  @Override
  public Optional<com.njydsz.userinfo.domain.alert.SecurityAlert> findById(String id) { // FQN-OK: name conflict with SecurityAlert
    com.njydsz.userinfo.infra.entity.SecurityAlert entity = securityAlertMapper.selectById(id); // FQN-OK: name conflict with SecurityAlert
    return Optional.ofNullable(entity).map(converter::entityToDomain);
  }

  @Override
  public PageResponse<List<com.njydsz.userinfo.domain.alert.SecurityAlert>> page( // FQN-OK: name conflict with SecurityAlert
      SecurityAlertPageQuery query) {
    int pageNum = query.getPageNum();
    int pageSize = query.getPageSize();
    Page<com.njydsz.userinfo.infra.entity.SecurityAlert> page = new Page<>(pageNum, pageSize); // FQN-OK: name conflict with SecurityAlert
    LambdaQueryWrapper<com.njydsz.userinfo.infra.entity.SecurityAlert> wrapper = // FQN-OK: name conflict with SecurityAlert
        new LambdaQueryWrapper<>();
    if (query.getAlertStatus() != null) {
      wrapper.eq(com.njydsz.userinfo.infra.entity.SecurityAlert::getStatus, // FQN-OK: name conflict with SecurityAlert
          query.getAlertStatus().name());
    }
    if (query.getRiskLevel() != null) {
      wrapper.eq(com.njydsz.userinfo.infra.entity.SecurityAlert::getRiskLevel, // FQN-OK: name conflict with SecurityAlert
          query.getRiskLevel().name());
    }
    if (query.effectiveStartTime() != null) {
      wrapper.ge(com.njydsz.userinfo.infra.entity.SecurityAlert::getCreatedAt, // FQN-OK: name conflict with SecurityAlert
          query.effectiveStartTime());
    }
    if (query.effectiveEndTime() != null) {
      wrapper.le(com.njydsz.userinfo.infra.entity.SecurityAlert::getCreatedAt, // FQN-OK: name conflict with SecurityAlert
          query.effectiveEndTime());
    }
    wrapper.orderByDesc(com.njydsz.userinfo.infra.entity.SecurityAlert::getCreatedAt); // FQN-OK: name conflict with SecurityAlert
    Page<com.njydsz.userinfo.infra.entity.SecurityAlert> result = // FQN-OK: name conflict with SecurityAlert
        securityAlertMapper.selectPage(page, wrapper);
    List<com.njydsz.userinfo.domain.alert.SecurityAlert> alerts = result.getRecords().stream() // FQN-OK: name conflict with SecurityAlert
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
      com.njydsz.userinfo.domain.alert.SecurityAlert.AlertType alertType, // FQN-OK: name conflict with SecurityAlert
      String userId,
      String sourceIp,
      LocalDateTime since) {
    LambdaQueryWrapper<com.njydsz.userinfo.infra.entity.SecurityAlert> wrapper = // FQN-OK: name conflict with SecurityAlert
        new LambdaQueryWrapper<>();
    wrapper.eq(com.njydsz.userinfo.infra.entity.SecurityAlert::getAlertType, alertType.name()); // FQN-OK: name conflict with SecurityAlert
    if (userId != null) {
      wrapper.eq(com.njydsz.userinfo.infra.entity.SecurityAlert::getUserId, userId); // FQN-OK: name conflict with SecurityAlert
    }
    if (sourceIp != null) {
      wrapper.eq(com.njydsz.userinfo.infra.entity.SecurityAlert::getSourceIp, sourceIp); // FQN-OK: name conflict with SecurityAlert
    }
    if (since != null) {
      wrapper.ge(com.njydsz.userinfo.infra.entity.SecurityAlert::getCreatedAt, since); // FQN-OK: name conflict with SecurityAlert
    }
    return securityAlertMapper.selectCount(wrapper);
  }

  @Override
  public boolean updateStatus(
      String id,
      com.njydsz.userinfo.domain.alert.SecurityAlert.AlertStatus status, // FQN-OK: name conflict with SecurityAlert
      String handlerNote) {
    var entity = new com.njydsz.userinfo.infra.entity.SecurityAlert(); // FQN-OK: name conflict with SecurityAlert
    entity.setId(id);
    entity.setStatus(status.name());
    entity.setHandledAt(LocalDateTime.now());
    entity.setHandlerNote(handlerNote);
    return securityAlertMapper.updateById(entity) > 0;
  }

  @Override
  public List<com.njydsz.userinfo.domain.alert.SecurityAlert> findPendingAlerts( // FQN-OK: name conflict with SecurityAlert
      com.njydsz.userinfo.domain.alert.SecurityAlert.RiskLevel riskLevel, int limit) { // FQN-OK: name conflict with SecurityAlert
    LambdaQueryWrapper<com.njydsz.userinfo.infra.entity.SecurityAlert> wrapper = // FQN-OK: name conflict with SecurityAlert
        new LambdaQueryWrapper<>();
    wrapper.eq(
        com.njydsz.userinfo.infra.entity.SecurityAlert::getStatus, // FQN-OK: name conflict with SecurityAlert
        com.njydsz.userinfo.domain.alert.SecurityAlert.AlertStatus.PENDING.name()); // FQN-OK: name conflict with SecurityAlert
    if (riskLevel != null) {
      // FQN-OK: name conflict with SecurityAlert
    wrapper.eq(com.njydsz.userinfo.infra.entity.SecurityAlert::getRiskLevel, riskLevel.name());
    }
    wrapper.orderByDesc(com.njydsz.userinfo.infra.entity.SecurityAlert::getCreatedAt); // FQN-OK: name conflict with SecurityAlert
    wrapper.last("LIMIT " + limit);
    List<com.njydsz.userinfo.infra.entity.SecurityAlert> entities = // FQN-OK: name conflict with SecurityAlert
        securityAlertMapper.selectList(wrapper);
    return entities.stream()
        .map(converter::entityToDomain)
        .toList();
  }
}
