package com.njydsz.common.seata.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SAGA 状态机日志存储接口
 *
 * <p>用于持久化 SAGA 事务状态，支持崩溃恢复。
 *
 * <p><b>P1-4 修复</b>：原 SAGA 编排器无持久化能力，服务崩溃后状态丢失。 本接口定义 SAGA 状态机的持久化操作。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public interface SagaStateMachineLogStore {

  /**
   * 保存 SAGA 状态机日志
   *
   * @param log 状态机日志
   */
  void save(SagaStateMachineLog log);

  /**
   * 更新 SAGA 状态
   *
   * @param xid 全局事务 ID
   * @param state 新状态
   */
  void updateState(String xid, SagaStateMachineLog.SagaState state);

  /**
   * 根据 XID 查询 SAGA 状态机日志
   *
   * @param xid 全局事务 ID
   * @return 状态机日志，不存在返回 null
   */
  SagaStateMachineLog findByXid(String xid);

  /**
   * 查询超时未完成的 SAGA 事务（用于恢复扫描）
   *
   * @param threshold 超时阈值
   * @param limit 最大返回数量
   * @return 超时未完成的 SAGA 事务列表
   */
  List<SagaStateMachineLog> findTimeoutPending(LocalDateTime threshold, int limit);

  /**
   * 删除已完成的 SAGA 事务日志
   *
   * @param xid 全局事务 ID
   */
  void delete(String xid);
}
