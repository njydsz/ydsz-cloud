package com.njydsz.nextwiki.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.nextwiki.domain.entity.StorageQuota;
import com.njydsz.nextwiki.domain.service.QuotaDomainService;

/**
 * 配额应用服务。
 *
 * <p>对外暴露配额查询/扣减/退还 API。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaApplicationService {

  private final QuotaDomainService quotaDomainService;

  /**
   * 查询配额信息（已用/上限、文件数已用/上限等）。
   *
   * @param scopeType 配额作用域类型（如 "user" 表示用户级配额）
   * @param scopeId 作用域 ID（如用户 ID）
   * @return 配额实体 {@link StorageQuota}（含容量与文件数上下限及已用量）
   * @complexity O(1)（一次按作用域查询）
   * @note 只读，无事务边界；委托 {@link QuotaDomainService} 实现
   */
  public StorageQuota getQuotaInfo(String scopeType, String scopeId) {
    return quotaDomainService.getQuotaInfo(scopeType, scopeId);
  }

  /**
   * 设置/更新配额上限（容量上限、文件数上限）。
   *
   * @param scopeType 配额作用域类型
   * @param scopeId 作用域 ID
   * @param quotaLimit 容量上限（字节），可为 {@code null}（不限制）
   * @param fileCountLimit 文件数上限，可为 {@code null}（不限制）
   * @param userId 操作人 ID（记为配额变更人）
   * @return 设置后的配额实体 {@link StorageQuota}
   * @throws 由 {@link QuotaDomainService} 在参数非法或越权时抛出的业务异常
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   * @complexity O(1)（一次配额写入）
   * @note 委托 {@link QuotaDomainService} 实现；仅覆盖持久化，不校验与已用量的关系
   */
  @Transactional(rollbackFor = Exception.class)
  public StorageQuota setQuota(
      String scopeType, String scopeId, Long quotaLimit, Integer fileCountLimit, String userId) {
    return quotaDomainService.setQuota(scopeType, scopeId, quotaLimit, fileCountLimit, userId);
  }
}
