package com.njydsz.cronjob.server.core.discovery;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.cronjob.domain.entity.job.JobNode;
import com.njydsz.cronjob.infra.mapper.job.JobNodeMapper;
import com.njydsz.cronjob.server.config.CronjobProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于心跳表的节点发现策略（P1-1，向后兼容）。
 *
 * <p>查询 {@code ydsz_job_node} 表中 {@code last_heartbeat} 在阈值内的节点，
 * 与 {@link com.njydsz.cronjob.server.core.executor.JobNodeHeartbeat} + 
 * {@link com.njydsz.cronjob.server.core.executor.JobNodeReaper} 配合使用。
 *
 * <p>通过 {@code ydsz.cronjob.node-discovery.type=db} 启用，
 * 启用时 JobNodeHeartbeat 和 JobNodeReaper 也会自动注册。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ydsz.cronjob.node-discovery.type", havingValue = "db")
public class DbNodeDiscoveryStrategy implements NodeDiscoveryStrategy {

    private final JobNodeMapper jobNodeMapper;
    private final CronjobProperties cronjobProperties;

    /** 当前节点 ID（hostname:port，与 JobNodeHeartbeat 保持一致） */
    private final String localNodeId;

    public DbNodeDiscoveryStrategy(JobNodeMapper jobNodeMapper,
                                   CronjobProperties cronjobProperties,
                                   @Value("${server.port:0}") int serverPort) {
        this.jobNodeMapper = jobNodeMapper;
        this.cronjobProperties = cronjobProperties;
        this.localNodeId = resolveHostName() + ":" + serverPort;
        log.info("[DbNodeDiscovery] 初始化完成, localNodeId={}", localNodeId);
    }

    @Override
    public List<JobNode> getOnlineNodes() {
        try {
            long threshold = cronjobProperties.getExecutor().getOfflineThresholdSeconds();
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(threshold);
            LambdaQueryWrapper<JobNode> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(JobNode::getStatus, "ONLINE")
                    .ge(JobNode::getLastHeartbeat, cutoff)
                    .orderByAsc(JobNode::getNodeId);
            return jobNodeMapper.selectList(wrapper);
        } catch (Exception e) {
            log.warn("[DbNodeDiscovery] 查询在线节点失败, 返回空列表: reason={}", e.getMessage());
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
