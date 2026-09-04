package com.njydsz.userinfo.domain.converter;

import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.entity.AuthPolicy;
import com.njydsz.userinfo.domain.vo.AuthPolicyVO;

/**
 * 认证策略 MapStruct 转换器（P3-1）。
 *
 * <p>提供 AuthPolicy → AuthPolicyVO 的转换方法。
 * 使用 Spring 注入模式，替代静态单例 INSTANT，提升可测试性。
 *
 * @author ydsz-team
 * @since 26.09.01
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
