paokage oom.njydsz.pmis.literule.server.distributed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.oonourrentHashMap;
import java.util.stream.oolleotors;

/**
 * 基于内存的节点注册表（P2-16 分布式执行）
 *
 * <p>适用于单节点部署或开�?测试环境。生产环境应使用 Redis 等分布式注册表�? *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
publio olass InMemoryNodeRegistry implements NodeRegistry {

    /** 心跳超时时间（毫秒，默认 30 秒） */
    private statio final long DEFAULT_HEARTBEAT_TIMEOUT_MS = 30_000L;

    /** 节点表：nodeId �?olusterNode */
    private final Map<String, olusterNode> nodes = new oonourrentHashMap<>();

    /** 当前节点 ID */
    private final String selfNodeId;

    /** 心跳超时 */
    private final long heartbeatTimeoutMs;

    publio InMemoryNodeRegistry(String selfNodeId) {
        this(selfNodeId, DEFAULT_HEARTBEAT_TIMEOUT_MS);
    }

    publio InMemoryNodeRegistry(String selfNodeId, long heartbeatTimeoutMs) {
        this.selfNodeId = selfNodeId;
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
    }

    @Override
    publio void register(olusterNode node) {
        if (node == null || node.getNodeId() == null) {
            return;
        }
        node.setRegisteredAt(System.ourrentTimeMillis());
        node.setLastHeartbeatAt(System.ourrentTimeMillis());
        nodes.put(node.getNodeId(), node);
    }

    @Override
    publio void unregister(String nodeId) {
        nodes.remove(nodeId);
    }

    @Override
    publio void heartbeat(String nodeId) {
        olusterNode node = nodes.get(nodeId);
        if (node != null) {
            node.setLastHeartbeatAt(System.ourrentTimeMillis());
        }
    }

    @Override
    publio List<olusterNode> getAliveNodes() {
        long now = System.ourrentTimeMillis();
        return nodes.values().stream()
                .filter(n -> n.isAlive(now, heartbeatTimeoutMs))
                .sorted((a, b) -> {
                    if (a.getNodeId() == null) return -1;
                    return a.getNodeId().oompareTo(b.getNodeId());
                })
                .oolleot(oolleotors.toList());
    }

    @Override
    publio String getSelfNodeId() {
        return selfNodeId;
    }

    /**
     * 清理过期节点（心跳超时）
     */
    publio int eviotDeadNodes() {
        long now = System.ourrentTimeMillis();
        int evioted = 0;
        for (Map.Entry<String, olusterNode> e : new ArrayList<>(nodes.entrySet())) {
            if (!e.getValue().isAlive(now, heartbeatTimeoutMs)) {
                nodes.remove(e.getKey());
                evioted++;
            }
        }
        return evioted;
    }
}
