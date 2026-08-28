package com.njydsz.userinfo.infra.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.alert.SecurityAlert.AlertStatus;
import com.njydsz.userinfo.domain.alert.SecurityAlert.AlertType;
import com.njydsz.userinfo.domain.alert.SecurityAlert.RiskLevel;

/**
 * 安全告警 MapStruct 转换器。
 *
 * <p>提供 SecurityAlert ↔ SecurityAlert 的转换方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper(componentModel = "spring")
@Component
public interface SecurityAlertConverter {

  /**
   * 安全告警实体 → 领域模型。
   *
   * @param entity 安全告警实体
   * @return 安全告警领域模型
   */
  @Mapping(target = "id", source = "id")
  @Mapping(target = "alertType",
      expression = "java(AlertType.valueOf(entity.getAlertType()))")
  @Mapping(target = "riskLevel",
      expression = "java(RiskLevel.valueOf(entity.getRiskLevel()))")
  @Mapping(target = "userId", source = "userId")
  @Mapping(target = "username", source = "username")
  @Mapping(target = "sourceIp", source = "sourceIp")
  @Mapping(target = "title", source = "title")
  @Mapping(target = "content", source = "content")
  @Mapping(target = "status",
      expression = "java(AlertStatus.valueOf(entity.getStatus()))")
  @Mapping(target = "createdAt", source = "createdAt")
  @Mapping(target = "handledAt", source = "handledAt")
  @Mapping(target = "handlerNote", source = "handlerNote")
  com.njydsz.userinfo.domain.alert.SecurityAlert entityToDomain(com.njydsz.userinfo.infra.entity.SecurityAlert entity);

  /**
   * 安全告警领域模型 → 实体。
   *
   * @param domain 安全告警领域模型
   * @return 安全告警实体
   */
  @Mapping(target = "id", source = "id")
  @Mapping(target = "alertType", expression = "java(domain.alertType().name())")
  @Mapping(target = "riskLevel", expression = "java(domain.riskLevel().name())")
  @Mapping(target = "userId", source = "userId")
  @Mapping(target = "username", source = "username")
  @Mapping(target = "sourceIp", source = "sourceIp")
  @Mapping(target = "title", source = "title")
  @Mapping(target = "content", source = "content")
  @Mapping(target = "status", expression = "java(domain.status().name())")
  @Mapping(target = "handledAt", source = "handledAt")
  @Mapping(target = "handlerNote", source = "handlerNote")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  com.njydsz.userinfo.infra.entity.SecurityAlert domainToEntity(com.njydsz.userinfo.domain.alert.SecurityAlert domain);
}
