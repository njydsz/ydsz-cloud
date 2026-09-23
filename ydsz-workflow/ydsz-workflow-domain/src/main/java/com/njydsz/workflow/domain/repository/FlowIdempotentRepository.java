package com.njydsz.workflow.domain.repository;

import com.njydsz.workflow.domain.entity.FlowIdempotent;

/**
 * 工作流幂等记录仓储接口（Domain 层契约）。
 *
 * <p>定义幂等记录的持久化操作，实现位于 {@code infra} 层。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
public interface FlowIdempotentRepository {

  /**
   * 尝试插入幂等记录（状态 PROCESSING）。
   *
   * <p>若唯一约束 (scope, keyHash) 冲突且状态为 SUCCESS，返回 null（表示已成功，可取缓存结果）；
   * 若冲突且状态为 PROCESSING，返回 null（表示正在处理，调用方应等待或降级）。
   *
   * @param record 幂等记录
   * @return 插入成功返回记录；冲突返回 null
   */
  FlowIdempotent tryInsert(FlowIdempotent record);

  /**
   * 更新为成功状态并缓存结果。
   *
   * @param scope 作用域
   * @param keyHash 幂等键哈希
   * @param resultData 结果 JSON
   */
  void markSuccess(String scope, String keyHash, String resultData);

  /**
   * 更新为失败状态。
   *
   * @param scope 作用域
   * @param keyHash 幂等键哈希
   * @param errorMessage 错误信息
   */
  void markFailed(String scope, String keyHash, String errorMessage);

  /**
   * 查询已成功的幂等记录（取缓存结果用）。
   *
   * @param scope 作用域
   * @param keyHash 幂等键哈希
   * @return 成功记录；不存在返回 null
   */
  FlowIdempotent findSuccess(String scope, String keyHash);

  /**
   * 清理过期记录（TTL 已过的 SUCCESS 记录）。
   *
   * @param ttlBefore 过期时间上限
   * @return 清理条数
   */
  int purgeExpired(java.time.LocalDateTime ttlBefore);
}
