package com.njydsz.workflow.domain.repository;

/**
 * 并行网关 join token 仓储接口（domain 层契约）。
 *
 * <p>定义 join token 持久化的领域抽象，隔离领域模型与具体数据访问技术实现。
 * 运行中并行网关的分支到达状态通过本接口持久化到数据库，确保服务重启后可恢复。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力（init / arrive / complete / cancel）</li>
 *   <li>实现由 infra 层提供，运行时由 Spring 注入</li>
 *   <li>server 层仅依赖本接口，不感知具体持久化技术</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.08
 */
public interface FlowJoinTokenRepository {

  /**
   * 初始化 join token（幂等）。
   *
   * <p>如果已存在有效的 PENDING token 则跳过。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @param totalBranches 总分支数
   * @param requiredBranches 所需到达数（0 表示全部分支）
   * @return token ID；失败返回 null
   */
  String initTokens(
      String instanceId, String joinNodeCode, int totalBranches, int requiredBranches);

  /**
   * 标记一个分支到达：原子递增到达计数。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return 递增后的到达计数；-1 表示 token 不存在或异常
   */
  int arriveToken(String instanceId, String joinNodeCode);

  /**
   * 判断 join token 是否已满足聚合条件。
   *
   * <p>requiredBranches=0 时退化为全部分支到达语义。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=已满足聚合条件
   */
  boolean isComplete(String instanceId, String joinNodeCode);

  /**
   * 标记 join token 为已完成（CAS 更新）。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=更新成功
   */
  boolean markCompleted(String instanceId, String joinNodeCode);

  /**
   * 标记 join token 为已取消（流程终止时）。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return 受影响行数
   */
  int markCancelled(String instanceId, String joinNodeCode);

  /**
   * 取消实例下所有 PENDING 状态的 join token。
   *
   * @param instanceId 流程实例 ID
   * @return 受影响行数
   */
  int cancelAllPending(String instanceId);

  /**
   * 检查 join token 是否已初始化且为 PENDING 状态。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=已初始化且为 PENDING 状态
   */
  boolean isInitialized(String instanceId, String joinNodeCode);

  /**
   * 查询当前到达计数。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return 到达计数；-1 表示记录不存在
   */
  int getArrivedCount(String instanceId, String joinNodeCode);
}
