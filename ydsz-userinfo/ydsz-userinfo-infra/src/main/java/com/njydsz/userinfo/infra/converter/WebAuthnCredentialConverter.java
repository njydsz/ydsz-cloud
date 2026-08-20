package com.njydsz.userinfo.infra.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.vo.WebAuthnCredentialVO;
import com.njydsz.userinfo.infra.entity.WebAuthnCredentialDO;

/**
 * WebAuthn 凭证 MapStruct 转换器（P1-2 统一 Converter 策略）。
 *
 * <p>负责 DO（持久化实体）与 VO（视图对象）之间的转换。
 * 使用 Spring 注入模式 + MapStruct，替代手动 toVO/toDO 方法，提升可测试性并与项目 Converter 体系一致。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
@Mapper(componentModel = "spring")
public interface WebAuthnCredentialConverter {

  /**
   * DO → VO 转换。
   *
   * @param entity 持久化实体
   * @return 视图对象
   */
  @Mapping(target = "signCount", source = "signCount")
  WebAuthnCredentialVO toVO(WebAuthnCredentialDO entity);

  /**
   * VO → DO 转换。
   *
   * @param vo 视图对象
   * @return 持久化实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  WebAuthnCredentialDO toDO(WebAuthnCredentialVO vo);
}
