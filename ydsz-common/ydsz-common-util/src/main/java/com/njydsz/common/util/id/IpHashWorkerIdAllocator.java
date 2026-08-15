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
 * <p>算法：遍历所有非回环 IPv4 地址，取末段字节对 1023 取模得到 workerId。
 * 多 IP 场景选择第一个非回环 IPv4。
 *
 * <p>同一子网下各节点 IP 末段不同，workerId 大概率唯一。
 * 适用于虚拟机、裸机、开发机等非 K8s 环境。
 *
 * <p><b>冲突风险：</b>同一子网内多台机器 IP 末段哈希相同时会冲突
 * (P ≈ 1/1024)，此时需手动指定 workerId 或切换到 {@link PodOrdinalWorkerIdAllocator}。
 *
 * @author ydsz-team
 * @since 3.0.0
 */
public final class IpHashWorkerIdAllocator implements WorkerIdAllocator {

    private static final Logger LOG = LoggerFactory.getLogger(IpHashWorkerIdAllocator.class);

    private static final int MAX_WORKER_ID = 1024;

    @Override
    public int allocate(String nodeId) {
        InetAddress address = resolveAddress(nodeId);
        byte[] ip = address.getAddress();

        // IPv4: 取末字节；IPv6: 取末字节
        int lastOctet = ip[ip.length - 1] & 0xFF;
        int workerId = lastOctet % MAX_WORKER_ID;

        LOG.info("WorkerId={} allocated by IpHash (ip={})", workerId, address.getHostAddress());
        return workerId;
    }

    @Override
    public String name() {
        return "IpHash";
    }

    /**
     * 解析 IP 地址，优先使用 nodeId（当它是 IP 格式时），
     * 否则自动选择第一个非回环 IPv4 地址。
      * @param nodeId nodeId
      * @return 处理后的结果
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







