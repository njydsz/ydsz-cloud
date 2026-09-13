package com.njydsz.agent.domain.workspace;

/**
 * 工作区存储接口 — Agent 工作区的持久化抽象（对标 AgentScope 的可插拔文件系统）。
 *
 * <p>实现可选择：
 * <ul>
 *   <li>本地磁盘文件（开发环境）</li>
 *   <li>Redis KV（生产环境，跨副本共享）</li>
 *   <li>数据库存储（审计合规场景）</li>
 * </ul>
 *
 * <p><b>线程安全</b>：存储实现须保证并发 load/save 的线程安全。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
public interface AgentWorkspaceStore {

  /**
   * 加载指定 Agent 的工作区。
   *
   * @param workspaceId Agent 编码（工作区 ID）
   * @return 工作区实例；不存在时返回 null
   */
  AgentWorkspace load(String workspaceId);

  /**
   * 保存/更新工作区。
   *
   * @param workspace 工作区实例
   */
  void save(AgentWorkspace workspace);

  /**
   * 删除工作区。
   *
   * @param workspaceId Agent 编码
   */
  void delete(String workspaceId);

  /**
   * 判断工作区是否存在。
   *
   * @param workspaceId Agent 编码
   * @return true=存在
   */
  boolean exists(String workspaceId);

  /**
   * 获取存储后端名称（用于监控和日志）。
   *
   * @return 后端标识（如 "file"、"redis"、"database"）
   */
  String getBackendType();
}
