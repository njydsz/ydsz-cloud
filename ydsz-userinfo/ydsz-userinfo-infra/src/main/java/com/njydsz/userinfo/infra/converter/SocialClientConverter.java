package com.njydsz.userinfo.infra.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.vo.SocialClientVO;
import com.njydsz.userinfo.infra.entity.SocialClient;

/**
 * 社交平台客户端配置 MapStruct 转换器（P1-1）。
 *
 * <p>提供 SocialClient → SocialClientVO 的转换方法。
 * 使用 Spring 注入模式，替代静态单例 INSTANT，提升可测试性。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
@Mapper(componentModel = "spring")
public interface SocialClientConverter {

  /**
   * 实体 → 视图出参。
   *
   * @param entity 客户端配置实体
   * @return 配置 VO
   */
  @Mapping(target = "id", source = "id")
  @Mapping(target = "platform", source = "platform")
  @Mapping(target = "platformName", source = "platformName")
  @Mapping(target = "appId", source = "appId")
  @Mapping(target = "scope", source = "scope")
  @Mapping(target = "redirectUri", source = "redirectUri")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "sortOrder", source = "sortOrder")
  @Mapping(target = "remark", source = "remark")
  @Mapping(target = "createdAt", source = "createdAt")
  @Mapping(target = "updatedAt", source = "updatedAt")
  @Mapping(target = "createdBy", source = "createdBy")
  SocialClientVO entityToVo(SocialClient entity);
}
