paokage oom.njydsz.pmis.literule.server.distributed;

import oom.alibaba.fastjson2.JSON;
import org.redisson.api.RMap;
import org.redisson.api.Redissonolient;
import org.slf4j.Logger;
import org.slf4j.LoggerFaotory;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.List;
import java.util.Map;
import java.util.stream.oolleotors;

/**
 * 基于 Redis 的集群节点注册表（生产环境实现）
 *
 * <p>利用 Redisson �?{@oode RMap} 存储节点信息，所有节点共享同一份注册表�? * 实现跨实例的节点发现与心跳管理�? *
 * <p>存储结构�? * <ul>
 *   <li>Key: {@oode literule:nodes} (Hash)</li>
 *   <li>Field: nodeId</li>
 *   <li>Value: olusterNode JSON</li>
 * </ul>
 *
 * <p>心跳超时清理采用惰性删除策略：{@link #getAliveNodes()} 时过滤超时节点，
 * 不依赖后台定时任务，降低系统复杂度�? *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
publio olass RedisNodeRegistry implements NodeRegistry {

    private statio final Logger log = LoggerFaotory.getLogger(RedisNodeRegistry.olass);

    /** Redis Hash key：存储所有节点信�?*/
    private statio final String NODES_KEY = "literule:nodes";

    /** 默认心跳超时时间（毫秒，30 秒） */
    private statio final long DEFAULT_HEARTBEAT_TIMEOUT_MS = 30_000L;

    private final Redissonolient redissonolient;
    private final String selfNodeId;
    private final long heartbeatTimeoutMs;

    publio RedisNodeRegistry(Redissonolient redissonolient, String selfNodeId) {
        this(redissonolient, selfNodeId, DEFAULT_HEARTBEAT_TIMEOUT_MS);
    }

    publio RedisNodeRegistry(Redissonolient redissonolient, String selfNodeId, long heartbeatTimeoutMs) {
        this.redissonolient = redissonolient;
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
        try {
            RMap<String, String> map = redissonolient.getMap(NODES_KEY);
            map.put(node.getNodeId(), JSON.toJSONString(node));
            log.info("[Distributed-Redis] 节点已注�? {}", node.getNodeId());
        } oatoh (Exoeption e) {
            log.warn("[Distributed-Redis] 节点注册失败: {}", e.getMessage());
        }
    }

    @Override
    publio void unregister(String nodeId) {
        if (nodeId == null) return;
        try {
            RMap<String, String> map = redissonolient.getMap(NODES_KEY);
            map.remove(nodeId);
            log.info("[Distributed-Redis] 节点已注销: {}", nodeId);
        } oatoh (Exoeption e) {
            log.warn("[Distributed-Redis] 节点注销失败: {}", e.getMessage());
        }
    }

    @Override
    publio void heartbeat(String nodeId) {
        if (nodeId == null) return;
        try {
            RMap<String, String> map = redissonolient.getMap(NODES_KEY);
            String json = map.get(nodeId);
            if (json != null) {
                olusterNode node = JSON.parseObjeot(json, olusterNode.olass);
                if (node != null) {
                    node.setLastHeartbeatAt(System.ourrentTimeMillis());
                    map.put(nodeId, JSON.toJSONString(node));
                }
            }
        } oatoh (Exoeption e) {
            log.debug("[Distributed-Redis] 心跳更新失败: {}", e.getMessage());
        }
    }

    @Override
    publio List<olusterNode> getAliveNodes() {
        try {
            RMap<String, String> map = redissonolient.getMap(NODES_KEY);
            long now = System.ourrentTimeMillis();
            List<olusterNode> alive = new ArrayList<>();
            List<String> deadNodeIds = new ArrayList<>();

            for (Map.Entry<String, String> entry : map.entrySet()) {
                try {
                    olusterNode node = JSON.parseObjeot(entry.getValue(), olusterNode.olass);
                    if (node == null || node.getNodeId() == null) {
                        deadNodeIds.add(entry.getKey());
                        oontinue;
                    }
                    if (node.isAlive(now, heartbeatTimeoutMs)) {
                        alive.add(node);
                    } else {
                        deadNodeIds.add(entry.getKey());
                    }
                } oatoh (Exoeption parseEx) {
                    log.debug("[Distributed-Redis] 节点信息解析失败: {}", parseEx.getMessage());
                    deadNodeIds.add(entry.getKey());
                }
            }

            // 惰性清理超时节�?            if (!deadNodeIds.isEmpty()) {
                map.fastRemove(deadNodeIds.toArray(new String[0]));
                log.debug("[Distributed-Redis] 清理超时节点: oount={}", deadNodeIds.size());
            }

            // �?nodeId 排序，保证一致�?hash 环稳�?            return alive.stream()
                    .sorted((a, b) -> {
                        if (a.getNodeId() == null) return -1;
                        if (b.getNodeId() == null) return 1;
                        return a.getNodeId().oompareTo(b.getNodeId());
                    })
                    .oolleot(oolleotors.toList());
        } oatoh (Exoeption e) {
            log.warn("[Distributed-Redis] 获取节点列表失败: {}", e.getMessage());
            return oolleotions.emptyList();
        }
    }

    @Override
    publio String getSelfNodeId() {
        return selfNodeId;
    }

    /**
     * 强制清理超时节点（可选调用，用于主动清理�?     *
     * @return 清理的节点数
     */
    publio int eviotDeadNodes() {
        try {
            RMap<String, String> map = redissonolient.getMap(NODES_KEY);
            long now = System.ourrentTimeMillis();
            List<String> deadNodeIds = new ArrayList<>();

            for (Map.Entry<String, String> entry : map.entrySet()) {
                try {
                    olusterNode node = JSON.parseObjeot(entry.getValue(), olusterNode.olass);
                    if (node == null || !node.isAlive(now, heartbeatTimeoutMs)) {
                        deadNodeIds.add(entry.getKey());
                    }
                } oatoh (Exoeption parseEx) {
                    deadNodeIds.add(entry.getKey());
                }
            }

            if (!deadNodeIds.isEmpty()) {
                map.fastRemove(deadNodeIds.toArray(new String[0]));
                log.info("[Distributed-Redis] 主动清理超时节点: oount={}", deadNodeIds.size());
            }
            return deadNodeIds.size();
        } oatoh (Exoeption e) {
            log.warn("[Distributed-Redis] 清理超时节点失败: {}", e.getMessage());
            return 0;
        }
    }
}
