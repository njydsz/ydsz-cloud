package com.njydsz.userinfo.infra.converter;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.oauth2.OAuth2Application;
import com.njydsz.userinfo.domain.oauth2.OAuth2Application.ApplicationStatus;
import com.njydsz.userinfo.domain.oauth2.OAuth2Application.ClientType;

/**
 * OAuth2 应用 MapStruct 转换器。
 *
 * <p>提供 OAuth2Application ↔ OAuth2Application 的转换方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper(componentModel = "spring")
@Component
public interface OAuth2ApplicationConverter {

  /**
   * 应用实体 → 领域模型。
   *
   * @param entity 应用实体
   * @return 应用领域模型
   */
  @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
  @Mapping(target = "id", source = "id")
  @Mapping(target = "clientId", source = "clientId")
  @Mapping(target = "clientName", source = "clientName")
  @Mapping(target = "clientSecret", source = "clientSecret")
  @Mapping(target = "clientType",
      expression = "java(ClientType.valueOf(entity.getClientType()))")
  @Mapping(target = "redirectUris", source = "redirectUris")
  @Mapping(target = "allowedScopes", source = "allowedScopes")
  @Mapping(target = "allowedAudiences", source = "allowedAudiences")
  @Mapping(target = "status",
      expression = "java(ApplicationStatus.valueOf(entity.getStatus()))")
  @Mapping(target = "description", source = "description")
  @Mapping(target = "iconUrl", source = "iconUrl")
  @Mapping(target = "createdAt", source = "createdAt")
  @Mapping(target = "updatedAt", source = "updatedAt")
  @Mapping(target = "createdBy", source = "createdBy")
  com.njydsz.userinfo.domain.oauth2.OAuth2Application entityToDomain(com.njydsz.userinfo.infra.entity.OAuth2Application entity);

  /**
   * 应用领域模型 → 实体。
   *
   * @param domain 应用领域模型
   * @return 应用实体
   */
  @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
  @Mapping(target = "id", source = "id")
  @Mapping(target = "clientId", source = "clientId")
  @Mapping(target = "clientName", source = "clientName")
  @Mapping(target = "clientSecret", source = "clientSecret")
  @Mapping(target = "clientType", expression = "java(domain.clientType().name())")
  @Mapping(target = "redirectUris", source = "redirectUris")
  @Mapping(target = "allowedScopes", source = "allowedScopes")
  @Mapping(target = "allowedAudiences", source = "allowedAudiences")
  @Mapping(target = "status", expression = "java(domain.status().name())")
  @Mapping(target = "description", source = "description")
  @Mapping(target = "iconUrl", source = "iconUrl")
  @Mapping(target = "createdBy", source = "createdBy")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  com.njydsz.userinfo.infra.entity.OAuth2Application domainToEntity(com.njydsz.userinfo.domain.oauth2.OAuth2Application domain);
}
