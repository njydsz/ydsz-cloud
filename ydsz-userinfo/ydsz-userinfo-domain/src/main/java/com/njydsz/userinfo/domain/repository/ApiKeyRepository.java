package com.njydsz.userinfo.domain.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.entity.ApiKey;
import com.njydsz.userinfo.domain.query.ApiKeyPageQuery;
import com.njydsz.userinfo.domain.vo.ApiKeyVO;

/**
 * API Key 仓储接口（领域契约层）。
 *
 * <p>管理 API Key 的完整生命周期：创建、验证、吊销、过期清理。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
public interface ApiKeyRepository {

  /**
   * 根据 API Key 哈希查询 Key（用于认证验证）。
   *
   * @param apiKeyHash API Key SHA-256 哈希
   * @return API Key 实体；不存在返回 Optional.empty()
   */
  Optional<ApiKey> findByKeyHash(String apiKeyHash);

  /**
   * 根据 ID 查询 API Key。
   *
   * @param id 主键 ID
   * @return API Key VO；不存在返回 Optional.empty()
   */
  Optional<ApiKeyVO> findById(Long id);

  /**
   * 分页查询 API Key 列表。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 列表）
   */
  PageResponse<List<ApiKeyVO>> page(ApiKeyPageQuery query);

  /**
   * 根据用户 ID 查询其所有 API Key。
   *
   * @param userId 用户 ID
   * @return API Key VO 列表
   */
  List<ApiKeyVO> listByUserId(String userId);

  /**
   * 保存 API Key（创建）。
   *
   * @param apiKey API Key 实体
   * @return 持久化后的实体（含生成的 ID）
   */
  ApiKey save(ApiKey apiKey);

  /**
   * 批量撤销（软删除）API Key。
   *
   * @param ids 要撤销的 ID 集合
   * @return 实际撤销的数量
   */
  int revokeByIds(Collection<Long> ids);

  /**
   * 更新启用状态。
   *
   * @param id 主键 ID
   * @param enabled 启用/禁用
   * @return 影响行数
   */
  int updateEnabled(Long id, boolean enabled);

  /**
   * 更新最后使用时间。
   *
   * @param id 主键 ID
   * @param lastUsedAt 使用时间
   * @return 影响行数
   */
  int updateLastUsedAt(Long id, LocalDateTime lastUsedAt);

  /**
   * 统计已过期的 Key 数量（用于定时任务清理）。
   *
   * @param now 当前时间
   * @return 已过期的 Key 数量
   */
  long countExpired(LocalDateTime now);

  /**
   * 批量删除已过期的 Key（定时任务清理）。
   *
   * @param now 当前时间
   * @return 清理的数量
   */
  int deleteExpired(LocalDateTime now);
}
