package com.njydsz.userinfo.infra.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.oauth2.OAuth2Application;
import com.njydsz.userinfo.infra.entity.OAuth2ApplicationDO;

/**
 * OAuth2 应用 MapStruct 转换器。
 *
 * <p>提供 OAuth2ApplicationDO ↔ OAuth2Application 的转换方法。
 *
 * @author ydsz-team
 * @since 2.18.0
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
  @Mapping(target = "id", source = "id")
  @Mapping(target = "clientId", source = "clientId")
  @Mapping(target = "clientName", source = "clientName")
  @Mapping(target = "clientSecret", source = "clientSecret")
  @Mapping(target = "clientType", expression = "java(OAuth2Application.ClientType.valueOf(entity.getClientType()))")
  @Mapping(target = "redirectUris", source = "redirectUris")
  @Mapping(target = "allowedScopes", source = "allowedScopes")
  @Mapping(target = "allowedAudiences", source = "allowedAudiences")
  @Mapping(target = "status", expression = "java(OAuth2Application.ApplicationStatus.valueOf(entity.getStatus()))")
  @Mapping(target = "description", source = "description")
  @Mapping(target = "iconUrl", source = "iconUrl")
  @Mapping(target = "createdAt", source = "createdAt")
  @Mapping(target = "updatedAt", source = "updatedAt")
  @Mapping(target = "createdBy", source = "createdBy")
  OAuth2Application entityToDomain(OAuth2ApplicationDO entity);

  /**
   * 应用领域模型 → 实体。
   *
   * @param domain 应用领域模型
   * @return 应用实体
   */
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
  OAuth2ApplicationDO domainToEntity(OAuth2Application domain);
}
