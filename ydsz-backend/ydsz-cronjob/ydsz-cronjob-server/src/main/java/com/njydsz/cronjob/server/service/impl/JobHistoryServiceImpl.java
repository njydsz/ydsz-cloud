package com.njydsz.cronjob.server.service.impl.job;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.njydsz.common.json.YdszJson;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.domain.entity.job.JobHistory;
import com.njydsz.cronjob.infra.mapper.job.JobHistoryMapper;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.server.service.job.JobHistoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务历史记录服务实现。
 *
 * <p>维护任务的执行历史归档 ({@code ydsz_job_history})：触发时间、结束时间、耗时、状态、调度节点、
 *
 * <p>触发方式（手动/自动/重试）、关联调度计划。
 *
 * <p>支持按时间窗/状态/调度计划/执行人等多维度查询与清理（保留 N 个月后归档至 OSS）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class JobHistoryServiceImpl implements JobHistoryService {

    /** 任务历史版本 Mapper */
    private final JobHistoryMapper jobHistoryMapper;
    /** 任务定义 Mapper（回滚时更新当前配置） */
    private final JobMapper jobMapper;

    /** 需要对比的配置字段及其展示名（顺序保持一致便于前端渲染） */
    private static final List<String> COMPARE_FIELDS = List.of(
            "jobName", "jobGroup", "handler", "cronExpression",
            "scheduleType", "fixedRateMs", "fixedDelayMs",
            "paramsJson", "status", "remark",
            "lockTtlMs", "timeoutMs", "slowThresholdMs",
            "misfirePolicy", "shardTotal", "jobType",
            "maxRetries", "retryIntervalMs", "retryBackoff",
            "blockStrategy", "maxConsecutiveFails", "autoResumeAfterMinutes",
            "priority"
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobHistory saveHistory(Job job, String changedBy) {
        if (job == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_history_job_required");
        }
        JobHistory history = new JobHistory();
        history.setJobId(job.getId());
        history.setVersion(job.getVersion());
        history.setSnapshot(YdszJson.toJson(job));
        history.setChangeType("UPDATE");
        history.setJobName(job.getJobName());
        history.setJobKey(job.getJobKey());
        history.setHandler(job.getHandler());
        history.setCronExpression(job.getCronExpression());
        history.setParamsJson(job.getParamsJson());
        history.setRemark(job.getRemark());
        history.setChangedBy(StringUtils.hasText(changedBy) ? changedBy : "SYSTEM");
        history.setChangedAt(LocalDateTime.now());
        history.setDeleted(0);
        jobHistoryMapper.insert(history);
        log.info("[History] 保存任务历史版本: jobId={} version={}", job.getId(), job.getVersion());
        return history;
    }

    @Override
    public void recordVersionChange(Job beforeJob, Job afterJob,
                                      String changeType, String changedBy, String changeRemark) {
        try {
            Job referenceJob = afterJob != null ? afterJob : beforeJob;
            if (referenceJob == null) {
                return;
            }
            JobHistory history = new JobHistory();
            history.setJobId(referenceJob.getId());
            history.setVersion(referenceJob.getVersion() != null ? referenceJob.getVersion() : 1);
            history.setChangeType(changeType);
            history.setSnapshot(afterJob != null ? YdszJson.toJson(afterJob) : null);
            history.setBeforeSnapshot(beforeJob != null ? YdszJson.toJson(beforeJob) : null);
            history.setChangeRemark(changeRemark);
            // 冗余字段从 afterJob 取（DELETE 时从 beforeJob 取；referenceJob 已保证非 null）
            Job displayJob = referenceJob;
            history.setJobName(displayJob.getJobName());
            history.setJobKey(displayJob.getJobKey());
            history.setHandler(displayJob.getHandler());
            history.setCronExpression(displayJob.getCronExpression());
            history.setParamsJson(displayJob.getParamsJson());
            history.setRemark(displayJob.getRemark());
            history.setChangedBy(StringUtils.hasText(changedBy) ? changedBy : "SYSTEM");
            history.setChangedAt(LocalDateTime.now());
            history.setDeleted(0);
            jobHistoryMapper.insert(history);
            log.info("[History] 版本记录: jobId={} key={} version={} type={}",
                    referenceJob.getId(), referenceJob.getJobKey(),
                    history.getVersion(), changeType);
        } catch (Exception e) {
            log.error("[History] 记录版本变更异常: jobId={} reason={}",
                    afterJob != null ? afterJob.getId() : (beforeJob != null ? beforeJob.getId() : "null"),
                    e.getMessage(), e);
        }
    }

    @Override
    public List<JobHistory> listVersions(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            return Collections.emptyList();
        }
        return jobHistoryMapper.selectByJobIdOrderByVersionDesc(jobId);
    }

    @Override
    public JobHistory getVersion(String jobId, Integer version) {
        if (!StringUtils.hasText(jobId) || version == null) {
            return null;
        }
        return jobHistoryMapper.selectByVersion(jobId, version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Job rollback(String jobId, Integer version) {
        if (!StringUtils.hasText(jobId)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_history_job_id_required");
        }
        if (version == null || version < 1) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_history_version_invalid");
        }
        // 查询目标历史版本
        JobHistory targetHistory = jobHistoryMapper.selectByVersion(jobId, version);
        if (targetHistory == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.cronjob.msg_history_version_not_found");
        }
        // 反序列化快照为 Job
        Job snapshotJob = YdszJson.toObject(targetHistory.getSnapshot(), Job.class);
        // 查询当前任务（用于保留统计字段等）
        Job currentJob = jobMapper.selectById(jobId);
        if (currentJob == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.cronjob.msg_c0d8369f");
        }
        // 保留 id/jobKey/tenantId/统计字段/createdAt（这些字段不应被回滚覆盖）
        snapshotJob.setId(jobId);
        snapshotJob.setJobKey(currentJob.getJobKey());
        snapshotJob.setTenantId(currentJob.getTenantId());
        snapshotJob.setFireCount(currentJob.getFireCount());
        snapshotJob.setSuccessCount(currentJob.getSuccessCount());
        snapshotJob.setFailCount(currentJob.getFailCount());
        snapshotJob.setLastFireTime(currentJob.getLastFireTime());
        snapshotJob.setNextFireTime(currentJob.getNextFireTime());
        snapshotJob.setConsecutiveFailCount(currentJob.getConsecutiveFailCount());
        snapshotJob.setCreatedAt(currentJob.getCreatedAt());
        snapshotJob.setCreatedBy(currentJob.getCreatedBy());
        // 计算新版本号 = max(历史版本号) + 1
        int nextVersion = getNextVersion(jobId);
        snapshotJob.setVersion(nextVersion);
        // 持久化回滚后的任务
        jobMapper.updateById(snapshotJob);
        // 保存新的历史版本
        saveHistory(snapshotJob, "SYSTEM");
        log.info("[History] 回滚任务配置: jobId={} fromVersion={} toVersion={}", jobId, version, nextVersion);
        return snapshotJob;
    }

    @Override
    public List<Map<String, Object>> compareVersions(String jobId, Integer version1, Integer version2) {
        if (!StringUtils.hasText(jobId)) {
            return Collections.emptyList();
        }
        if (version1 == null || version2 == null) {
            return Collections.emptyList();
        }
        JobHistory h1 = jobHistoryMapper.selectByVersion(jobId, version1);
        JobHistory h2 = jobHistoryMapper.selectByVersion(jobId, version2);
        if (h1 == null || h2 == null) {
            return Collections.emptyList();
        }
        Job job1 = YdszJson.toObject(h1.getSnapshot(), Job.class);
        Job job2 = YdszJson.toObject(h2.getSnapshot(), Job.class);
        return diffFields(job1, job2);
    }

    /**
     * 计算下一个版本号（当前最大历史版本号 + 1）。
     *
     * @param jobId 任务 ID
     * @return 下一个版本号；无历史记录时返回 1
     */
    private int getNextVersion(String jobId) {
        List<JobHistory> versions = jobHistoryMapper.selectByJobIdOrderByVersionDesc(jobId);
        if (versions == null || versions.isEmpty()) {
            return 1;
        }
        Integer maxVersion = versions.get(0).getVersion();
        return (maxVersion == null ? 0 : maxVersion) + 1;
    }

    /**
     * 逐字段对比两个 Job 的配置字段，返回差异列表。
     *
     * @param job1 旧版本任务
     * @param job2 新版本任务
     * @return 差异字段列表，每个元素包含 field/oldValue/newValue
     */
    private List<Map<String, Object>> diffFields(Job job1, Job job2) {
        List<Map<String, Object>> diffs = new ArrayList<>();
        Map<String, Object> snapshot1 = YdszJson.parseMap(YdszJson.toJson(job1));
        Map<String, Object> snapshot2 = YdszJson.parseMap(YdszJson.toJson(job2));
        for (String field : COMPARE_FIELDS) {
            Object oldValue = snapshot1.get(field);
            Object newValue = snapshot2.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> diff = new LinkedHashMap<>();
                diff.put("field", field);
                diff.put("oldValue", oldValue);
                diff.put("newValue", newValue);
                diffs.add(diff);
            }
        }
        return diffs;
    }
}
