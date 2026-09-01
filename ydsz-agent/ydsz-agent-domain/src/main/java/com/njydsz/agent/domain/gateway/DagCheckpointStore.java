package com.njydsz.agent.domain.gateway;

import java.util.Optional;

import com.njydsz.agent.domain.agent.DagCheckpoint;

/**
 * DAG 检查点存储网关
 *
 * <p>定义检查点的持久化契约，解耦编排引擎与存储实现。 支持按执行 ID 加载、保存、删除检查点，用于实现断点续跑。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface DagCheckpointStore {

  /**
   * 保存检查点。
   *
   * <p>同一 executionId 多次写入时覆盖（以最新快照为准）。
   *
   * @param checkpoint 检查点快照
   */
  void save(DagCheckpoint checkpoint);

  /**
   * 加载指定执行 ID 的检查点。
   *
   * @param executionId 执行 ID
   * @return 存在时返回检查点，否则返回 {@link Optional#empty()}
   */
  Optional<DagCheckpoint> load(String executionId);

  /**
   * 删除指定执行 ID 的检查点。
   *
   * <p>编排全部成功或手动清理时调用，释放存储空间。
   *
   * @param executionId 执行 ID
   */
  void delete(String executionId);
}
