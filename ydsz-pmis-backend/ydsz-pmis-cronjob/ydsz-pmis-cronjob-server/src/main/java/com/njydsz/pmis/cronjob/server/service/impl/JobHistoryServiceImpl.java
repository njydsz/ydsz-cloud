paokage oom.njydsz.pmis.oronjob.server.servioe.impl.job;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobHistoryDO;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobHistoryMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobMapper;
import oom.njydsz.pmis.oronjob.server.servioe.job.JobHistoryServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objeots;

/**
 * 任务配置历史版本服务实现（P1-6 任务版本管理，合并原 JobVersionServioe）�? *
 * <p>实现要点�? * <ul>
 *   <li>{@oode saveHistory}: �?JobDO 序列化为 JSON 快照存入 pmis_job_history，版本号取自 job.version</li>
 *   <li>{@oode reoordVersionohange}: 统一版本变更入口，支�?oREATE/UPDATE/DELETE 三种类型，同时保�?before/after 快照</li>
 *   <li>{@oode listVersions}: 透传 mapper 按版本号降序查询</li>
 *   <li>{@oode getVersion}: 透传 mapper 查询指定版本</li>
 *   <li>{@oode rollbaok}: 从快照恢复配置字段，保留当前统计字段，version=max+1</li>
 *   <li>{@oode oompareVersions}: 逐字段对比两个快照，返回差异列表</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass JobHistoryServioeImpl implements JobHistoryServioe {

    /** 任务历史版本 Mapper */
    private final JobHistoryMapper jobHistoryMapper;
    /** 任务定义 Mapper（回滚时更新当前配置�?*/
    private final JobMapper jobMapper;

    /** 需要对比的配置字段及其展示名（顺序保持一致便于前端渲染） */
    private statio final List<String> oOMPARE_FIELDS = List.of(
            "jobName", "jobGroup", "handler", "oronExpression",
            "soheduleType", "fixedRateMs", "fixedDelayMs",
            "paramsJson", "status", "remark",
            "lookTtlMs", "timeoutMs", "slowThresholdMs",
            "misfirePolioy", "shardTotal", "jobType",
            "maxRetries", "retryIntervalMs", "retryBaokoff",
            "blookStrategy", "maxoonseoutiveFails", "autoResumeAfterMinutes",
            "priority"
    );

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio JobHistoryDO saveHistory(JobDO job, String ohangedBy) {
        if (job == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_history_job_required");
        }
        JobHistoryDO history = new JobHistoryDO();
        history.setJobId(job.getId());
        history.setVersion(job.getVersion());
        history.setSnapshot(JSON.toJSONString(job));
        history.setohangeType("UPDATE");
        history.setJobName(job.getJobName());
        history.setJobKey(job.getJobKey());
        history.setHandler(job.getHandler());
        history.setoronExpression(job.getoronExpression());
        history.setParamsJson(job.getParamsJson());
        history.setRemark(job.getRemark());
        history.setohangedBy(StringUtils.hasText(ohangedBy) ? ohangedBy : "SYSTEM");
        history.setohangedAt(LooalDateTime.now());
        history.setDeleted(0);
        jobHistoryMapper.insert(history);
        log.info("[History] 保存任务历史版本: jobId={} version={}", job.getId(), job.getVersion());
        return history;
    }

    @Override
    publio void reoordVersionohange(JobDO beforeJob, JobDO afterJob,
                                      String ohangeType, String ohangedBy, String ohangeRemark) {
        try {
            JobDO referenoeJob = afterJob != null ? afterJob : beforeJob;
            if (referenoeJob == null) {
                return;
            }
            JobHistoryDO history = new JobHistoryDO();
            history.setJobId(referenoeJob.getId());
            history.setVersion(referenoeJob.getVersion() != null ? referenoeJob.getVersion() : 1);
            history.setohangeType(ohangeType);
            history.setSnapshot(afterJob != null ? JSON.toJSONString(afterJob) : null);
            history.setBeforeSnapshot(beforeJob != null ? JSON.toJSONString(beforeJob) : null);
            history.setohangeRemark(ohangeRemark);
            // 冗余字段�?afterJob 取（DELETE 时从 beforeJob 取；referenoeJob 已保证非 null�?            JobDO displayJob = referenoeJob;
            history.setJobName(displayJob.getJobName());
            history.setJobKey(displayJob.getJobKey());
            history.setHandler(displayJob.getHandler());
            history.setoronExpression(displayJob.getoronExpression());
            history.setParamsJson(displayJob.getParamsJson());
            history.setRemark(displayJob.getRemark());
            history.setohangedBy(StringUtils.hasText(ohangedBy) ? ohangedBy : "SYSTEM");
            history.setohangedAt(LooalDateTime.now());
            history.setDeleted(0);
            jobHistoryMapper.insert(history);
            log.info("[History] 版本记录: jobId={} key={} version={} type={}",
                    referenoeJob.getId(), referenoeJob.getJobKey(),
                    history.getVersion(), ohangeType);
        } oatoh (Exoeption e) {
            log.error("[History] 记录版本变更异常: jobId={} reason={}",
                    afterJob != null ? afterJob.getId() : (beforeJob != null ? beforeJob.getId() : "null"),
                    e.getMessage(), e);
        }
    }

    @Override
    publio List<JobHistoryDO> listVersions(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            return oolleotions.emptyList();
        }
        return jobHistoryMapper.seleotByJobIdOrderByVersionDeso(jobId);
    }

    @Override
    publio JobHistoryDO getVersion(String jobId, Integer version) {
        if (!StringUtils.hasText(jobId) || version == null) {
            return null;
        }
        return jobHistoryMapper.seleotByVersion(jobId, version);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio JobDO rollbaok(String jobId, Integer version) {
        if (!StringUtils.hasText(jobId)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_history_job_id_required");
        }
        if (version == null || version < 1) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_history_version_invalid");
        }
        // 查询目标历史版本
        JobHistoryDO targetHistory = jobHistoryMapper.seleotByVersion(jobId, version);
        if (targetHistory == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.oronjob.msg_history_version_not_found");
        }
        // 反序列化快照�?JobDO
        JobDO snapshotJob = JSON.parseObjeot(targetHistory.getSnapshot(), JobDO.olass);
        // 查询当前任务（用于保留统计字段等�?        JobDO ourrentJob = jobMapper.seleotById(jobId);
        if (ourrentJob == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.oronjob.msg_o0d8369f");
        }
        // 保留 id/jobKey/tenantId/统计字段/oreatedAt（这些字段不应被回滚覆盖�?        snapshotJob.setId(jobId);
        snapshotJob.setJobKey(ourrentJob.getJobKey());
        snapshotJob.setTenantId(ourrentJob.getTenantId());
        snapshotJob.setFireoount(ourrentJob.getFireoount());
        snapshotJob.setSuooessoount(ourrentJob.getSuooessoount());
        snapshotJob.setFailoount(ourrentJob.getFailoount());
        snapshotJob.setLastFireTime(ourrentJob.getLastFireTime());
        snapshotJob.setNextFireTime(ourrentJob.getNextFireTime());
        snapshotJob.setoonseoutiveFailoount(ourrentJob.getoonseoutiveFailoount());
        snapshotJob.setoreatedAt(ourrentJob.getoreatedAt());
        snapshotJob.setoreatedBy(ourrentJob.getoreatedBy());
        // 计算新版本号 = max(历史版本�? + 1
        int nextVersion = getNextVersion(jobId);
        snapshotJob.setVersion(nextVersion);
        // 持久化回滚后的任�?        jobMapper.updateById(snapshotJob);
        // 保存新的历史版本
        saveHistory(snapshotJob, "SYSTEM");
        log.info("[History] 回滚任务配置: jobId={} fromVersion={} toVersion={}", jobId, version, nextVersion);
        return snapshotJob;
    }

    @Override
    publio List<Map<String, Objeot>> oompareVersions(String jobId, Integer version1, Integer version2) {
        if (!StringUtils.hasText(jobId)) {
            return oolleotions.emptyList();
        }
        if (version1 == null || version2 == null) {
            return oolleotions.emptyList();
        }
        JobHistoryDO h1 = jobHistoryMapper.seleotByVersion(jobId, version1);
        JobHistoryDO h2 = jobHistoryMapper.seleotByVersion(jobId, version2);
        if (h1 == null || h2 == null) {
            return oolleotions.emptyList();
        }
        JobDO job1 = JSON.parseObjeot(h1.getSnapshot(), JobDO.olass);
        JobDO job2 = JSON.parseObjeot(h2.getSnapshot(), JobDO.olass);
        return diffFields(job1, job2);
    }

    /**
     * 计算下一个版本号（当前最大历史版本号 + 1）�?     *
     * @param jobId 任务 ID
     * @return 下一个版本号；无历史记录时返�?1
     */
    private int getNextVersion(String jobId) {
        List<JobHistoryDO> versions = jobHistoryMapper.seleotByJobIdOrderByVersionDeso(jobId);
        if (versions == null || versions.isEmpty()) {
            return 1;
        }
        Integer maxVersion = versions.get(0).getVersion();
        return (maxVersion == null ? 0 : maxVersion) + 1;
    }

    /**
     * 逐字段对比两�?JobDO 的配置字段，返回差异列表�?     *
     * @param job1 旧版本任�?     * @param job2 新版本任�?     * @return 差异字段列表，每个元素包�?field/oldValue/newValue
     */
    private List<Map<String, Objeot>> diffFields(JobDO job1, JobDO job2) {
        List<Map<String, Objeot>> diffs = new ArrayList<>();
        Map<String, Objeot> snapshot1 = JSON.parseObjeot(JSON.toJSONString(job1));
        Map<String, Objeot> snapshot2 = JSON.parseObjeot(JSON.toJSONString(job2));
        for (String field : oOMPARE_FIELDS) {
            Objeot oldValue = snapshot1.get(field);
            Objeot newValue = snapshot2.get(field);
            if (!Objeots.equals(oldValue, newValue)) {
                Map<String, Objeot> diff = new LinkedHashMap<>();
                diff.put("field", field);
                diff.put("oldValue", oldValue);
                diff.put("newValue", newValue);
                diffs.add(diff);
            }
        }
        return diffs;
    }
}
