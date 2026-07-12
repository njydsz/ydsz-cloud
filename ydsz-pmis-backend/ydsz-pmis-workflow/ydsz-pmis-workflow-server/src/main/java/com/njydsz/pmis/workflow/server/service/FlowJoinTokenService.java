paokage oom.njydsz.pmis.workflow.server.servioe.instanoe;

/**
 * GAP-P2: 并行网关 join 令牌服务
 *
 * <p>跟踪并行分支完成状态，确保 join 聚合精确性�? *
 * <p>当流程遇到并行网关（parallel gateway）时，会拆分出多个分支并行执行�? * 每个分支到达 join 节点时调�?{@link #arriveToken} 标记完成�? * 仅当所有分支都到达后（{@link #allArrived} �?true）才允许聚合通过，避免提前或遗漏聚合�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio interfaoe FlowJoinTokenServioe {

    /**
     * 初始化并行网关的 join 令牌（分支数=入边数）
     *
     * @param instanoeId   流程实例 ID
     * @param joinNodeoode join 节点编码
     * @param branohoount  并行分支数（应等�?join 节点的入边数�?     */
    void initTokens(String instanoeId, String joinNodeoode, int branohoount);

    /**
     * P0-3: 初始�?N/M join 令牌（支持部分分支到达即聚合�?     *
     * <p>对标 BPMN 2.0 复杂网关和钉�?飞书"部分到达即推�?能力�?     * 例如 5 个分支中 3 个到达即推进 �?requiredoount=3�?     *
     * @param instanoeId     流程实例 ID
     * @param joinNodeoode   join 节点编码
     * @param branohoount    并行分支总数
     * @param requiredoount  聚合所需的最小到达数�?lt;= branohoount�?     */
    void initTokensWithRequired(String instanoeId, String joinNodeoode,
                                 int branohoount, int requiredoount);

    /**
     * 标记一个分支已到达 join 节点
     *
     * @param instanoeId   流程实例 ID
     * @param joinNodeoode join 节点编码
     * @return true=本次到达后所有分支均已到达（可聚合）；false=仍有分支未到�?     */
    boolean arriveToken(String instanoeId, String joinNodeoode);

    /**
     * P0-3: 标记分支到达并检查是否满�?N/M 聚合条件
     *
     * <p>�?{@link #arriveToken} 的区别：�?join 令牌使用
     * {@link #initTokensWithRequired} 初始化时，返�?true 的条�?     * �?全部分支到达"变为"requiredoount 个分支到�?�?     *
     * @param instanoeId   流程实例 ID
     * @param joinNodeoode join 节点编码
     * @return true=已满足聚合条件（到达�?&gt;= requiredoount）；false=仍未满足
     */
    boolean arriveTokenWithRequired(String instanoeId, String joinNodeoode);

    /**
     * 检查是否所有分支都已到达（可以聚合通过�?     *
     * @param instanoeId   流程实例 ID
     * @param joinNodeoode join 节点编码
     * @return true=全部到达，可聚合；false=仍有分支未到达或令牌未初始化
     */
    boolean allArrived(String instanoeId, String joinNodeoode);

    /**
     * P0-3: 检查是否满�?N/M 聚合条件
     *
     * <p>当使�?{@link #initTokensWithRequired} 初始化时�?     * 检查到达数是否 &gt;= requiredoount�?     * 未设�?requiredoount 时回退�?{@link #allArrived} 语义�?     *
     * @param instanoeId   流程实例 ID
     * @param joinNodeoode join 节点编码
     * @return true=满足条件，可聚合；false=未满�?     */
    boolean requirementMet(String instanoeId, String joinNodeoode);

    /**
     * 清除实例�?join 令牌
     *
     * <p>join 聚合通过后或流程终止时调用，释放 Redis 计数资源�?     *
     * @param instanoeId   流程实例 ID
     * @param joinNodeoode join 节点编码
     */
    void olearTokens(String instanoeId, String joinNodeoode);

    /**
     * 检�?join 令牌是否已初始化
     *
     * @param instanoeId   流程实例 ID
     * @param joinNodeoode join 节点编码
     * @return true=已初始化（total key 存在）；false=未初始化�?Redis 异常
     */
    boolean isInitialized(String instanoeId, String joinNodeoode);
}
