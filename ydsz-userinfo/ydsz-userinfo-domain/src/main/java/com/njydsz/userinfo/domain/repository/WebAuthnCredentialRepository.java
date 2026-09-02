package com.njydsz.userinfo.domain.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import com.njydsz.userinfo.domain.vo.WebAuthnCredentialVO;

/**
 * WebAuthn 凭证仓储接口
 *
 * <p>定义 WebAuthn 凭证的持久化操作，遵循 DDD 仓储模式。实现位于 {@code infra} 层。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface WebAuthnCredentialRepository {

  /**
   * 保存凭证
   *
   * @param credential 凭证 VO
   */
  void save(WebAuthnCredentialVO credential);

  /**
   * 根据凭证 ID 查询凭证
   *
   * @param credentialId 凭证 ID
   * @return 凭证 VO
   */
  Optional<WebAuthnCredentialVO> findByCredentialId(String credentialId);

  /**
   * 根据用户 ID 查询所有凭证
   *
   * @param userId 用户 ID
   * @return 凭证列表
   */
  List<WebAuthnCredentialVO> findByUserId(String userId);

  /**
   * 更新凭证签名计数
   *
   * @param credentialId 凭证 ID
   * @param signCount 新签名计数
   */
  void updateSignCount(String credentialId, long signCount);

  /**
   * 更新最后使用时间
   *
   * @param credentialId 凭证 ID
   * @param lastUsedAt 最后使用时间
   */
  void updateLastUsedAt(String credentialId, LocalDateTime lastUsedAt);

  /**
   * 删除凭证
   *
   * @param credentialId 凭证 ID
   * @return 是否删除成功
   */
  boolean deleteByCredentialId(String credentialId);

  /**
   * 删除用户的所有凭证
   *
   * @param userId 用户 ID
   * @return 删除的凭证数量
   */
  int deleteByUserId(String userId);

  /**
   * 统计用户凭证数量
   *
   * @param userId 用户 ID
   * @return 凭证数量
   */
  long countByUserId(String userId);
}
