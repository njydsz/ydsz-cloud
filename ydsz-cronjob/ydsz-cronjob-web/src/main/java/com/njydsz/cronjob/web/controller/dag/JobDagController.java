package com.njydsz.cronjob.web.controller.dag;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.lock.annotation.IdempotentExempt;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.cronjob.domain.dto.dag.JobDagSaveDTO;
import com.njydsz.cronjob.domain.dto.dag.JobDagTriggerDTO;
import com.njydsz.cronjob.domain.dto.post.JobDagPostDTO;
import com.njydsz.cronjob.domain.dto.put.JobDagPutDTO;
import com.njydsz.cronjob.domain.vo.JobDagVO;
import com.njydsz.cronjob.domain.vo.JobDagVersionVO;
import com.njydsz.cronjob.server.core.dag.DagDefinition;
import com.njydsz.cronjob.server.core.dag.DagDefinitionCodec;
import com.njydsz.cronjob.server.core.dag.DagDefinitionValidator;
import com.njydsz.cronjob.server.service.dag.JobDagService;

/**
 * DAG 工作流定义 Controller（P2 DAG 增强）。
 *
 * <p>面向可视化 DAG 编辑器提供 REST API，承担 DAG 工作流的完整生命周期管理：
 *
 * <ul>
 *   <li>CRUD：创建 / 更新 / 删除 / 详情 / 按 KEY 查
 *   <li>状态：启用 / 禁用
 *   <li>触发：手动触发（同步返回实例 ID）
 *   <li>校验：编辑器保存前的实时校验
 *   <li>版本：版本历史查询、回滚到指定版本
 * </ul>
 *
 * <h3>核心组件</h3>
 *
 * <ul>
 *   <li>{@link JobDagService} - DAG 业务编排（CRUD/触发/版本）
 *   <li>{@link DagDefinitionValidator} - 节点/边/环/根节点等结构校验
 *   <li>{@link DagDefinitionCodec} - DAG 定义 JSON 序列化/反序列化
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>CRUD 全部 {@link Idempotent} 防重（5s TTL）
 *   <li>权限按 DAG 维度细分（CRONJOB_DAG_CREATE/UPDATE/DELETE/VIEW/TRIGGER）
 *   <li>所有写操作 {@link RateLimit} 限流 50 QPS / IP
 *   <li>所有变更 {@link Audit} 异步落库审计日志
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "DAG工作流定义", description = "DAG CRUD、启停、触发、校验、版本历史、回滚")
@Slf4j
@RequestMapping("/api/v1/cronjob/dag")
@RequiredArgsConstructor
public class JobDagController {
  /** 默认版本列表条数 */
  private static final int DEFAULT_VERSION_LIST_LIMIT = 50;


  /** DAG 工作流服务 */
  private final JobDagService jobDagService;

  /** DAG 定义校验器（校验节点/边/环等） */
  private final DagDefinitionValidator dagDefinitionValidator;

  /** DAG 定义 JSON 编解码器 */
  private final DagDefinitionCodec dagDefinitionCodec;

  /**
   * 创建 DAG 工作流。
   *
   * <p>在保存前会先做基础结构校验（dagDefinition 非空、节点数 > 0）。 完整的 DAG 结构校验（环/根/边）由 {@link #validateDag}
   * 在编辑器保存前完成。 持久化时初始化 version=1，状态按入参设置（默认 DRAFT）。
   *
   * @param dto DAG 保存请求体（含 dagKey/dagName/dagDefinition 等）
   * @return 新创建的 DAG ID（雪花算法）
   */
  @Operation(summary = "创建 DAG 工作流")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_CREATE)
  @Idempotent(key = "ydsz:cronjob:JobDagController:createDag:lock", ttlSeconds = 5)
  @Audit(
      module = "DAG管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'createDag'")
  @RateLimit(resource = "cronjob.jobdag.createDag", threshold = 50)
  @PostMapping("/")
  public YdszResponse<String> createDag(@Valid @RequestBody JobDagPostDTO dto) {
    return YdszResponse.success(jobDagService.createDag(toSaveDTO(dto)));
  }

  /**
   * 更新 DAG 工作流。
   *
   * <p>保存新版本时自动 +1 version，并同步生成 {@code ydsz_job_dag_version} 快照记录。 已启动的 DAG
   * 实例继续按旧版本执行；新触发的实例使用新版本。
   *
   * @param dagId DAG ID
   * @param dto DAG 保存请求体
   * @return 统一响应结果
   */
  @Operation(summary = "更新 DAG 工作流")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobDagController:updateDag:lock", ttlSeconds = 5)
  @Audit(
      module = "DAG管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'updateDag'")
  @RateLimit(resource = "cronjob.jobdag.updateDag", threshold = 50)
  @PutMapping("/{dagId}")
  public YdszResponse<Void> updateDag(
      @PathVariable String dagId, @Valid @RequestBody JobDagPutDTO dto) {
    jobDagService.updateDag(dagId, toSaveDTO(dto));
    return YdszResponse.success();
  }

  /**
   * 删除 DAG 工作流。
   *
   * <p>逻辑删除（status 置为 DELETED）。如果 DAG 有运行中的实例，禁止删除并抛业务异常。 历史实例数据保留，不受影响。
   *
   * @param dagId DAG ID
   * @return 统一响应结果
   */
  @Operation(summary = "删除 DAG 工作流")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_DELETE)
  @Idempotent(key = "ydsz:cronjob:JobDagController:deleteDag:lock", ttlSeconds = 5)
  @Audit(
      module = "DAG管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'deleteDag'")
  @RateLimit(resource = "cronjob.jobdag.deleteDag", threshold = 50)
  @DeleteMapping("/{dagId}")
  public YdszResponse<Void> deleteDag(@PathVariable String dagId) {
    jobDagService.deleteDag(dagId);
    return YdszResponse.success();
  }

  /**
   * 启用 DAG 工作流。
   *
   * <p>启用后调度器按 cron 表达式（或外部触发）创建 DAG 实例。运行中的实例不受影响。
   *
   * @param dagId DAG ID
   * @return 统一响应结果
   */
  @Operation(summary = "启用 DAG 工作流")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobDagController:enableDag:lock", ttlSeconds = 5)
  @Audit(
      module = "DAG管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'enableDag'")
  @RateLimit(resource = "cronjob.jobdag.enableDag", threshold = 50)
  @PutMapping("/{dagId}/enable")
  public YdszResponse<Void> enableDag(@PathVariable String dagId) {
    jobDagService.enableDag(dagId);
    return YdszResponse.success();
  }

  /**
   * 禁用 DAG 工作流。
   *
   * <p>禁用后调度器不再按 cron 触发新实例，但运行中的实例继续执行完成。 手动触发也会被拒绝。
   *
   * @param dagId DAG ID
   * @return 统一响应结果
   */
  @Operation(summary = "禁用 DAG 工作流")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobDagController:disableDag:lock", ttlSeconds = 5)
  @Audit(
      module = "DAG管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'disableDag'")
  @RateLimit(resource = "cronjob.jobdag.disableDag", threshold = 50)
  @PutMapping("/{dagId}/disable")
  public YdszResponse<Void> disableDag(@PathVariable String dagId) {
    jobDagService.disableDag(dagId);
    return YdszResponse.success();
  }

  /**
   * 查询 DAG 工作流详情。
   *
   * <p>按 ID 查最新版本。返回的 VO 经过 {@link CronjobConverter} 转换， 包含 dagDefinition 完整定义（节点/边/参数）、当前版本号、状态等。
   *
   * @param dagId DAG ID
   * @return DAG 详情 VO
   */
  @Operation(summary = "查询 DAG 工作流详情")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
  @GetMapping("/{dagId}")
  public YdszResponse<JobDagVO> getDagById(@PathVariable String dagId) {
    return YdszResponse.success(
        jobDagService.getDagById(dagId));
  }

  /**
   * 根据 KEY 查询 DAG 工作流。
   *
   * <p>外部系统集成时常用 dagKey（业务语义化的唯一标识）而非雪花 ID。 dagKey 在租户内唯一，由创建者保证。
   *
   * @param dagKey DAG 唯一 KEY
   * @return DAG 详情 VO
   */
  @Operation(summary = "根据 KEY 查询 DAG 工作流")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
  @GetMapping("/key/{dagKey}")
  public YdszResponse<JobDagVO> getDagByKey(@PathVariable String dagKey) {
    return YdszResponse.success(
        jobDagService.getDagByKey(dagKey));
  }

  /**
   * 查询所有启用的 DAG 工作流。
   *
   * <p>调度器启动时调用，构建内存 DAG 索引。外部系统也可用于下拉选择器。
   *
   * @return 启用的 DAG 列表
   */
  @Operation(summary = "查询所有启用的 DAG 工作流")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
  @GetMapping("/enabled")
  public YdszResponse<List<JobDagVO>> listEnabledDags() {
    return YdszResponse.success(
        jobDagService.listEnabledDags());
  }

  /**
   * 手动触发 DAG 工作流。
   *
   * <p>立即创建一个新的 DAG 实例，返回实例 ID 用于追踪执行进度。 被 {@link IdempotentExempt} 豁免（手动触发本身允许重复），但通过 {@link
   * RateLimit} 限流。
   *
   * @param dto DAG 触发请求体（dagKey + triggerBy）
   * @return 新创建的 DAG 实例 ID
   */
  @Operation(summary = "手动触发 DAG 工作流")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_TRIGGER)
  @IdempotentExempt("定时触发接口，无需幂等")
  @Audit(
      module = "DAG管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'triggerDag'")
  @RateLimit(resource = "cronjob.jobdag.triggerDag", threshold = 50)
  @Idempotent(key = "ydsz:cronjob:JobDagController:triggerDag:lock", ttlSeconds = 5)
  @PostMapping("/trigger")
  public YdszResponse<String> triggerDag(@Valid @RequestBody JobDagTriggerDTO dto) {
    return YdszResponse.success(jobDagService.triggerDag(dto.getDagKey(), dto.getTriggerBy()));
  }

  /**
   * P0-3: 校验 DAG 定义 JSON（可视化编辑器保存前校验）。
   *
   * <p>校验规则：节点完整性、边完整性、无自环、无环、根节点存在、节点类型约束、规模限制。 校验通过返回 true，校验失败抛业务异常并返回错误信息（{@code
   * BizException}）。
   *
   * <p>典型调用方：前端 DAG 编辑器在"保存"按钮点击时异步调用，错误时弹出校验详情面板。
   *
   * @param dagDefinitionJson DAG 定义 JSON 字符串
   * @return 校验结果（true=通过）
   */
  @Operation(summary = "校验 DAG 定义")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
  @Idempotent(key = "ydsz:cronjob:JobDagController:validateDag:lock", ttlSeconds = 5)
  @Audit(
      module = "DAG管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'validateDag'")
  @RateLimit(resource = "cronjob.jobdag.validateDag", threshold = 50)
  @PostMapping("/validate")
  public YdszResponse<Boolean> validateDag(@RequestBody String dagDefinitionJson) {
    DagDefinition definition = dagDefinitionCodec.fromJson(dagDefinitionJson);
    dagDefinitionValidator.validate(definition);
    return YdszResponse.success(true);
  }

  /**
   * P1-4: 查询 DAG 版本历史列表。
   *
   * <p>返回指定 DAG 的所有历史版本快照（按版本号降序，最多 50 条）。 用于版本回滚 UI：用户选择某个历史版本 → 调用 {@link #rollbackDag}。
   *
   * @param dagId DAG ID
   * @return 版本历史列表（含 version/dagDefinition/createdBy/createdAt）
   */
  @Operation(summary = "查询 DAG 版本历史")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
  @GetMapping("/{dagId}/versions")
  public YdszResponse<List<JobDagVersionVO>> listDagVersions(@PathVariable String dagId) {
    return YdszResponse.success(
        jobDagService.listDagVersions(dagId, DEFAULT_VERSION_LIST_LIMIT));
  }

  /**
   * P1-4: 回滚 DAG 到指定版本。
   *
   * <p>将目标历史版本的定义复制为新版本（version = 当前 version + 1）， 保留完整的版本谱系（不会删除中间版本），支持后续再次回滚。
   * 已运行的实例继续按其启动时的版本执行。
   *
   * @param dagId DAG ID
   * @param version 目标版本号（必须 ≤ 当前版本号）
   * @return 回滚后的 DAG 最新定义
   */
  @Operation(summary = "回滚 DAG 到指定版本")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobDagController:rollbackDag:lock", ttlSeconds = 5)
  @Audit(
      module = "DAG管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'rollbackDag'")
  @RateLimit(resource = "cronjob.jobdag.rollbackDag", threshold = 50)
  @PostMapping("/{dagId}/rollback")
  public YdszResponse<JobDagVO> rollbackDag(
      @PathVariable String dagId, @RequestParam Integer version) {
    jobDagService.rollbackDagVersion(dagId, version, AuthContextUtils.getUserId());
    return YdszResponse.success(
        jobDagService.getDagById(dagId));
  }

  /** 将 PostDTO 转换为 SaveDTO。 */
  private JobDagSaveDTO toSaveDTO(JobDagPostDTO dto) {
    JobDagSaveDTO saveDTO = new JobDagSaveDTO();
    saveDTO.setDagKey(dto.getDagKey());
    saveDTO.setDagName(dto.getDagName());
    saveDTO.setDagDefinition(dto.getDagDefinition());
    saveDTO.setStatus(dto.getStatus());
    saveDTO.setTriggerType(dto.getTriggerType());
    saveDTO.setCronExpression(dto.getCronExpression());
    saveDTO.setMaxConcurrentInstances(dto.getMaxConcurrentInstances());
    saveDTO.setFailStrategy(dto.getFailStrategy());
    saveDTO.setDescription(dto.getDescription());
    return saveDTO;
  }

  /** 将 PutDTO 转换为 SaveDTO。 */
  private JobDagSaveDTO toSaveDTO(JobDagPutDTO dto) {
    JobDagSaveDTO saveDTO = new JobDagSaveDTO();
    saveDTO.setDagKey(dto.getDagKey());
    saveDTO.setDagName(dto.getDagName());
    saveDTO.setDagDefinition(dto.getDagDefinition());
    saveDTO.setStatus(dto.getStatus());
    saveDTO.setTriggerType(dto.getTriggerType());
    saveDTO.setCronExpression(dto.getCronExpression());
    saveDTO.setMaxConcurrentInstances(dto.getMaxConcurrentInstances());
    saveDTO.setFailStrategy(dto.getFailStrategy());
    saveDTO.setDescription(dto.getDescription());
    return saveDTO;
  }
}
