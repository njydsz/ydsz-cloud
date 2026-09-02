package com.njydsz.system.server.service.rollback;

/**
 * 回滚策略接口 — 定义资源重建契约。
 *
 * <p>每种资源类型（CONFIG/DICT/VARIABLE）实现本接口，负责从快照 JSON 反序列化并重建资源。
 * 替代原有的 {@code RollbackCallback} 匿名回调，使回滚逻辑内聚到独立类中，便于测试和维护。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>策略类通过 Spring 注入所需依赖（Repository、Cache 等），避免回调闭包捕获
 *   <li>每个策略类仅处理单一资源类型，符合单一职责原则
 *   <li>策略类可被单元测试独立验证
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@FunctionalInterface
public interface RollbackStrategy {

  /**
   * 执行回滚逻辑 — 从快照 JSON 反序列化并重建资源。
   *
   * <p>实现类应完成：
   *
   * <ol>
   *   <li>反序列化快照 JSON 为资源 VO
   *   <li>更新或创建资源记录
   *   <li>失效相关缓存
   *   <li>发布变更事件（如适用）
   * </ol>
   *
   * @param snapshotJson 目标版本的快照 JSON
   */
  void rebuild(String snapshotJson);
}
