package com.njydsz.workflow.domain.repository;

import java.util.Optional;

import com.njydsz.workflow.domain.entity.FlowIdempotent;

/**
 * 工作流幂等记录仓储接口（domain 层契约）。
 *
 * <p>定义幂等记录（ydsz_flow_idempotent）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>幂等键唯一性由 scope + key_hash 约束保障</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.02
 */
public interface FlowIdempotentRepository {

  /**
   * 保存幂等记录（新增）。
   *
   * @param entity 幂等记录实体
   * @return 保存后的实体（含生成的 id 与时间戳）
   */
  FlowIdempotent save(FlowIdempotent entity);

  /**
   * 根据 ID 查询幂等记录。
   *
   * @param id 记录 ID
   * @return 幂等记录；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowIdempotent> findById(String id);

  /**
   * 根据作用域 + 幂等键哈希查询记录。
   *
   * <p>用于幂等去重判断：若存在 SUCCESS 状态记录则直接返回缓存结果。
   *
   * @param scope 幂等作用域
   * @param keyHash 幂等键哈希
   * @return 幂等记录；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowIdempotent> findByScopeAndKeyHash(String scope, String keyHash);

  /**
   * 更新幂等记录状态。
   *
   * @param entity 含 id 和待更新字段的实体
   * @return 更新后的实体
   */
  FlowIdempotent update(FlowIdempotent entity);

  /**
   * 根据 ID 删除幂等记录。
   *
   * @param id 记录 ID
   */
  void deleteById(String id);
}
