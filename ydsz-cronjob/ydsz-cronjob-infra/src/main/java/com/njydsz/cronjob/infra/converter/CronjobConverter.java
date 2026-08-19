package com.njydsz.cronjob.infra.converter;

import java.util.List;

import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import com.njydsz.cronjob.domain.dto.alert.AlertRuleSaveDTO;
import com.njydsz.cronjob.domain.dto.post.JobDagPostDTO;
import com.njydsz.cronjob.domain.dto.post.JobPostDTO;
import com.njydsz.cronjob.domain.dto.post.JobWebhookPostDTO;
import com.njydsz.cronjob.domain.dto.put.JobDagPutDTO;
import com.njydsz.cronjob.domain.dto.put.JobPutDTO;
import com.njydsz.cronjob.domain.dto.put.JobWebhookPutDTO;
import com.njydsz.cronjob.infra.entity.log.JobDailyStats;
import com.njydsz.cronjob.infra.entity.log.JobLog;
import com.njydsz.cronjob.infra.entity.log.JobLogContent;
import com.njydsz.cronjob.infra.entity.dag.JobDag;
import com.njydsz.cronjob.infra.entity.dag.JobDagInstance;
import com.njydsz.cronjob.infra.entity.dag.JobDagNodeInstance;
import com.njydsz.cronjob.infra.entity.dag.JobDagVersion;
import com.njydsz.cronjob.infra.entity.job.Job;
import com.njydsz.cronjob.infra.entity.job.JobAlertLog;
import com.njydsz.cronjob.infra.entity.job.JobAlertRule;
import com.njydsz.cronjob.infra.entity.job.JobArtifact;
import com.njydsz.cronjob.infra.entity.job.JobHistory;
import com.njydsz.cronjob.infra.entity.job.JobNode;
import com.njydsz.cronjob.infra.entity.job.JobTask;
import com.njydsz.cronjob.infra.entity.job.JobWebhook;
import com.njydsz.cronjob.infra.entity.job.TenantQuota;
import com.njydsz.cronjob.infra.entity.schedule.GlueCode;
import com.njydsz.cronjob.domain.vo.GlueCodeVO;
import com.njydsz.cronjob.domain.vo.TenantQuotaVO;
import com.njydsz.cronjob.domain.vo.JobAlertLogVO;
import com.njydsz.cronjob.domain.vo.JobAlertRuleVO;
import com.njydsz.cronjob.domain.vo.JobArtifactVO;
import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagVO;
import com.njydsz.cronjob.domain.vo.JobDagVersionVO;
import com.njydsz.cronjob.domain.vo.JobDailyStatsVO;
import com.njydsz.cronjob.domain.vo.JobHistoryVO;
import com.njydsz.cronjob.domain.vo.JobLogContentVO;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.domain.vo.JobNodeVO;
import com.njydsz.cronjob.domain.vo.JobTaskVO;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.domain.vo.JobWebhookVO;

