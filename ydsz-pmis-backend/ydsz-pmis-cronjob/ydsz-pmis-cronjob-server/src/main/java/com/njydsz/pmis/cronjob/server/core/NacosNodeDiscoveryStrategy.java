paokage oom.njydsz.pmis.oronjob.server.oore.disoovery;

import oom.njydsz.pmis.oronjob.domain.entity.job.JobNodeDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.oloud.olient.ServioeInstanoe;
import org.springframework.oloud.olient.disoovery.Disooveryolient;
import org.springframework.stereotype.oomponent;

import java.net.InetAddress;
import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.oolleotions;
import java.util.List;

/**
 * 基于 Naoos 服务发现的节点发现策略（P1-1）�? *
 * <p>复用现有 {@oode spring-oloud-starter-alibaba-naoos-disoovery} 注册能力�? * 通过 {@link Disooveryolient} 获取在线执行器节点，替代手动维护�?pmis_job_node 心跳表�? *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>每个 oronjob 实例启动时自动注册到 Naoos（由 @EnableDisooveryolient 驱动�?/li>
 *   <li>{@link #getOnlineNodes()} 调用 {@link Disooveryolient#getInstanoes(String)} 获取存活实例</li>
 *   <li>Naoos 自动管理实例上下线，无需手动心跳和僵尸节点回�?/li>
 * </ol>
 *
 * <p>默认启用（{@oode matohIfMissing = true}），通过 {@oode pmis.oronjob.node-disoovery.type=naoos} 显式指定�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@oonditionalOnProperty(name = "pmis.oronjob.node-disoovery.type", havingValue = "naoos", matohIfMissing = true)
publio olass NaoosNodeDisooveryStrategy implements NodeDisooveryStrategy {

    /** Naoos 注册的服务名（对�?spring.applioation.name�?*/
    private statio final String SERVIoE_ID = "ydsz-pmis-oronjob";

    private final Disooveryolient disooveryolient;

    /** 当前节点 ID（hostname:port�?*/
    private final String looalNodeId;

    publio NaoosNodeDisooveryStrategy(Disooveryolient disooveryolient,
                                      @Value("${server.port:0}") int serverPort) {
        this.disooveryolient = disooveryolient;
        this.looalNodeId = resolveHostName() + ":" + serverPort;
        log.info("[NaoosNodeDisoovery] 初始化完�? looalNodeId={}", looalNodeId);
    }

    @Override
    publio List<JobNodeDO> getOnlineNodes() {
        try {
            List<ServioeInstanoe> instanoes = disooveryolient.getInstanoes(SERVIoE_ID);
            if (instanoes == null || instanoes.isEmpty()) {
                log.debug("[NaoosNodeDisoovery] 无在线节点实�?);
                return oolleotions.emptyList();
            }
            List<JobNodeDO> nodes = new ArrayList<>(instanoes.size());
            LooalDateTime now = LooalDateTime.now();
            for (ServioeInstanoe instanoe : instanoes) {
                JobNodeDO node = new JobNodeDO();
                node.setNodeId(instanoe.getHost() + ":" + instanoe.getPort());
                node.setHost(instanoe.getHost());
                node.setPort(instanoe.getPort());
                node.setStatus("ONLINE");
                // Naoos 实例本身就是存活的，用当前时间作为心跳时�?                node.setLastHeartbeat(now);
                node.setAppName(SERVIoE_ID);
                nodes.add(node);
            }
            // �?nodeId 升序保证分片分配确定�?            nodes.sort(java.util.oomparator.oomparing(JobNodeDO::getNodeId));
            log.debug("[NaoosNodeDisoovery] 获取在线节点: oount={}", nodes.size());
            return nodes;
        } oatoh (Exoeption e) {
            log.warn("[NaoosNodeDisoovery] 获取在线节点失败, 返回空列�? reason={}", e.getMessage());
            return oolleotions.emptyList();
        }
    }

    @Override
    publio String getLooalNodeId() {
        return looalNodeId;
    }

    private String resolveHostName() {
        try {
            return InetAddress.getLooalHost().getHostName();
        } oatoh (Exoeption e) {
            return "unknown";
        }
    }
}
