package com.njydsz.cronjob.domain.converter;

import java.util.List;

import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import com.njydsz.cronjob.domain.dto.alert.AlertRuleSaveDTO;
import com.njydsz.cronjob.domain.dto.dag.JobDagSaveDTO;
import com.njydsz.cronjob.domain.dto.post.JobDagPostDTO;
import com.njydsz.cronjob.domain.dto.post.JobPostDTO;
import com.njydsz.cronjob.domain.dto.post.JobWebhookPostDTO;
import com.njydsz.cronjob.domain.dto.put.JobDagPutDTO;
import com.njydsz.cronjob.domain.dto.put.JobPutDTO;
import com.njydsz.cronjob.domain.dto.put.JobWebhookPutDTO;
import com.njydsz.cronjob.domain.entity.OutboxEvent;
import com.njydsz.cronjob.domain.entity.dag.JobDag;
import com.njydsz.cronjob.domain.entity.dag.JobDagContext;
import com.njydsz.cronjob.domain.entity.dag.JobDagInstance;
import com.njydsz.cronjob.domain.entity.dag.JobDagNodeInstance;
import com.njydsz.cronjob.domain.entity.dag.JobDagVersion;
import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.domain.entity.job.JobAlertLog;
import com.njydsz.cronjob.domain.entity.job.JobAlertRule;
import com.njydsz.cronjob.domain.entity.job.JobArtifact;
import com.njydsz.cronjob.domain.entity.job.JobHistory;
import com.njydsz.cronjob.domain.entity.job.JobNode;
import com.njydsz.cronjob.domain.entity.job.JobTask;
import com.njydsz.cronjob.domain.entity.job.JobWebhook;
import com.njydsz.cronjob.domain.entity.job.JobWebhookRetry;
import com.njydsz.cronjob.domain.entity.job.TenantQuota;
import com.njydsz.cronjob.domain.entity.log.JobDailyStats;
import com.njydsz.cronjob.domain.entity.log.JobLog;
import com.njydsz.cronjob.domain.entity.log.JobLogContent;
import com.njydsz.cronjob.domain.entity.schedule.GlueCode;
import com.njydsz.cronjob.domain.vo.GlueCodeVO;
import com.njydsz.cronjob.domain.vo.JobAlertLogVO;
import com.njydsz.cronjob.domain.vo.JobAlertRuleVO;
import com.njydsz.cronjob.domain.vo.JobArtifactVO;
import com.njydsz.cronjob.domain.vo.JobDagContextVO;
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
import com.njydsz.cronjob.domain.vo.JobWebhookRetryVO;
import com.njydsz.cronjob.domain.vo.JobWebhookVO;
import com.njydsz.cronjob.domain.vo.OutboxEventVO;
import com.njydsz.cronjob.domain.vo.TenantQuotaVO;

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
 * @since 26.09.01
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CronjobConverter {

  /** MapStruct 单例实例 */
  CronjobConverter INSTANT = Mappers.getMapper(CronjobConverter.class);

  // ===== TenantQuota =====
  TenantQuotaVO entityToVO(TenantQuota entity);

  List<TenantQuotaVO> tenantQuotaListToVO(List<TenantQuota> entities);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  TenantQuota voToEntity(TenantQuotaVO vo);

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

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Job voToEntity(JobVO vo);

  // ===== JobAlertLog =====
  JobAlertLogVO entityToVO(JobAlertLog entity);

  List<JobAlertLogVO> jobAlertLogListToVO(List<JobAlertLog> entities);

  @Mapping(target = "id", ignore = true)
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
  @Mapping(target = "dagStatus", source = "status")
  JobDag dtoToEntity(JobDagSaveDTO dto);

  /**
   * 将 JobDagVO 转换为 infra 实体（内部服务新增/更新场景）。
   *
   * <p>VO 与实体字段名基本一致（除审计字段、级联字段外），MapStruct 可自动映射。
   *
   * @param vo DAG 工作流视图对象
   * @return DAG 实体（审计字段、级联字段由服务层处理）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  JobDag voToEntity(JobDagVO vo);

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
  JobHistory voToEntity(JobHistoryVO vo);

  // ===== JobLog =====
  JobLogVO entityToVO(JobLog entity);

  List<JobLogVO> jobLogListToVO(List<JobLog> entities);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  JobLog voToEntity(JobLogVO vo);

  // ===== JobLogContent =====
  JobLogContentVO entityToVO(JobLogContent entity);

  List<JobLogContentVO> jobLogContentListToVO(List<JobLogContent> entities);

  @Mapping(target = "id", ignore = true)
  JobLogContent voToEntity(JobLogContentVO vo);

  // ===== JobNode =====
  JobNodeVO entityToVO(JobNode entity);

  List<JobNodeVO> jobNodeListToVO(List<JobNode> entities);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  JobNode voToEntity(JobNodeVO vo);

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

  /**
   * P0-F3: VO → Entity（testWebhook 需要以实体形式发送测试事件）
   *
   * @param vo Webhook 配置视图对象
   * @return Webhook 配置实体
   */
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

  // ===== OutboxEvent =====
  OutboxEventVO entityToVO(OutboxEvent entity);

  List<OutboxEventVO> outboxEventListToVO(List<OutboxEvent> entities);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createTime", ignore = true)
  @Mapping(target = "updateTime", ignore = true)
  OutboxEvent voToEntity(OutboxEventVO vo);

  List<OutboxEvent> outboxEventVOsToEntities(List<OutboxEventVO> vos);

  // ===== JobDagContext（P0-13：DAG 节点上下文独立存储） =====

  /**
   * 将 DAG 节点上下文实体转换为 VO。
   *
   * @param entity 节点上下文实体
   * @return 节点上下文 VO
   */
  JobDagContextVO jobDagContextEntityToVO(JobDagContext entity);

  /**
   * 将 DAG 节点上下文实体列表转换为 VO 列表。
   *
   * @param entities 节点上下文实体列表
   * @return 节点上下文 VO 列表
   */
  List<JobDagContextVO> jobDagContextListToVO(List<JobDagContext> entities);

  /**
   * 将 DAG 节点上下文 VO 转换为实体。
   *
   * <p>注意：VO 含 {@code tenantId} 字段但实体不含（tenantId 由 MyBatis 拦截器自动注入），
   * 故仅映射 {@code dagInstanceId}、{@code nodeKey}、{@code resultJson} 三个业务字段。
   *
   * @param vo 节点上下文 VO
   * @return 节点上下文实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  JobDagContext jobDagContextVOToEntity(JobDagContextVO vo);

  /**
   * 将 DAG 节点上下文 VO 列表转换为实体列表。
   *
   * @param vos 节点上下文 VO 列表
   * @return 节点上下文实体列表
   */
  List<JobDagContext> jobDagContextListVOToEntity(List<JobDagContextVO> vos);

  // ===== JobWebhookRetry（P1-3：Webhook 重试补偿） =====

  /**
   * 将 Webhook 重试记录实体转换为 VO。
   *
   * @param entity 重试记录实体
   * @return 重试记录 VO
   */
  JobWebhookRetryVO jobWebhookRetryEntityToVO(JobWebhookRetry entity);

  /**
   * 将 Webhook 重试记录实体列表转换为 VO 列表。
   *
   * @param entities 重试记录实体列表
   * @return 重试记录 VO 列表
   */
  List<JobWebhookRetryVO> jobWebhookRetryListToVO(List<JobWebhookRetry> entities);

  /**
   * 将 Webhook 重试记录 VO 转换为实体。
   *
   * @param vo 重试记录 VO
   * @return 重试记录实体
   */
  @Mapping(target = "id", ignore = true)
  JobWebhookRetry jobWebhookRetryVOToEntity(JobWebhookRetryVO vo);

  /**
   * 将 Webhook 重试记录 VO 列表转换为实体列表。
   *
   * @param vos 重试记录 VO 列表
   * @return 重试记录实体列表
   */
  List<JobWebhookRetry> jobWebhookRetryListVOToEntity(List<JobWebhookRetryVO> vos);
}
