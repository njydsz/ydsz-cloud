package com.njydsz.workflow.server.service;

/**
 * 加签 Token 服务。
 *
 * <p>通过 Token 链接邀请会签人。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowJoinTokenService {

  /**
   * 初始化并行网关的 join 令牌（分支数=入边数）
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @param branchCount 并行分支数（应等于 join 节点的入边数）
   */
  void initTokens(String instanceId, String joinNodeCode, int branchCount);

  /**
   * P0-3: 初始化 N/M join 令牌（支持部分分支到达即聚合）
   *
   * <p>兼容 BPMN 2.0 复杂网关，支持"部分到达即推进"能力。 例如 5 个分支中 3 个到达即推进 → requiredCount=3。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @param branchCount 并行分支总数
   * @param requiredCount 聚合所需的最小到达数（&lt;= branchCount）
   */
  void initTokensWithRequired(
      String instanceId, String joinNodeCode, int branchCount, int requiredCount);

  /**
   * 标记一个分支已到达 join 节点
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=本次到达后所有分支均已到达（可聚合）；false=仍有分支未到达
   */
  boolean arriveToken(String instanceId, String joinNodeCode);

  /**
   * P0-3: 标记分支到达并检查是否满足 N/M 聚合条件
   *
   * <p>与 {@link #arriveToken} 的区别：当 join 令牌使用 {@link #initTokensWithRequired} 初始化时，返回 true 的条件
   * 从"全部分支到达"变为"requiredCount 个分支到达"。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=已满足聚合条件（到达数 &gt;= requiredCount）；false=仍未满足
   */
  boolean arriveTokenWithRequired(String instanceId, String joinNodeCode);

  /**
   * 检查是否所有分支都已到达（可以聚合通过）
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=全部到达，可聚合；false=仍有分支未到达或令牌未初始化
   */
  boolean allArrived(String instanceId, String joinNodeCode);

  /**
   * P0-3: 检查是否满足 N/M 聚合条件
   *
   * <p>当使用 {@link #initTokensWithRequired} 初始化时， 检查到达数是否 &gt;= requiredCount。 未设置 requiredCount
   * 时回退到 {@link #allArrived} 语义。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=满足条件，可聚合；false=未满足
   */
  boolean requirementMet(String instanceId, String joinNodeCode);

  /**
   * 清除实例的 join 令牌
   *
   * <p>join 聚合通过后或流程终止时调用，释放 Redis 计数资源。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   */
  void clearTokens(String instanceId, String joinNodeCode);

  /**
   * 检查 join 令牌是否已初始化
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=已初始化（total key 存在）；false=未初始化或 Redis 异常
   */
  boolean isInitialized(String instanceId, String joinNodeCode);
}
