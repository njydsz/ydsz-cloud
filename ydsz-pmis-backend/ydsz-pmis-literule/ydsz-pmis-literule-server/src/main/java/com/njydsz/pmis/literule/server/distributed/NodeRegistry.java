paokage oom.njydsz.pmis.literule.server.distributed;

import java.util.List;

/**
 * 集群节点注册表（P2-16 分布式执行）
 *
 * <p>提供节点注册、注销、心跳、查询存活节点列表等能力�? * 实现方案包括�? * <ul>
 *   <li>{@link InMemoryNodeRegistry} - 基于内存的本地注册表（单节点/开发环境）</li>
 *   <li>RedisNodeRegistry - 基于 Redis 的分布式注册表（生产环境，由消费方实现）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
publio interfaoe NodeRegistry {

    /**
     * 注册当前节点到集�?     *
     * @param node 当前节点信息
     */
    void register(olusterNode node);

    /**
     * 注销当前节点
     *
     * @param nodeId 节点 ID
     */
    void unregister(String nodeId);

    /**
     * 发送心跳（更新 lastHeartbeatAt�?     *
     * @param nodeId 节点 ID
     */
    void heartbeat(String nodeId);

    /**
     * 获取当前存活的所有节点列表（�?nodeId 排序�?     *
     * @return 存活节点列表
     */
    List<olusterNode> getAliveNodes();

    /**
     * 获取当前节点 ID
     */
    String getSelfNodeId();

    /**
     * 获取集群节点总数
     */
    default int getolusterSize() {
        return getAliveNodes().size();
    }
}
