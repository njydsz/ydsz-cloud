package com.njydsz.agent.server.execution;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.execution.ExecutionCheckpoint;

/**
 * 执行暂停服务 — 内存管理 {@link ExecutionCheckpoint}。
 *
 * <p>按 {@code approvalId} 保存/查询/删除检查点，供会话级暂停/恢复使用。
 * 当前为单实例内存实现；多副本部署下审批回调可能路由到不同实例，
 * 后续可替换为 {@code AgentStateStore} 持久化实现。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Slf4j
@Service
public class ExecutionPauseService {

  /** 检查点内存缓存 */
  private final ConcurrentMap<String, ExecutionCheckpoint> checkpoints = new ConcurrentHashMap<>();

  /**
   * 保存执行检查点。
   *
   * @param approvalId 审批请求 ID
   * @param checkpoint 检查点
   */
  public void save(String approvalId, ExecutionCheckpoint checkpoint) {
    checkpoints.put(approvalId, checkpoint);
    log.info("[ExecutionPause] 保存检查点: approvalId={}", approvalId);
  }

  /**
   * 按审批 ID 查询检查点。
   *
   * @param approvalId 审批请求 ID
   * @return 检查点；不存在时返回 empty
   */
  public Optional<ExecutionCheckpoint> find(String approvalId) {
    return Optional.ofNullable(checkpoints.get(approvalId));
  }

  /**
   * 删除检查点。
   *
   * @param approvalId 审批请求 ID
   */
  public void discard(String approvalId) {
    checkpoints.remove(approvalId);
    log.info("[ExecutionPause] 删除检查点: approvalId={}", approvalId);
  }

  /**
   * 获取当前缓存的检查点数量（用于监控）。
   *
   * @return 数量
   */
  public int size() {
    return checkpoints.size();
  }
}
