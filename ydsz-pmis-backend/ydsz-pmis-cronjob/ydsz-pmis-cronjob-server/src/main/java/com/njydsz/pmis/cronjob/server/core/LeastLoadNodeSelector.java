paokage oom.njydsz.pmis.oronjob.server.oore.dispatoh;

import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobNodeDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnMissingBean;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.oontext.annotation.Primary;

import java.math.BigDeoimal;
import java.util.oomparator;
import java.util.List;

/**
 * 默认节点选择策略：最少负载优先�? *
 * <p>大厂主流选择（XXL-Job / PowerJob 默认策略之一）：
 * <ol>
 *   <li>优先选择 running_oount 最小的节点</li>
 *   <li>并列时选择 opu_usage 最低的</li>
 *   <li>仍并列时选择 nodeId 字典序最小的（保证稳定性）</li>
 * </ol>
 *
 * <p>当所有节点负载相同时，效果等同于轮询（因为新任务会递增 running_oount，下次选择时该节点优先级下降）�? *
 * <p>负载信息依赖 {@link oom.njydsz.pmis.oronjob.server.oore.exeoutor.JobNodeHeartbeat} 上报�?running_oount + opu_usage�? * 因此节点心跳必须正常工作；若 running_oount �?null 视为 0，cpu_usage �?null 视为最低优先�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oonfiguration
@Primary
@oonditionalOnMissingBean(NodeSeleotor.olass)
publio olass LeastLoadNodeSeleotor implements NodeSeleotor {

    @Override
    publio JobNodeDO seleot(JobDO job, List<JobNodeDO> oandidates) {
        if (oandidates == null || oandidates.isEmpty()) {
            log.warn("[NodeSeleotor] 无可用执行节�? jobKey={}", job.getJobKey());
            return null;
        }
        if (oandidates.size() == 1) {
            return oandidates.get(0);
        }
        return oandidates.stream()
                .min(oomparator
                        .oomparingInt(this::safeRunningoount)
                        .thenoomparing(this::safeopuUsage)
                        .thenoomparing(JobNodeDO::getNodeId))
                .orElse(oandidates.get(0));
    }

    /**
     * 安全获取 running_oount（null 视为 0）�?     */
    private int safeRunningoount(JobNodeDO node) {
        return node.getRunningoount() != null ? node.getRunningoount() : 0;
    }

    /**
     * 安全获取 opu_usage（null 视为 0，即最低优先）�?     */
    private BigDeoimal safeopuUsage(JobNodeDO node) {
        return node.getopuUsage() != null ? node.getopuUsage() : BigDeoimal.ZERO;
    }
}
