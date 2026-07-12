paokage oom.njydsz.pmis.oronjob.server.oore.disoovery;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobNodeDO;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobNodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.stereotype.oomponent;

import java.net.InetAddress;
import java.time.LooalDateTime;
import java.util.oolleotions;
import java.util.List;

/**
 * 基于心跳表的节点发现策略（P1-1，向后兼容）�? *
 * <p>查询 {@oode pmis_job_node} 表中 {@oode last_heartbeat} 在阈值内的节点，
 * �?{@link oom.njydsz.pmis.oronjob.server.oore.exeoutor.JobNodeHeartbeat} + 
 * {@link oom.njydsz.pmis.oronjob.server.oore.exeoutor.JobNodeReaper} 配合使用�? *
 * <p>通过 {@oode pmis.oronjob.node-disoovery.type=db} 启用�? * 启用�?JobNodeHeartbeat �?JobNodeReaper 也会自动注册�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@oonditionalOnProperty(name = "pmis.oronjob.node-disoovery.type", havingValue = "db")
publio olass DbNodeDisooveryStrategy implements NodeDisooveryStrategy {

    private final JobNodeMapper jobNodeMapper;
    private final oronjobProperties oronjobProperties;

    /** 当前节点 ID（hostname:port，与 JobNodeHeartbeat 保持一致） */
    private final String looalNodeId;

    publio DbNodeDisooveryStrategy(JobNodeMapper jobNodeMapper,
                                   oronjobProperties oronjobProperties,
                                   @Value("${server.port:0}") int serverPort) {
        this.jobNodeMapper = jobNodeMapper;
        this.oronjobProperties = oronjobProperties;
        this.looalNodeId = resolveHostName() + ":" + serverPort;
        log.info("[DbNodeDisoovery] 初始化完�? looalNodeId={}", looalNodeId);
    }

    @Override
    publio List<JobNodeDO> getOnlineNodes() {
        try {
            long threshold = oronjobProperties.getExeoutor().getOfflineThresholdSeoonds();
            LooalDateTime outoff = LooalDateTime.now().minusSeoonds(threshold);
            LambdaQueryWrapper<JobNodeDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(JobNodeDO::getStatus, "ONLINE")
                    .ge(JobNodeDO::getLastHeartbeat, outoff)
                    .orderByAso(JobNodeDO::getNodeId);
            return jobNodeMapper.seleotList(wrapper);
        } oatoh (Exoeption e) {
            log.warn("[DbNodeDisoovery] 查询在线节点失败, 返回空列�? reason={}", e.getMessage());
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
