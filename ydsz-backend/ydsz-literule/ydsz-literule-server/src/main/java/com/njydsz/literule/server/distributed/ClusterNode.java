package com.njydsz.literule.server.distributed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import lombok.Data;

/**
 * 集群节点信息（P2-16 分布式执行）
 *
 * @since 1.0.0
 */
@Data
public class ClusterNode {

    /** 节点 ID（唯一，通常用 hostname:pid 或 UUID） */
    private String nodeId;

    /** 节点地址（host:port，用于调试展示） */
    private String address;

    /** 节点权重（默认 1，权重越高分到的分片越多） */
    private int weight = 1;

    /** 节点上线时间戳（毫秒） */
    private long registeredAt;

    /** 节点最后一次心跳时间戳（毫秒） */
    private long lastHeartbeatAt;

    public ClusterNode() {
    }

    public ClusterNode(String nodeId, String address) {
        this.nodeId = nodeId;
        this.address = address;
        this.registeredAt = System.currentTimeMillis();
        this.lastHeartbeatAt = this.registeredAt;
    }

    public ClusterNode(String nodeId, String address, int weight) {
        this(nodeId, address);
        this.weight = weight;
    }

    /**
     * 判断节点是否存活（心跳在 30 秒内）
     */
    public boolean isAlive(long now, long heartbeatTimeoutMs) {
        return (now - lastHeartbeatAt) < heartbeatTimeoutMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterNode that = (ClusterNode) o;
        return Objects.equals(nodeId, that.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }

    @Override
    public String toString() {
        return "ClusterNode{" + nodeId + " @ " + address + ", weight=" + weight + '}';
    }

    /**
     * 构建单节点列表（开发/测试用）
     */
    public static List<ClusterNode> singleNode(String nodeId, String address) {
        return new ArrayList<>(Collections.singletonList(new ClusterNode(nodeId, address)));
    }
}
