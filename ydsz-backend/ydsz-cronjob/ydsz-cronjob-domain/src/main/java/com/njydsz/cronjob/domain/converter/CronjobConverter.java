package com.njydsz.cronjob.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
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
import com.njydsz.cronjob.domain.entity.job.JobTask;
import com.njydsz.cronjob.domain.entity.job.JobWebhook;
import com.njydsz.cronjob.domain.dto.job.JobSaveDTO;
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
import com.njydsz.cronjob.domain.vo.JobTaskVO;
import com.njydsz.cronjob.domain.vo.JobWebhookVO;
import com.njydsz.cronjob.domain.dto.post.JobWebhookPostDTO;
import com.njydsz.cronjob.domain.dto.post.JobPostDTO;
import com.njydsz.cronjob.domain.dto.post.JobDagPostDTO;
import com.njydsz.cronjob.domain.dto.post.AlertRulePostDTO;
import com.njydsz.cronjob.domain.dto.put.JobWebhookPutDTO;
import com.njydsz.cronjob.domain.dto.put.JobPutDTO;
import com.njydsz.cronjob.domain.dto.put.JobDagPutDTO;
import com.njydsz.cronjob.domain.dto.put.AlertRulePutDTO;

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

    // ===== JobTask =====
    JobTaskVO entityToVO(JobTask entity);
    List<JobTaskVO> jobTaskListToVO(List<JobTask> entities);

    // ===== JobWebhook =====
    JobWebhookVO entityToVO(JobWebhook entity);
    List<JobWebhookVO> jobWebhookListToVO(List<JobWebhook> entities);

    // ===== JobSaveDTO → Job Entity =====
    /**
     * 任务表单 DTO → Job 实体（用于新增/更新）。
     *
     * <p>忽略 {@code MpBaseEntity} 的自动填充字段（id/tenantId/审计字段/逻辑删除/乐观锁），
     * 这些字段由 MyBatis-Plus 拦截器在持久化时统一处理。
     *
     * @param dto 任务表单
     * @return Job 实体（id 为 null，由雪花算法生成）
     */
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "jobRemark", source = "remark")
    @Mapping(target = "nextFireTime", ignore = true)
    @Mapping(target = "lastFireTime", ignore = true)
    @Mapping(target = "fireCount", ignore = true)
    @Mapping(target = "successCount", ignore = true)
    @Mapping(target = "failCount", ignore = true)
    @Mapping(target = "jobType", ignore = true)
    @Mapping(target = "maxRetries", ignore = true)
    @Mapping(target = "retryIntervalMs", ignore = true)
    @Mapping(target = "retryBackoff", ignore = true)
    @Mapping(target = "blockStrategy", ignore = true)
    @Mapping(target = "consecutiveFailCount", ignore = true)
    @Mapping(target = "maxConsecutiveFails", ignore = true)
    @Mapping(target = "autoResumeAfterMinutes", ignore = true)
    @Mapping(target = "priority", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "canaryRatio", ignore = true)
    @Mapping(target = "canaryHandler", ignore = true)
    Job saveDtoToEntity(JobSaveDTO dto);


    // ===== JobWebhook PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    JobWebhook postDtoToEntity(JobWebhookPostDTO dto);

    // ===== JobWebhook PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    JobWebhook putDtoToEntity(JobWebhookPutDTO dto);

    // ===== Job PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Job postDtoToEntity(JobPostDTO dto);

    // ===== Job PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Job putDtoToEntity(JobPutDTO dto);

    // ===== JobDag PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    JobDag postDtoToEntity(JobDagPostDTO dto);

    // ===== JobDag PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    JobDag putDtoToEntity(JobDagPutDTO dto);

}