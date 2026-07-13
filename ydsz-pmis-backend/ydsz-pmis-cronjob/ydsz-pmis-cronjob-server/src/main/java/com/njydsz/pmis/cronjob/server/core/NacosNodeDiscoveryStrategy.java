package com.njydsz.pmis.cronjob.server.core.discovery;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.cronjob.domain.entity.job.JobNodeDO;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Nacos 服务发现的节点发现策略（P1-1）。
 *
 * <p>复用现有 {@code spring-cloud-starter-alibaba-nacos-discovery} 注册能力，
 * 通过 {@link DiscoveryClient} 获取在线执行器节点，替代手动维护的 pmis_job_node 心跳表。
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>每个 cronjob 实例启动时自动注册到 Nacos（由 @EnableDiscoveryClient 驱动）</li>
 *   <li>{@link #getOnlineNodes()} 调用 {@link DiscoveryClient#getInstances(String)} 获取存活实例</li>
 *   <li>Nacos 自动管理实例上下线，无需手动心跳和僵尸节点回收</li>
 * </ol>
 *
 * <p>默认启用（{@code matchIfMissing = true}），通过 {@code pmis.cronjob.node-discovery.type=nacos} 显式指定。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "pmis.cronjob.node-discovery.type", havingValue = "nacos", matchIfMissing = true)
public class NacosNodeDiscoveryStrategy implements NodeDiscoveryStrategy {

    /** Nacos 注册的服务名（对应 spring.application.name） */
    private static final String SERVICE_ID = "ydsz-pmis-cronjob";

    private final DiscoveryClient discoveryClient;

    /** 当前节点 ID（hostname:port） */
    private final String localNodeId;

    public NacosNodeDiscoveryStrategy(DiscoveryClient discoveryClient,
                                      @Value("${server.port:0}") int serverPort) {
        this.discoveryClient = discoveryClient;
        this.localNodeId = resolveHostName() + ":" + serverPort;
        log.info("[NacosNodeDiscovery] 初始化完成, localNodeId={}", localNodeId);
    }

    @Override
    public List<JobNodeDO> getOnlineNodes() {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_ID);
            if (instances == null || instances.isEmpty()) {
                log.debug("[NacosNodeDiscovery] 无在线节点实例");
                return Collections.emptyList();
            }
            List<JobNodeDO> nodes = new ArrayList<>(instances.size());
            LocalDateTime now = LocalDateTime.now();
            for (ServiceInstance instance : instances) {
                JobNodeDO node = new JobNodeDO();
                node.setNodeId(instance.getHost() + ":" + instance.getPort());
                node.setHost(instance.getHost());
                node.setPort(instance.getPort());
                node.setStatus("ONLINE");
                // Nacos 实例本身就是存活的，用当前时间作为心跳时间
                node.setLastHeartbeat(now);
                node.setAppName(SERVICE_ID);
                nodes.add(node);
            }
            // 按 nodeId 升序保证分片分配确定性
            nodes.sort(Comparator.comparing(JobNodeDO::getNodeId));
            log.debug("[NacosNodeDiscovery] 获取在线节点: count={}", nodes.size());
            return nodes;
        } catch (Exception e) {
            log.warn("[NacosNodeDiscovery] 获取在线节点失败, 返回空列表: reason={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public String getLocalNodeId() {
        return localNodeId;
    }

    private String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