/**
 * cronjob 模块统一 MapStruct 转换器。
 *
 * <p>承担定时任务调度模块所有 Entity ↔ VO、DTO → Entity 的类型转换。 覆盖任务（Job）、DAG 工作流、任务历史、告警规则、日志、Webhook、 Glue
 * 代码、任务产物、DAG 实例、日统计等核心实体的转换。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>使用 MapStruct 注解处理器，编译期生成实现类，性能优于反射
 *   <li>通过 {@link #INSTANT} 单例访问，零依赖注入
 *   <li>同名字段自动映射；系统字段通过 @Mapping(ignore = true) 忽略
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface CronjobConverter {

  CronjobConverter INSTANT = Mappers.getMapper(CronjobConverter.class);

  // ===== TenantQuota =====
  TenantQuotaVO entityToVO(TenantQuota entity);

  List<TenantQuotaVO> tenantQuotaListToVO(List<TenantQuota> entities);

  // ===== GlueCode =====
  GlueCodeVO entityToVO(GlueCode entity);

  List<GlueCodeVO> glueCodeListToVO(List<GlueCode> entities);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  GlueCode voToEntity(GlueCodeVO vo);

  // ===== Job =====
  JobVO entityToVO(Job entity);

  List<JobVO> jobListToVO(List<Job> entities);

  // ===== JobAlertLog =====
  JobAlertLogVO entityToVO(JobAlertLog entity);

  List<JobAlertLogVO> jobAlertLogListToVO(List<JobAlertLog> entities);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  JobAlertLog voToEntity(JobAlertLogVO vo);

  // ===== JobAlertRule =====
  JobAlertRuleVO entityToVO(JobAlertRule entity);

  List<JobAlertRuleVO> jobAlertRuleListToVO(List<JobAlertRule> entities);

  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  JobAlertRule voToEntity(JobAlertRuleVO vo);

  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  JobAlertRule dtoToEntity(AlertRuleSaveDTO dto);

  // ===== JobArtifact =====
  JobArtifactVO entityToVO(JobArtifact entity);

  List<JobArtifactVO> jobArtifactListToVO(List<JobArtifact> entities);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  JobArtifact voToEntity(JobArtifactVO vo);

  // ===== JobDag =====
  JobDagVO entityToVO(JobDag entity);

  List<JobDagVO> jobDagListToVO(List<JobDag> entities);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "fireCount", ignore = true)
  @Mapping(target = "successCount", ignore = true)
  @Mapping(target = "failCount", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "lastFireTime", ignore = true)
  @Mapping(target = "nextFireTime", ignore = true)
  JobDag dtoToEntity(JobDagSaveDTO dto);

  // ===== JobDagInstance =====
  JobDagInstanceVO entityToVO(JobDagInstance entity);

  List<JobDagInstanceVO> jobDagInstanceListToVO(List<JobDagInstance> entities);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "triggerType", ignore = true)
  @Mapping(target = "triggerBy", ignore = true)
  @Mapping(target = "triggerTraceId", ignore = true)
  @Mapping(target = "contextJson", ignore = true)
  @Mapping(target = "startedAt", ignore = true)
  @Mapping(target = "finishedAt", ignore = true)
  @Mapping(target = "durationMs", ignore = true)
  @Mapping(target = "errorMessage", ignore = true)
  @Mapping(target = "totalNodes", ignore = true)
  @Mapping(target = "successNodes", ignore = true)
  @Mapping(target = "failedNodes", ignore = true)
  @Mapping(target = "skippedNodes", ignore = true)
  JobDagInstance voToEntity(JobDagInstanceVO vo);

  // ===== JobDagNodeInstance =====
  JobDagNodeInstanceVO entityToVO(JobDagNodeInstance entity);

  List<JobDagNodeInstanceVO> jobDagNodeInstanceListToVO(List<JobDagNodeInstance> entities);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  JobDagNodeInstance voToEntity(JobDagNodeInstanceVO vo);

  @IterableMapping(qualifiedByName = "jobDagNodeInstanceVoToEntity")
  List<JobDagNodeInstance> jobDagNodeInstanceVOsToEntities(List<JobDagNodeInstanceVO> vos);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Named("jobDagNodeInstanceVoToEntity")
  JobDagNodeInstance jobDagNodeInstanceVoToEntityInternal(JobDagNodeInstanceVO vo);

  // ===== JobDagVersion =====
  JobDagVersionVO entityToVO(JobDagVersion entity);

  List<JobDagVersionVO> jobDagVersionListToVO(List<JobDagVersion> entities);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  JobDagVersion voToEntity(JobDagVersionVO vo);

  // ===== JobDailyStats =====
  JobDailyStatsVO entityToVO(JobDailyStats entity);

  List<JobDailyStatsVO> jobDailyStatsListToVO(List<JobDailyStats> entities);

  // P0-FIX: JobDailyStats 继承 MpBaseIdEntity（@SuperBuilder 仅暴露 id），
  // deleted/revision/tenantId/createdBy/createdAt/updatedBy/updatedAt 不在 builder 中，
  // 原 @Mapping(target=..., ignore=true) 引用不存在属性导致 MapStruct 生成失败（Unknown property）。
  @Mapping(target = "id", ignore = true)
  JobDailyStats voToEntity(JobDailyStatsVO vo);

  // ===== JobHistory =====
  JobHistoryVO entityToVO(JobHistory entity);

  List<JobHistoryVO> jobHistoryListToVO(List<JobHistory> entities);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  JobHistory voToEntity(JobHistoryVO vo);

  // ===== JobLog =====
  JobLogVO entityToVO(JobLog entity);

  List<JobLogVO> jobLogListToVO(List<JobLog> entities);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  JobLog voToEntity(JobLogVO vo);

  // ===== JobLogContent =====
  JobLogContentVO entityToVO(JobLogContent entity);

  List<JobLogContentVO> jobLogContentListToVO(List<JobLogContent> entities);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  JobLogContent voToEntity(JobLogContentVO vo);

  // ===== JobNode =====
  JobNodeVO entityToVO(JobNode entity);

  List<JobNodeVO> jobNodeListToVO(List<JobNode> entities);

  // ===== JobTask =====
  JobTaskVO entityToVO(JobTask entity);

  List<JobTaskVO> jobTaskListToVO(List<JobTask> entities);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  JobTask voToEntity(JobTaskVO vo);

  // ===== JobWebhook =====
  JobWebhookVO entityToVO(JobWebhook entity);

  List<JobWebhookVO> jobWebhookListToVO(List<JobWebhook> entities);

  /** P0-F3: VO → Entity（testWebhook 需要以实体形式发送测试事件） */
  JobWebhook voToEntity(JobWebhookVO vo);

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
