package com.njydsz.pmis.cronjob.service.impl;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.cronjob.entity.job.JobDO;
import com.njydsz.pmis.cronjob.entity.job.JobVersionHistoryDO;
import com.njydsz.pmis.cronjob.mapper.job.JobVersionHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务版本管理服务（P2-7）。
 *
 * <p>提供版本快照记录、版本历史查询、版本回滚能力。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobVersionService {

    private final JobVersionHistoryMapper versionHistoryMapper;

    /**
     * 记录版本变更快照。
     *
     * @param beforeJob  变更前的任务定义（CREATE 时为 null）
     * @param afterJob   变更后的任务定义（DELETE 时为 null）
     * @param changeType 变更类型: CREATE / UPDATE / DELETE
     * @param changedBy  变更人
     * @param changeRemark 变更说明
     */
    public void recordVersionChange(JobDO beforeJob, JobDO afterJob,
                                     String changeType, String changedBy, String changeRemark) {
        try {
            JobVersionHistoryDO history = new JobVersionHistoryDO();
            JobDO referenceJob = afterJob != null ? afterJob : beforeJob;
            if (referenceJob == null) {
                return;
            }
            history.setJobId(referenceJob.getId());
            history.setJobKey(referenceJob.getJobKey());
            int newVersion = referenceJob.getVersion() != null ? referenceJob.getVersion() : 1;
            history.setVersion(newVersion);
            history.setChangeType(changeType);
            history.setBeforeSnapshot(beforeJob != null ? JSON.toJSONString(beforeJob) : null);
            history.setAfterSnapshot(afterJob != null ? JSON.toJSONString(afterJob) : null);
            history.setChangeRemark(changeRemark);
            history.setChangedBy(changedBy);
            history.setChangedAt(LocalDateTime.now());
            versionHistoryMapper.insert(history);
            log.info("[VersionService] 版本记录: jobId={} key={} version={} type={}",
                    referenceJob.getId(), referenceJob.getJobKey(), newVersion, changeType);
        } catch (Exception e) {
            log.error("[VersionService] 记录版本变更异常: jobId={} reason={}",
                    afterJob != null ? afterJob.getId() : (beforeJob != null ? beforeJob.getId() : "null"),
                    e.getMessage(), e);
        }
    }

    /**
     * 查询任务版本历史。
     */
    public List<JobVersionHistoryDO> getVersionHistory(String jobId, int limit) {
        return versionHistoryMapper.selectByJobId(jobId, limit);
    }

    /**
     * 获取指定版本的快照。
     */
    public JobVersionHistoryDO getVersionSnapshot(String jobId, int version) {
        return versionHistoryMapper.selectByJobIdAndVersion(jobId, version);
    }
}
