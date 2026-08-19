package com.njydsz.userinfo.infra.converter;

import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.vo.WebAuthnCredentialVO;
import com.njydsz.userinfo.infra.entity.WebAuthnCredentialDO;

/**
 * WebAuthn 凭证转换器
 *
 * <p>负责 DO（持久化实体）与 VO（视图对象）之间的转换。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class WebAuthnCredentialConverter {

  /**
   * DO → VO 转换
   *
   * @param entity 持久化实体
   * @return 视图对象
   */
  public WebAuthnCredentialVO toVO(WebAuthnCredentialDO entity) {
    if (entity == null) {
      return null;
    }
    WebAuthnCredentialVO vo = new WebAuthnCredentialVO();
    vo.setCredentialId(entity.getCredentialId());
    vo.setUserId(entity.getUserId());
    vo.setPublicKey(entity.getPublicKey());
    vo.setSignCount(entity.getSignCount() != null ? entity.getSignCount() : 0L);
    vo.setCredentialType(entity.getCredentialType());
    vo.setAaguid(entity.getAaguid());
    vo.setDisplayName(entity.getDisplayName());
    vo.setRegisteredAt(entity.getRegisteredAt());
    vo.setLastUsedAt(entity.getLastUsedAt());
    return vo;
  }

  /**
   * VO → DO 转换
   *
   * @param vo 视图对象
   * @return 持久化实体
   */
  public WebAuthnCredentialDO toDO(WebAuthnCredentialVO vo) {
    if (vo == null) {
      return null;
    }
    WebAuthnCredentialDO entity = new WebAuthnCredentialDO();
    entity.setCredentialId(vo.getCredentialId());
    entity.setUserId(vo.getUserId());
    entity.setPublicKey(vo.getPublicKey());
    entity.setSignCount(vo.getSignCount());
    entity.setCredentialType(vo.getCredentialType());
    entity.setAaguid(vo.getAaguid());
    entity.setDisplayName(vo.getDisplayName());
    entity.setRegisteredAt(vo.getRegisteredAt());
    entity.setLastUsedAt(vo.getLastUsedAt());
    return entity;
  }
}
