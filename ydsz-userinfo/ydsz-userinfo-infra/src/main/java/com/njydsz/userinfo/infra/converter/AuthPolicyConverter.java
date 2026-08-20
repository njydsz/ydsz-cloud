package com.njydsz.userinfo.infra.converter;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.njydsz.userinfo.domain.vo.AuthPolicyVO;
import com.njydsz.userinfo.infra.entity.AuthPolicyDO;

/**
 * 认证策略 MapStruct 转换器（P3-1）。
 *
 * <p>提供 AuthPolicyDO ↔ AuthPolicyVO 的转换方法。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Mapper
public interface AuthPolicyConverter {

  /** MapStruct 生成的转换器单例。 */
  AuthPolicyConverter INSTANT = Mappers.getMapper(AuthPolicyConverter.class);

  /**
   * 实体 → 视图出参。
   *
   * @param entity 认证策略实体
   * @return 认证策略 VO
   */
  AuthPolicyVO entityToVo(AuthPolicyDO entity);
}
