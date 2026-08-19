package com.njydsz.userinfo.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.userinfo.domain.repository.WebAuthnCredentialRepository;
import com.njydsz.userinfo.domain.vo.WebAuthnCredentialVO;
import com.njydsz.userinfo.infra.converter.WebAuthnCredentialConverter;
import com.njydsz.userinfo.infra.entity.WebAuthnCredentialDO;
import com.njydsz.userinfo.infra.mapper.WebAuthnCredentialMapper;

/**
 * WebAuthn 凭证仓储实现
 *
 * <p>实现 {@link WebAuthnCredentialRepository} 接口，负责 DO ↔ VO 转换和数据库操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class WebAuthnCredentialRepositoryImpl implements WebAuthnCredentialRepository {

  private final WebAuthnCredentialMapper mapper;
  private final WebAuthnCredentialConverter converter;

  @Override
  public void save(WebAuthnCredentialVO credential) {
    WebAuthnCredentialDO entity = converter.toDO(credential);
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    entity.setDeleted(false);
    mapper.insert(entity);
  }

  @Override
  public Optional<WebAuthnCredentialVO> findByCredentialId(String credentialId) {
    LambdaQueryWrapper<WebAuthnCredentialDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(WebAuthnCredentialDO::getCredentialId, credentialId)
        .eq(WebAuthnCredentialDO::getDeleted, false);
    WebAuthnCredentialDO entity = mapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::toVO);
  }

  @Override
  public List<WebAuthnCredentialVO> findByUserId(String userId) {
    LambdaQueryWrapper<WebAuthnCredentialDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(WebAuthnCredentialDO::getUserId, userId)
        .eq(WebAuthnCredentialDO::getDeleted, false)
        .orderByDesc(WebAuthnCredentialDO::getLastUsedAt);
    List<WebAuthnCredentialDO> entities = mapper.selectList(wrapper);
    return entities.stream().map(converter::toVO).toList();
  }

  @Override
  public void updateSignCount(String credentialId, long signCount) {
    LambdaQueryWrapper<WebAuthnCredentialDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(WebAuthnCredentialDO::getCredentialId, credentialId);
    WebAuthnCredentialDO entity = new WebAuthnCredentialDO();
    entity.setSignCount(signCount);
    entity.setUpdatedAt(LocalDateTime.now());
    mapper.update(entity, wrapper);
  }

  @Override
  public void updateLastUsedAt(String credentialId, LocalDateTime lastUsedAt) {
    LambdaQueryWrapper<WebAuthnCredentialDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(WebAuthnCredentialDO::getCredentialId, credentialId);
    WebAuthnCredentialDO entity = new WebAuthnCredentialDO();
    entity.setLastUsedAt(lastUsedAt);
    entity.setUpdatedAt(LocalDateTime.now());
    mapper.update(entity, wrapper);
  }

  @Override
  public boolean deleteByCredentialId(String credentialId) {
    LambdaQueryWrapper<WebAuthnCredentialDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(WebAuthnCredentialDO::getCredentialId, credentialId);
    WebAuthnCredentialDO entity = new WebAuthnCredentialDO();
    entity.setDeleted(true);
    entity.setUpdatedAt(LocalDateTime.now());
    return mapper.update(entity, wrapper) > 0;
  }

  @Override
  public int deleteByUserId(String userId) {
    LambdaQueryWrapper<WebAuthnCredentialDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(WebAuthnCredentialDO::getUserId, userId)
        .eq(WebAuthnCredentialDO::getDeleted, false);
    WebAuthnCredentialDO entity = new WebAuthnCredentialDO();
    entity.setDeleted(true);
    entity.setUpdatedAt(LocalDateTime.now());
    return mapper.update(entity, wrapper);
  }

  @Override
  public long countByUserId(String userId) {
    LambdaQueryWrapper<WebAuthnCredentialDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(WebAuthnCredentialDO::getUserId, userId)
        .eq(WebAuthnCredentialDO::getDeleted, false);
    return mapper.selectCount(wrapper);
  }
}
