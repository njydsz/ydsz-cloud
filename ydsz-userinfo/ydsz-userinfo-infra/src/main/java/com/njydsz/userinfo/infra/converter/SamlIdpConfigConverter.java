package com.njydsz.userinfo.infra.converter;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.njydsz.userinfo.domain.vo.SamlIdpConfigVO;
import com.njydsz.userinfo.infra.entity.SamlIdpConfigDO;

/**
 * SAML IdP 配置 MapStruct 转换器（P2-1）。
 *
 * <p>提供 SamlIdpConfigDO ↔ SamlIdpConfigVO 的转换方法。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Mapper
public interface SamlIdpConfigConverter {

  /** MapStruct 生成的转换器单例。 */
  SamlIdpConfigConverter INSTANT = Mappers.getMapper(SamlIdpConfigConverter.class);

  /**
   * 实体 → 视图出参。
   *
   * @param entity IdP 配置实体
   * @return IdP 配置 VO
   */
  SamlIdpConfigVO entityToVo(SamlIdpConfigDO entity);
}
