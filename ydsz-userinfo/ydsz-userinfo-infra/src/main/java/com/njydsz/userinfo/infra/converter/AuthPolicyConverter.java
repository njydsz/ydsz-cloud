package com.njydsz.userinfo.infra.converter;

import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.vo.AuthPolicyVO;
import com.njydsz.userinfo.infra.entity.AuthPolicy;

/**
 * 认证策略 MapStruct 转换器（P3-1）。
 *
 * <p>提供 AuthPolicy → AuthPolicyVO 的转换方法。
 * 使用 Spring 注入模式，替代静态单例 INSTANT，提升可测试性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
@Mapper(componentModel = "spring")
public interface AuthPolicyConverter {

  /**
   * 实体 → 视图出参。
   *
   * @param entity 认证策略实体
   * @return 认证策略 VO
   */
  AuthPolicyVO entityToVo(AuthPolicy entity);
}
