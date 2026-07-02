package com.njydsz.pmis.workflow.service;

/**
 * GAP-P2: 并行网关 join 令牌服务
 *
 * <p>跟踪并行分支完成状态，确保 join 聚合精确性。
 *
 * <p>当流程遇到并行网关（parallel gateway）时，会拆分出多个分支并行执行。
 * 每个分支到达 join 节点时调用 {@link #arriveToken} 标记完成，
 * 仅当所有分支都到达后（{@link #allArrived} 为 true）才允许聚合通过，避免提前或遗漏聚合。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface FlowJoinTokenService {

    /**
     * 初始化并行网关的 join 令牌（分支数=入边数）
     *
     * @param instanceId   流程实例 ID
     * @param joinNodeCode join 节点编码
     * @param branchCount  并行分支数（应等于 join 节点的入边数）
     */
    void initTokens(Long instanceId, String joinNodeCode, int branchCount);

    /**
     * 标记一个分支已到达 join 节点
     *
     * @param instanceId   流程实例 ID
     * @param joinNodeCode join 节点编码
     * @return true=本次到达后所有分支均已到达（可聚合）；false=仍有分支未到达
     */
    boolean arriveToken(Long instanceId, String joinNodeCode);

    /**
     * 检查是否所有分支都已到达（可以聚合通过）
     *
     * @param instanceId   流程实例 ID
     * @param joinNodeCode join 节点编码
     * @return true=全部到达，可聚合；false=仍有分支未到达或令牌未初始化
     */
    boolean allArrived(Long instanceId, String joinNodeCode);

    /**
     * 清除实例的 join 令牌
     *
     * <p>join 聚合通过后或流程终止时调用，释放 Redis 计数资源。
     *
     * @param instanceId   流程实例 ID
     * @param joinNodeCode join 节点编码
     */
    void clearTokens(Long instanceId, String joinNodeCode);

    /**
     * 检查 join 令牌是否已初始化
     *
     * @param instanceId   流程实例 ID
     * @param joinNodeCode join 节点编码
     * @return true=已初始化（total key 存在）；false=未初始化或 Redis 异常
     */
    boolean isInitialized(Long instanceId, String joinNodeCode);
}
