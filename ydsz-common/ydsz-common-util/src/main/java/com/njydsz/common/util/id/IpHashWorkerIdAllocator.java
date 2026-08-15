package com.njydsz.common.util.id;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 IP 地址哈希的 WorkerId 分配器。
 *
 * <p>算法：对完整 IP 字节序列做 FNV-1a 哈希，映射到 [0, 1024) 区间。
 * 相比仅取末段字节（只能产生 0~255），全 IP 哈希将 workerId 均匀分布到 0~1023，
 * 显著降低跨子网/同末字节场景的冲突概率。
 *
 * <p>适用场景：虚拟机、裸机、开发机等非 K8s 环境（作为 {@link PodOrdinalWorkerIdAllocator} 的兜底）。
 *
 * <p><b>冲突风险与边界：</b>本策略基于哈希，无法保证全局唯一。
 * 生产环境如需强唯一性，应优先使用 {@link PodOrdinalWorkerIdAllocator}（StatefulSet 序数，确定性）
 * 或接入外部协调器（Redis/DB 注册表）通过 {@link WorkerIdAllocatorChain#prepend} 前置。
 *
 * @author ydsz-team
 * @since 3.0.0
 */
public final class IpHashWorkerIdAllocator implements WorkerIdAllocator {

    private static final Logger LOG = LoggerFactory.getLogger(IpHashWorkerIdAllocator.class);

    private static final int MAX_WORKER_ID = 1024;

    /** FNV-1a 偏移基准（标准算法常数，非魔法值） */
    private static final int FNV_OFFSET_BASIS = 0x811c9dc5;

    /** FNV-1a 质数乘数（标准算法常数，非魔法值） */
    private static final int FNV_PRIME = 0x01000193;

    @Override
    public int allocate(String nodeId) {
        InetAddress address = resolveAddress(nodeId);
        int workerId = hashToWorkerId(address.getAddress());

        LOG.info("WorkerId={} allocated by IpHash (ip={})", workerId, address.getHostAddress());
        return workerId;
    }

    @Override
    public String name() {
        return "IpHash";
    }

    /**
     * 将 IP 字节序列哈希到 [0, 1024) 区间。
     *
     * <p>使用 FNV-1a 32-bit 哈希（无符号取模），保证 workerId 在区间内均匀分布。
     *
     * @param ip IP 地址字节（IPv4 为 4 字节，IPv6 为 16 字节）
     * @return 0 ~ 1023 之间的 workerId
     */
    private int hashToWorkerId(byte[] ip) {
        int hash = FNV_OFFSET_BASIS;
        for (byte b : ip) {
            hash ^= (b & 0xFF);
            hash *= FNV_PRIME;
        }
        return (hash & 0x7FFFFFFF) % MAX_WORKER_ID;
    }

    /**
     * 解析 IP 地址，优先使用 nodeId（当它是 IP 格式时），否则自动选择第一个非回环 IPv4 地址。
     *
     * @param nodeId 节点标识（hostname 或 IP）
     * @return 解析到的 InetAddress
     */
    private InetAddress resolveAddress(String nodeId) {
        // 尝试将 nodeId 解析为 IP
        if (nodeId != null && !nodeId.isBlank()) {
            try {
                return InetAddress.getByName(nodeId);
            } catch (UnknownHostException ignored) {
                // nodeId 不是 IP 格式，继续自动发现
            }
        }

        // 自动选择：首个非回环 IPv4 地址
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.getAddress().length == 4 && !addr.isLoopbackAddress()) {
                        return addr;
                    }
                }
            }
        } catch (SocketException e) {
            LOG.warn("Failed to enumerate network interfaces: {}", e.getMessage());
        }

        // fallback: InetAddress.getLocalHost()
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new WorkerIdExhaustedException("Cannot resolve local IP address", e);
        }
    }
}
