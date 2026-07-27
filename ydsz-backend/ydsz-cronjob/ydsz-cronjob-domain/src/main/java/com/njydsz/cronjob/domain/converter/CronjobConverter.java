package com.njydsz.cronjob.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.njydsz.cronjob.domain.entity.schedule.GlueCode;
import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.domain.entity.job.JobAlertLog;
import com.njydsz.cronjob.domain.entity.job.JobAlertRule;
import com.njydsz.cronjob.domain.entity.job.JobArtifact;
import com.njydsz.cronjob.domain.entity.dag.JobDag;
import com.njydsz.cronjob.domain.entity.dag.JobDagInstance;
import com.njydsz.cronjob.domain.entity.dag.JobDagNodeInstance;
import com.njydsz.cronjob.domain.entity.dag.JobDagVersion;
import com.njydsz.cronjob.domain.entity.log.JobDailyStats;
import com.njydsz.cronjob.domain.entity.job.JobHistory;
import com.njydsz.cronjob.domain.entity.log.JobLog;
import com.njydsz.cronjob.domain.entity.log.JobLogContent;
import com.njydsz.cronjob.domain.entity.job.JobNode;
import com.njydsz.cronjob.domain.entity.alert.JobSla;
import com.njydsz.cronjob.domain.entity.job.JobTask;
import com.njydsz.cronjob.domain.entity.job.JobWebhook;
import com.njydsz.cronjob.domain.vo.GlueCodeVO;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.domain.vo.JobAlertLogVO;
import com.njydsz.cronjob.domain.vo.JobAlertRuleVO;
import com.njydsz.cronjob.domain.vo.JobArtifactVO;
import com.njydsz.cronjob.domain.vo.JobDagVO;
import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagVersionVO;
import com.njydsz.cronjob.domain.vo.JobDailyStatsVO;
import com.njydsz.cronjob.domain.vo.JobHistoryVO;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.domain.vo.JobLogContentVO;
import com.njydsz.cronjob.domain.vo.JobNodeVO;
import com.njydsz.cronjob.domain.vo.JobSlaVO;
import com.njydsz.cronjob.domain.vo.JobTaskVO;
import com.njydsz.cronjob.domain.vo.JobWebhookVO;

/**
 * cronjob 模块统一 MapStruct 转换器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface CronjobConverter {

    CronjobConverter INSTANT = Mappers.getMapper(CronjobConverter.class);

    // ===== GlueCode =====
    GlueCodeVO entityToVO(GlueCode entity);
    List<GlueCodeVO> glueCodeListToVO(List<GlueCode> entities);

    // ===== Job =====
    JobVO entityToVO(Job entity);
    List<JobVO> jobListToVO(List<Job> entities);

    // ===== JobAlertLog =====
    JobAlertLogVO entityToVO(JobAlertLog entity);
    List<JobAlertLogVO> jobAlertLogListToVO(List<JobAlertLog> entities);

    // ===== JobAlertRule =====
    JobAlertRuleVO entityToVO(JobAlertRule entity);
    List<JobAlertRuleVO> jobAlertRuleListToVO(List<JobAlertRule> entities);

    // ===== JobArtifact =====
    JobArtifactVO entityToVO(JobArtifact entity);
    List<JobArtifactVO> jobArtifactListToVO(List<JobArtifact> entities);

    // ===== JobDag =====
    JobDagVO entityToVO(JobDag entity);
    List<JobDagVO> jobDagListToVO(List<JobDag> entities);

    // ===== JobDagInstance =====
    JobDagInstanceVO entityToVO(JobDagInstance entity);
    List<JobDagInstanceVO> jobDagInstanceListToVO(List<JobDagInstance> entities);

    // ===== JobDagNodeInstance =====
    JobDagNodeInstanceVO entityToVO(JobDagNodeInstance entity);
    List<JobDagNodeInstanceVO> jobDagNodeInstanceListToVO(List<JobDagNodeInstance> entities);

    // ===== JobDagVersion =====
    JobDagVersionVO entityToVO(JobDagVersion entity);
    List<JobDagVersionVO> jobDagVersionListToVO(List<JobDagVersion> entities);

    // ===== JobDailyStats =====
    JobDailyStatsVO entityToVO(JobDailyStats entity);
    List<JobDailyStatsVO> jobDailyStatsListToVO(List<JobDailyStats> entities);

    // ===== JobHistory =====
    JobHistoryVO entityToVO(JobHistory entity);
    List<JobHistoryVO> jobHistoryListToVO(List<JobHistory> entities);

    // ===== JobLog =====
    JobLogVO entityToVO(JobLog entity);
    List<JobLogVO> jobLogListToVO(List<JobLog> entities);

    // ===== JobLogContent =====
    JobLogContentVO entityToVO(JobLogContent entity);
    List<JobLogContentVO> jobLogContentListToVO(List<JobLogContent> entities);

    // ===== JobNode =====
    JobNodeVO entityToVO(JobNode entity);
    List<JobNodeVO> jobNodeListToVO(List<JobNode> entities);

    // ===== JobSla =====
    JobSlaVO entityToVO(JobSla entity);
    List<JobSlaVO> jobSlaListToVO(List<JobSla> entities);

    // ===== JobTask =====
    JobTaskVO entityToVO(JobTask entity);
    List<JobTaskVO> jobTaskListToVO(List<JobTask> entities);

    // ===== JobWebhook =====
    JobWebhookVO entityToVO(JobWebhook entity);
    List<JobWebhookVO> jobWebhookListToVO(List<JobWebhook> entities);

}