package com.njydsz.userinfo.infra.converter;

import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.vo.SamlIdpConfigVO;
import com.njydsz.userinfo.infra.entity.SamlIdpConfig;

/**
 * SAML IdP 配置 MapStruct 转换器（P2-1）。
 *
 * <p>提供 SamlIdpConfig → SamlIdpConfigVO 的转换方法。
 * 使用 Spring 注入模式，替代静态单例 INSTANT，提升可测试性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
@Mapper(componentModel = "spring")
public interface SamlIdpConfigConverter {

  /**
   * 实体 → 视图出参。
   *
   * @param entity IdP 配置实体
   * @return IdP 配置 VO
   */
  SamlIdpConfigVO entityToVo(SamlIdpConfig entity);
}
