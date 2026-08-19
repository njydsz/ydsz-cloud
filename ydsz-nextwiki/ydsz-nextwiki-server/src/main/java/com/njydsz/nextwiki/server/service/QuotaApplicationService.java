package com.njydsz.nextwiki.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.dto.StorageQuotaDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.StorageQuotaRepository;
import com.njydsz.nextwiki.domain.service.QuotaDomainService;
import com.njydsz.nextwiki.domain.vo.StorageQuotaVO;
import com.njydsz.nextwiki.server.cache.NextwikiCacheService;

/**
 * 配额应用服务。
 *
 * <p>对外暴露配额查询/扣减/退还 API。
 *
 * <p><b>缓存策略：</b>
 *
 * <ul>
 *   <li>配额查询：Redis 缓存 3 分钟，减少高频读取压力
 *   <li>配额变更（上传/删除/恢复）：即时失效缓存
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaApplicationService {

  private final QuotaDomainService quotaDomainService;
  private final StorageQuotaRepository storageQuotaRepository;
  private final NextwikiCacheService cacheService;

  /**
   * 查询配额信息（已用/上限、文件数已用/上限等）。
   *
   * <p>带 Redis 缓存，首次查询回填，变更时失效。
   *
   * @param scopeType 配额作用域类型（如 "user" 表示用户级配额）
   * @param scopeId 作用域 ID（如用户 ID）
   * @return 配额 VO（含容量与文件数上下限及已用量）
   */
  public StorageQuotaVO getQuotaInfo(String scopeType, String scopeId) {
    return cacheService.getQuota(
            scopeType, scopeId, () -> storageQuotaRepository.findByScope(scopeType, scopeId))
        .orElseThrow(
            () ->
                BusinessException.of(NextwikiExceptionCode.QUOTA_NOT_FOUND)
                    .data("scopeType", scopeType)
                    .data("scopeId", scopeId));
  }

  /**
   * 设置/更新配额上限（容量上限、文件数上限）。
   *
   * @param scopeType 配额作用域类型
   * @param scopeId 作用域 ID
   * @param quotaLimit 容量上限（字节），可为 {@code null}（不限制）
   * @param fileCountLimit 文件数上限，可为 {@code null}（不限制）
   * @param userId 操作人 ID（记为配额变更人）
   * @return 设置后的配额 VO
   */
  @Transactional(rollbackFor = Exception.class)
  public StorageQuotaVO setQuota(
      String scopeType, String scopeId, Long quotaLimit, Integer fileCountLimit, String userId) {
    StorageQuotaVO existing = storageQuotaRepository.findByScope(scopeType, scopeId)
        .orElse(null);

    StorageQuotaDTO dto;
    if (existing == null) {
      // 首次创建，使用默认值填充
      dto = quotaDomainService.buildDefaultQuota(scopeType, scopeId);
    } else {
      dto = StorageQuotaDTO.builder()
          .id(existing.getId())
          .scopeType(existing.getScopeType())
          .scopeId(existing.getScopeId())
          .quotaLimit(existing.getQuotaLimit())
          .quotaUsed(existing.getQuotaUsed())
          .fileCountLimit(existing.getFileCountLimit())
          .fileCountUsed(existing.getFileCountUsed())
          .createdBy(existing.getCreatedBy())
          .build();
    }

    // 更新上限
    if (quotaLimit != null) {
      dto.setQuotaLimit(quotaLimit);
    }
    if (fileCountLimit != null) {
      dto.setFileCountLimit(fileCountLimit);
    }
    dto.setUpdatedBy(userId);

    StorageQuotaVO saved = storageQuotaRepository.save(dto);

    // 失效缓存
    cacheService.evictQuotaOnChange(scopeType, scopeId);

    log.info("[QuotaApplicationService] 配额设置成功: {}:{}, quotaLimit={}, fileCountLimit={}",
        scopeType, scopeId, quotaLimit, fileCountLimit);
    return saved;
  }

  /**
   * 原子增加已使用量（上传文件时调用）。
   *
   * @param scopeType 配额维度
   * @param scopeId 维度 ID
   * @param bytesDelta 字节变化量（正数）
   * @param fileCountDelta 文件数变化量（正数）
   */
  @Transactional(rollbackFor = Exception.class)
  public void addUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta) {
    // 确保配额记录存在（首次使用时自动创建）
    ensureQuotaExists(scopeType, scopeId);
    storageQuotaRepository.addUsage(scopeType, scopeId, bytesDelta, fileCountDelta);
    cacheService.evictQuotaOnChange(scopeType, scopeId);
    log.debug("[QuotaApplicationService] 配额增加: {}:{}, bytes={}, count={}",
        scopeType, scopeId, bytesDelta, fileCountDelta);
  }

  /**
   * 原子减少已使用量（删除/恢复文件时调用）。
   *
   * @param scopeType 配额维度
   * @param scopeId 维度 ID
   * @param bytesDelta 字节变化量（正数，内部取减）
   * @param fileCountDelta 文件数变化量（正数，内部取减）
   */
  @Transactional(rollbackFor = Exception.class)
  public void subtractUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta) {
    storageQuotaRepository.subtractUsage(scopeType, scopeId, bytesDelta, fileCountDelta);
    cacheService.evictQuotaOnChange(scopeType, scopeId);
    log.debug("[QuotaApplicationService] 配额减少: {}:{}, bytes={}, count={}",
        scopeType, scopeId, bytesDelta, fileCountDelta);
  }

  /**
   * 确保配额记录存在（首次使用时自动创建）。
   */
  private void ensureQuotaExists(String scopeType, String scopeId) {
    if (storageQuotaRepository.findByScope(scopeType, scopeId).isEmpty()) {
      StorageQuotaDTO dto = quotaDomainService.buildDefaultQuota(scopeType, scopeId);
      storageQuotaRepository.save(dto);
    }
  }

  /**
   * 校验配额是否充足（上传前置校验）。
   *
   * @param scopeType 配额维度
   * @param scopeId 维度 ID
   * @param requiredBytes 所需字节数
   * @throws BusinessException 配额不足时抛出
   */
  public void checkQuota(String scopeType, String scopeId, long requiredBytes) {
    StorageQuotaVO quota = getQuotaInfo(scopeType, scopeId);
    quotaDomainService.checkQuota(
        StorageQuotaDTO.builder()
            .quotaLimit(quota != null ? quota.getQuotaLimit() : null)
            .quotaUsed(quota != null ? quota.getQuotaUsed() : null)
            .fileCountLimit(quota != null ? quota.getFileCountLimit() : null)
            .fileCountUsed(quota != null ? quota.getFileCountUsed() : null)
            .build(),
        requiredBytes);
  }
}
