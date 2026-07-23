package com.njydsz.literule.server.distributed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.njydsz.common.json.YdszJson;

import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Redis 的集群节点注册表（生产环境实现）
 *
 * <p>利用 Redisson 的 {@code RMap} 存储节点信息，所有节点共享同一份注册表，
 * 实现跨实例的节点发现与心跳管理。
 *
 * <p>存储结构：
 * <ul>
 *   <li>Key: {@code literule:nodes} (Hash)</li>
 *   <li>Field: nodeId</li>
 *   <li>Value: ClusterNode JSON</li>
 * </ul>
 *
 * <p>心跳超时清理采用惰性删除策略：{@link #getAliveNodes()} 时过滤超时节点，
 * 不依赖后台定时任务，降低系统复杂度。
 *
 * @since 1.5.0
 */
public class RedisNodeRegistry implements NodeRegistry {

    private static final Logger log = LoggerFactory.getLogger(RedisNodeRegistry.class);

    /** Redis Hash key：存储所有节点信息 */
    private static final String NODES_KEY = "literule:nodes";

    /** 默认心跳超时时间（毫秒，30 秒） */
    private static final long DEFAULT_HEARTBEAT_TIMEOUT_MS = 30_000L;

    private final RedissonClient redissonClient;
    private final String selfNodeId;
    private final long heartbeatTimeoutMs;

    public RedisNodeRegistry(RedissonClient redissonClient, String selfNodeId) {
        this(redissonClient, selfNodeId, DEFAULT_HEARTBEAT_TIMEOUT_MS);
    }

    public RedisNodeRegistry(RedissonClient redissonClient, String selfNodeId, long heartbeatTimeoutMs) {
        this.redissonClient = redissonClient;
        this.selfNodeId = selfNodeId;
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
    }

    @Override
    public void register(ClusterNode node) {
        if (node == null || node.getNodeId() == null) {
            return;
        }
        node.setRegisteredAt(System.currentTimeMillis());
        node.setLastHeartbeatAt(System.currentTimeMillis());
        try {
            RMap<String, String> map = redissonClient.getMap(NODES_KEY);
            map.put(node.getNodeId(), YdszJson.toJson(node));
            log.info("[Distributed-Redis] 节点已注册: {}", node.getNodeId());
        } catch (Exception e) {
            log.warn("[Distributed-Redis] 节点注册失败: {}", e.getMessage());
        }
    }

    @Override
    public void unregister(String nodeId) {
        if (nodeId == null) return;
        try {
            RMap<String, String> map = redissonClient.getMap(NODES_KEY);
            map.remove(nodeId);
            log.info("[Distributed-Redis] 节点已注销: {}", nodeId);
        } catch (Exception e) {
            log.warn("[Distributed-Redis] 节点注销失败: {}", e.getMessage());
        }
    }

    @Override
    public void heartbeat(String nodeId) {
        if (nodeId == null) return;
        try {
            RMap<String, String> map = redissonClient.getMap(NODES_KEY);
            String json = map.get(nodeId);
            if (json != null) {
                ClusterNode node = YdszJson.toObject(json, ClusterNode.class);
                if (node != null) {
                    node.setLastHeartbeatAt(System.currentTimeMillis());
                    map.put(nodeId, YdszJson.toJson(node));
                }
            }
        } catch (Exception e) {
            log.debug("[Distributed-Redis] 心跳更新失败: {}", e.getMessage());
        }
    }

    @Override
    public List<ClusterNode> getAliveNodes() {
        try {
            RMap<String, String> map = redissonClient.getMap(NODES_KEY);
            long now = System.currentTimeMillis();
            List<ClusterNode> alive = new ArrayList<>();
            List<String> deadNodeIds = new ArrayList<>();

            for (Map.Entry<String, String> entry : map.entrySet()) {
                try {
                    ClusterNode node = YdszJson.fromJson(entry.getValue(), ClusterNode.class);
                    if (node == null || node.getNodeId() == null) {
                        deadNodeIds.add(entry.getKey());
                        continue;
                    }
                    if (node.isAlive(now, heartbeatTimeoutMs)) {
                        alive.add(node);
                    } else {
                        deadNodeIds.add(entry.getKey());
                    }
                } catch (Exception parseEx) {
                    log.debug("[Distributed-Redis] 节点信息解析失败: {}", parseEx.getMessage());
                    deadNodeIds.add(entry.getKey());
                }
            }

            // 惰性清理超时节点
            if (!deadNodeIds.isEmpty()) {
                map.fastRemove(deadNodeIds.toArray(new String[0]));
                log.debug("[Distributed-Redis] 清理超时节点: count={}", deadNodeIds.size());
            }

            // 按 nodeId 排序，保证一致性 hash 环稳定
            return alive.stream()
                    .sorted((a, b) -> {
                        if (a.getNodeId() == null) return -1;
                        if (b.getNodeId() == null) return 1;
                        return a.getNodeId().compareTo(b.getNodeId());
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[Distributed-Redis] 获取节点列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public String getSelfNodeId() {
        return selfNodeId;
    }

    /**
     * 强制清理超时节点（可选调用，用于主动清理）
     *
     * @return 清理的节点数
     */
    public int evictDeadNodes() {
        try {
            RMap<String, String> map = redissonClient.getMap(NODES_KEY);
            long now = System.currentTimeMillis();
            List<String> deadNodeIds = new ArrayList<>();

            for (Map.Entry<String, String> entry : map.entrySet()) {
                try {
                    ClusterNode node = YdszJson.fromJson(entry.getValue(), ClusterNode.class);
                    if (node == null || !node.isAlive(now, heartbeatTimeoutMs)) {
                        deadNodeIds.add(entry.getKey());
                    }
                } catch (Exception parseEx) {
                    deadNodeIds.add(entry.getKey());
                }
            }

            if (!deadNodeIds.isEmpty()) {
                map.fastRemove(deadNodeIds.toArray(new String[0]));
                log.info("[Distributed-Redis] 主动清理超时节点: count={}", deadNodeIds.size());
            }
            return deadNodeIds.size();
        } catch (Exception e) {
            log.warn("[Distributed-Redis] 清理超时节点失败: {}", e.getMessage());
            return 0;
        }
    }
}
