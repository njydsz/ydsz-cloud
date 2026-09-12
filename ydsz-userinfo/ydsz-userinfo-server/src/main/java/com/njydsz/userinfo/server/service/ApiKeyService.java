package com.njydsz.userinfo.server.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.userinfo.domain.dto.ApiKeyCreateDTO;
import com.njydsz.userinfo.domain.entity.ApiKey;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.query.ApiKeyPageQuery;
import com.njydsz.userinfo.domain.repository.ApiKeyRepository;
import com.njydsz.userinfo.domain.vo.ApiKeyVO;

/**
 * API Key 管理服务（P1-2 API Key 授权体系）。
 *
 * <p>提供 API Key 的完整生命周期管理：
 *
 * <ul>
 *   <li>生成：随机 32 字节 → Base64 编码 → 存储 SHA-256 哈希</li>
 *   <li>验证：计算请求 Key 哈希 → 匹配数据库 → 校验有效期</li>
 *   <li>吊销：批量软删除</li>
 *   <li>过期清理：定时任务物理删除</li>
 * </ul>
 *
 * <p><b>安全设计：</b>
 *
 * <ul>
 *   <li>明文 API Key 仅在创建时返回一次（类似 AWS/GitHub 设计）</li>
 *   <li>数据库只存储 SHA-256 哈希 + 前 8 位明文前缀</li>
 *   <li>使用 {@link SecureRandom} 生成，不可预测</li>
 *   <li>每个用户最多持有 10 个有效 Key</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

  /** API Key 随机字节长度 */
  private static final int API_KEY_RANDOM_BYTES = 32;

  /** API Key 前缀明文长度 */
  private static final int API_KEY_PREFIX_LENGTH = 8;

  /** 单个用户最大有效 Key 数 */
  private static final int MAX_KEYS_PER_USER = 10;

  /** API Key 前缀标识 */
  private static final String API_KEY_PREFIX = "ak_";

  /** 默认过期天数（90 天） */
  private static final int DEFAULT_EXPIRE_DAYS = 90;

  /** 默认限流阈值（每分钟 600 次 = 10 QPS） */
  private static final int DEFAULT_RATE_LIMIT = 600;

  private final SecureRandom secureRandom = new SecureRandom();

  private final ApiKeyRepository apiKeyRepository;

  /**
   * 创建 API Key。
   *
   * <p>生成随机 Key，返回明文（仅此一次），存储哈希值。
   *
   * @param dto 创建参数
   * @return ApiKeyVO（含明文 apiKey，仅此次返回）
   * @throws BusinessException 超过最大 Key 数限制时抛出
   */
  @Transactional(rollbackFor = Exception.class)
  public ApiKeyVO createKey(ApiKeyCreateDTO dto) {
    String userId = getCurrentUserId();

    // 检查用户的有效 Key 数量
    List<ApiKeyVO> existingKeys = apiKeyRepository.listByUserId(userId);
    long activeCount = existingKeys.stream().filter(k -> Boolean.TRUE.equals(k.getIsEnabled())).count();
    if (activeCount >= MAX_KEYS_PER_USER) {
      throw new BusinessException(UserInfoExceptionCode.API_KEY_LIMIT_EXCEEDED);
    }

    // 生成 API Key
    String plainKey = generateApiKey();
    String keyHash = sha256Hex(plainKey);
    String keyPrefix = plainKey.substring(0, API_KEY_PREFIX_LENGTH);

    // 计算过期时间
    LocalDateTime expireAt = null;
    if (dto.getExpireDays() != null && dto.getExpireDays() > 0) {
      expireAt = LocalDateTime.now().plusDays(dto.getExpireDays());
    } else {
      expireAt = LocalDateTime.now().plusDays(DEFAULT_EXPIRE_DAYS);
    }

    // 构建实体
    ApiKey entity = new ApiKey();
    entity.setApiKeyHash(keyHash);
    entity.setApiKeyPrefix(keyPrefix);
    entity.setUserId(userId);
    entity.setKeyName(dto.getKeyName());
    entity.setScopes(dto.getScopes());
    entity.setExpireAt(expireAt);
    entity.setRateLimit(dto.getRateLimit() != null ? dto.getRateLimit() : DEFAULT_RATE_LIMIT);
    entity.setIsEnabled(true);

    apiKeyRepository.save(entity);

    log.info("API Key 已创建: userId={}, keyId={}, prefix={}", userId, entity.getId(), keyPrefix);

    // 返回 VO（含明文 Key）
    ApiKeyVO vo = new ApiKeyVO();
    vo.setId(entity.getId());
    vo.setApiKey(plainKey);  // 仅创建时返回明文
    vo.setApiKeyPrefix(keyPrefix);
    vo.setKeyName(dto.getKeyName());
    vo.setScopes(dto.getScopes());
    vo.setExpireAt(expireAt);
    vo.setRateLimit(entity.getRateLimit());
    vo.setIsEnabled(true);
    vo.setCreatedAt(LocalDateTime.now());
    return vo;
  }

  /**
   * 验证 API Key 是否有效。
   *
   * @param plainKey 明文 API Key
   * @return 有效的 ApiKey 实体
   * @throws BusinessException Key 无效/过期/禁用时抛出
   */
  public ApiKey validateKey(String plainKey) {
    if (plainKey == null || plainKey.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.API_KEY_INVALID);
    }

    String keyHash = sha256Hex(plainKey);
    ApiKey apiKey = apiKeyRepository.findByKeyHash(keyHash)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.API_KEY_INVALID));

    // 检查是否启用
    if (!Boolean.TRUE.equals(apiKey.getIsEnabled())) {
      throw new BusinessException(UserInfoExceptionCode.API_KEY_DISABLED);
    }

    // 检查是否过期
    if (apiKey.getExpireAt() != null && apiKey.getExpireAt().isBefore(LocalDateTime.now())) {
      throw new BusinessException(UserInfoExceptionCode.API_KEY_EXPIRED);
    }

    // 更新最后使用时间（异步更佳，此处简化）
    try {
      apiKeyRepository.updateLastUsedAt(apiKey.getId(), LocalDateTime.now());
    } catch (Exception e) {
      log.warn("Failed to update API Key last used time: keyId={}", apiKey.getId());
    }

    return apiKey;
  }

  /**
   * 分页查询当前用户的 API Key 列表。
   *
   * @param query 分页查询参数
   * @return 分页结果
   */
  public PageResponse<List<ApiKeyVO>> pageKeys(ApiKeyPageQuery query) {
    query.setUserId(getCurrentUserId());
    return apiKeyRepository.page(query);
  }

  /**
   * 查询当前用户的所有 API Key。
   *
   * @return API Key VO 列表
   */
  public List<ApiKeyVO> listMyKeys() {
    return apiKeyRepository.listByUserId(getCurrentUserId());
  }

  /**
   * 批量撤销（吊销）API Key。
   *
   * @param ids 要撤销的 ID 集合
   * @return 实际撤销数量
   */
  @Transactional(rollbackFor = Exception.class)
  public int revokeKeys(Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    int count = apiKeyRepository.revokeByIds(ids);
    log.info("API Key 已撤销: userId={}, count={}", getCurrentUserId(), count);
    return count;
  }

  /**
   * 更新 API Key 启用状态。
   *
   * @param id 主键 ID
   * @param enabled 启用/禁用
   */
  public void updateEnabled(Long id, boolean enabled) {
    apiKeyRepository.updateEnabled(id, enabled);
    log.info("API Key 状态已更新: userId={}, id={}, enabled={}", getCurrentUserId(), id, enabled);
  }

  /**
   * 清理已过期的 API Key（定时任务调用）。
   *
   * @return 清理数量
   */
  public int cleanupExpired() {
    LocalDateTime now = LocalDateTime.now();
    long count = apiKeyRepository.countExpired(now);
    if (count > 0) {
      int deleted = apiKeyRepository.deleteExpired(now);
      log.info("API Key 过期清理完成: expired={}, deleted={}", count, deleted);
      return deleted;
    }
    return 0;
  }

  // ==================== 私有方法 ====================

  private String generateApiKey() {
    byte[] randomBytes = new byte[API_KEY_RANDOM_BYTES];
    secureRandom.nextBytes(randomBytes);
    return API_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }

  /**
   * 计算 SHA-256 摘要（委托 {@link DigestUtils#sha256Hex(String)}，UTF-8 编码）。
   *
   * @param input 待摘要内容（明文 API Key）
   * @return Hex 编码摘要
   */
  private String sha256Hex(String input) {
    return DigestUtils.sha256Hex(input);
  }

  private String getCurrentUserId() {
    String userId = RequestContext.getUserId();
    if (userId == null || userId.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.API_KEY_INVALID);
    }
    return userId;
  }
}
