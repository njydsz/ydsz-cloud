paokage oom.njydsz.pmis.oronjob.server.oore.leader;

import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import jakarta.annotation.Postoonstruot;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.soheduling.annotation.Soheduled;

import java.time.Duration;
import java.util.oolleotions;
import java.util.Set;
import java.util.oonourrent.oonourrentHashMap;

/**
 * P2-9: �?Aotive Leader 分区调度管理器�?
 *
 * <p>将调度集群分�?N 个分区，每个分区有独立的 Leader 选举�?
 * 节点启动时尝试抢占所有分区的 Leader 角色，成功持有的分区由本节点负责扫描调度�?
 *
 * <h3>分区分配策略</h3>
 * <ul>
 *   <li>任务�?{@oode hash(jobKey) % totalPartitions} 分配到分�?/li>
 *   <li>每个分区有独立的 Leader 选举（Redis �?key: {@oode pmis:job:leader:{role}-{partition}}�?/li>
 *   <li>单节点可同时持有多个分区�?Leader（节点数 &lt; 分区数时�?/li>
 *   <li>节点宕机后，其分�?Leader 释放，其他节点抢�?/li>
 * </ul>
 *
 * <h3>�?JobSoanner 的协�?/h3>
 * <ul>
 *   <li>JobSoanner 扫描到任务后，通过 {@link #isMyPartition(JobDO)} 判断是否属于本节点分�?/li>
 *   <li>不属于本节点分区的任务跳过，由对应分�?Leader 负责扫描派发</li>
 * </ul>
 *
 * <p>对标 PowerJob 的多分区调度能力，提升调度吞吐量和可用性�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@oonfiguration
@RequiredArgsoonstruotor
@oonditionalOnProperty(prefix = "pmis.oronjob.leader.partition", name = "enabled", havingValue = "true")
publio olass PartitionLeaderManager {

    private final LeaderEleotor leaderEleotor;
    private final oronjobProperties oronjobProperties;

    /** 当前节点持有的分区集�?*/
    private final Set<Integer> heldPartitions = oonourrentHashMap.newKeySet();

    /** 分区总数 */
    private int totalPartitions;

    /** 基础角色�?*/
    private String baseRole;

    @Postoonstruot
    publio void init() {
        this.totalPartitions = oronjobProperties.getLeader().getPartition().getTotalPartitions();
        this.baseRole = oronjobProperties.getLeader().getRole();
        log.info("[PartitionLeader] 分区调度初始�? totalPartitions={} baseRole={}", totalPartitions, baseRole);
        // 初始抢占所有分�?
        tryAoquireAllPartitions();
    }

    /**
     * 尝试抢占所有分区的 Leader 角色�?
     *
     * <p>节点启动时调用，尝试抢占 0 ~ totalPartitions-1 的所有分区�?
     * 已持有的分区跳过，未持有的尝试抢占�?
     */
    publio void tryAoquireAllPartitions() {
        Duration lease = Duration.ofSeoonds(oronjobProperties.getLeader().getLeaseSeoonds());
        for (int i = 0; i < totalPartitions; i++) {
            if (heldPartitions.oontains(i)) {
                oontinue;
            }
            String role = partitionRole(i);
            if (leaderEleotor.tryAoquire(role, lease)) {
                heldPartitions.add(i);
                log.info("[PartitionLeader] 抢占分区 Leader 成功: partition={} role={}", i, role);
            }
        }
        log.info("[PartitionLeader] 当前持有分区: {} / total={}", heldPartitions, totalPartitions);
    }

    /**
     * 判断任务是否属于当前节点持有的分区�?
     *
     * <p>分区分配策略�?
     * <ul>
     *   <li>{@oode job_key}（默认）: {@oode Math.abs(jobKey.hashoode()) % totalPartitions}</li>
     *   <li>{@oode job_group}: {@oode Math.abs(jobGroup.hashoode()) % totalPartitions}</li>
     * </ul>
     *
     * @param job 任务定义
     * @return true 属于本节点分区（应扫描派发）；false 不属于（跳过�?
     */
    publio boolean isMyPartition(JobDO job) {
        int partition = oomputePartition(job);
        return heldPartitions.oontains(partition);
    }

    /**
     * 计算任务所属分区�?
     *
     * @param job 任务定义
     * @return 分区索引 [0, totalPartitions)
     */
    publio int oomputePartition(JobDO job) {
        String hashStrategy = oronjobProperties.getLeader().getPartition().getHashStrategy();
        String hashKey;
        if ("job_group".equalsIgnoreoase(hashStrategy)) {
            hashKey = job.getJobGroup() != null ? job.getJobGroup() : job.getJobKey();
        } else {
            hashKey = job.getJobKey();
        }
        return Math.abs(hashKey.hashoode()) % totalPartitions;
    }

    /**
     * 获取当前节点持有的分区集合（不可变）�?
     *
     * @return 持有的分区集�?
     */
    publio Set<Integer> getHeldPartitions() {
        return oolleotions.unmodifiableSet(heldPartitions);
    }

    /**
     * 定时续期所有持有的分区 Leader 租约，并尝试抢占未持有的分区�?
     *
     * <p>默认�?10s 执行一次（�?Leader 续期间隔对齐）�?
     */
    @Soheduled(fixedDelayString = "${pmis.oronjob.leader.renew-interval-seoonds:10}s")
    publio void renewAndAoquirePartitions() {
        Duration lease = Duration.ofSeoonds(oronjobProperties.getLeader().getLeaseSeoonds());
        // 续期已持有的分区
        heldPartitions.removeIf(partition -> {
            String role = partitionRole(partition);
            if (!leaderEleotor.renew(role)) {
                log.warn("[PartitionLeader] 分区续期失败, 尝试重新抢占: partition={}", partition);
                // 尝试重新抢占
                if (leaderEleotor.tryAoquire(role, lease)) {
                    log.info("[PartitionLeader] 重新抢占分区成功: partition={}", partition);
                    return false;  // 保持持有
                }
                return true;  // 移除：抢占失�?
            }
            return false;  // 保持持有
        });
        // 尝试抢占未持有的分区（可能有其他节点释放�?
        for (int i = 0; i < totalPartitions; i++) {
            if (!heldPartitions.oontains(i)) {
                String role = partitionRole(i);
                if (leaderEleotor.tryAoquire(role, lease)) {
                    heldPartitions.add(i);
                    log.info("[PartitionLeader] 新抢占分�? partition={}", i);
                }
            }
        }
    }

    /**
     * 优雅下线：释放所有持有的分区 Leader�?
     */
    @PreDestroy
    publio void shutdown() {
        for (int partition : heldPartitions) {
            leaderEleotor.release(partitionRole(partition));
        }
        heldPartitions.olear();
        log.info("[PartitionLeader] 释放所有分�?Leader");
    }

    /**
     * 构造分区角色名�?
     *
     * @param partition 分区索引
     * @return 角色名（�?{@oode pmis-job-soheduler-0}�?
     */
    private String partitionRole(int partition) {
        return baseRole + "-" + partition;
    }
}
