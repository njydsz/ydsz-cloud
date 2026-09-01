package com.njydsz.userinfo.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.repository.WebAuthnCredentialRepository;
import com.njydsz.userinfo.domain.vo.WebAuthnCredentialVO;
import com.njydsz.userinfo.infra.converter.WebAuthnCredentialConverter;
import com.njydsz.userinfo.infra.entity.WebAuthnCredential;
import com.njydsz.userinfo.infra.mapper.WebAuthnCredentialMapper;

/**
 * WebAuthn 凭证仓储实现。
 *
 * <p>通过 {@link WebAuthnCredentialMapper} 访问数据库，使用注入的 {@link WebAuthnCredentialConverter} 完成 DO ↔ VO 转换。
 * P1-2: 升级为 Spring 注入模式，提升可测试性。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class WebAuthnCredentialRepositoryImpl implements WebAuthnCredentialRepository {

  private final WebAuthnCredentialMapper mapper;
  private final WebAuthnCredentialConverter webAuthnCredentialConverter;

  @Override
  public Optional<WebAuthnCredentialVO> findByCredentialId(String credentialId) {
    LambdaQueryWrapper<WebAuthnCredential> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(WebAuthnCredential::getCredentialId, credentialId);
    WebAuthnCredential entity = mapper.selectOne(wrapper);
    return entity != null
        ? Optional.of(webAuthnCredentialConverter.toVO(entity))
        : Optional.empty();
  }

  @Override
  public List<WebAuthnCredentialVO> findByUserId(String userId) {
    LambdaQueryWrapper<WebAuthnCredential> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(WebAuthnCredential::getUserId, userId)
        .orderByDesc(WebAuthnCredential::getRegisteredAt);
    List<WebAuthnCredential> entities = mapper.selectList(wrapper);
    return entities.stream()
        .map(webAuthnCredentialConverter::toVO)
        .toList();
  }

  @Override
  public void save(WebAuthnCredentialVO credential) {
    WebAuthnCredential entity = webAuthnCredentialConverter.toDO(credential);
    mapper.insert(entity);
  }

  @Override
  public void updateSignCount(String credentialId, long signCount) {
    LambdaUpdateWrapper<WebAuthnCredential> wrapper = new LambdaUpdateWrapper<>();
    wrapper.eq(WebAuthnCredential::getCredentialId, credentialId)
        .set(WebAuthnCredential::getSignCount, signCount);
    mapper.update(null, wrapper);
  }

  @Override
  public void updateLastUsedAt(String credentialId, LocalDateTime lastUsedAt) {
    LambdaUpdateWrapper<WebAuthnCredential> wrapper = new LambdaUpdateWrapper<>();
    wrapper.eq(WebAuthnCredential::getCredentialId, credentialId)
        .set(WebAuthnCredential::getLastUsedAt, lastUsedAt);
    mapper.update(null, wrapper);
  }

  @Override
  public boolean deleteByCredentialId(String credentialId) {
    LambdaQueryWrapper<WebAuthnCredential> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(WebAuthnCredential::getCredentialId, credentialId);
    return mapper.delete(wrapper) > 0;
  }

  @Override
  public int deleteByUserId(String userId) {
    LambdaQueryWrapper<WebAuthnCredential> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(WebAuthnCredential::getUserId, userId);
    return mapper.delete(wrapper);
  }

  @Override
  public long countByUserId(String userId) {
    LambdaQueryWrapper<WebAuthnCredential> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(WebAuthnCredential::getUserId, userId);
    return mapper.selectCount(wrapper);
  }
}
