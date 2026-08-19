package com.njydsz.cronjob.web.controller.dag;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;
import com.njydsz.cronjob.server.service.dag.JobDagInstanceService;
import com.njydsz.cronjob.server.vo.DagInstanceVisualizationVO;

/**
 * DAG 工作流实例 Controller（P2 DAG 增强）。
 *
 * <p>提供 DAG 运行实例的全生命周期管理 REST API：
 *
 * <ul>
 *   <li>查询：实例详情 / 按 DAG 查 / 按状态查 / 节点列表 / 可视化
 *   <li>控制：暂停 / 恢复 / 取消
 *   <li>上下文：节点间参数传递（运行时更新）
 * </ul>
 *
 * <h3>实例状态机</h3>
 *
 * <pre>
 *   PENDING → RUNNING ⇄ PAUSED
 *                ↓
 *           SUCCESS / FAILED / CANCELLED
 * </pre>
 *
 * 状态转换由 {@link JobDagInstanceService#canTransitTo} 校验合法性。
 *
 * <h3>可视化数据</h3>
 *
 * {@link #getVisualization} 返回的 VO 包含节点/边的实时状态、运行时长、错误堆栈等， 供前端 DAG 查看器渲染节点颜色（绿/黄/红/灰）和边动画。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "DAG工作流实例", description = "DAG 实例查询、暂停/恢复/取消、可视化、上下文管理")
@Slf4j
@RequestMapping("/api/v1/cronjob/dag/instance")
@RequiredArgsConstructor
public class JobDagInstanceController {

  /** DAG 实例服务 */
  private final JobDagInstanceService jobDagInstanceService;

  /**
   * 查询 DAG 实例详情。
   *
   * <p>返回实例的元信息（status / triggerType / triggerBy / 时间戳）， 不含节点列表（节点列表见 {@link #listNodes}）。
   *
   * @param instanceId DAG 实例 ID
   * @return DAG 实例详情 VO
   */
  @Operation(summary = "查询 DAG 实例详情")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
  @GetMapping("/{instanceId}")
  public YdszResponse<JobDagInstanceVO> getInstanceById(@PathVariable String instanceId) {
    return YdszResponse.success(
        CronjobConverter.INSTANT.entityToVO(jobDagInstanceService.getInstanceById(instanceId)));
  }

  /**
   * 查询指定 DAG 的实例列表。
   *
   * <p>按 start_time 倒序，最近触发的实例排在前面。limit 默认 20，控制单次返回量。
   *
   * @param dagId DAG ID
   * @param limit 最多返回条数（默认 20）
   * @return DAG 实例列表
   */
  @Operation(summary = "查询 DAG 的实例列表")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
  @GetMapping("/dag/{dagId}")
  public YdszResponse<List<JobDagInstanceVO>> listByDagId(
      @PathVariable String dagId, @RequestParam(defaultValue = "20") int limit) {
    return YdszResponse.success(
        CronjobConverter.INSTANT.jobDagInstanceListToVO(
            jobDagInstanceService.listByDagId(dagId, limit)));
  }

  /**
   * 按状态查询 DAG 实例。
   *
   * <p>运维场景下常用：例如查所有 RUNNING 状态的实例做巡检；查所有 FAILED 做失败重跑。
   *
   * @param status 实例状态（RUNNING/PAUSED/SUCCESS/FAILED/CANCELLED）
   * @return DAG 实例列表
   */
  @Operation(summary = "按状态查询 DAG 实例")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
  @GetMapping("/status/{status}")
  public YdszResponse<List<JobDagInstanceVO>> listByStatus(@PathVariable String status) {
    return YdszResponse.success(
        CronjobConverter.INSTANT.jobDagInstanceListToVO(
            jobDagInstanceService.listByStatus(status)));
  }

  /**
   * 查询 DAG 实例的节点列表。
   *
   * <p>展示每个节点实例的状态、开始/结束时间、耗时、错误信息等。 配合 {@link #getInstanceById} 一起使用，构成"实例总览 + 节点明细"的完整视图。
   *
   * @param instanceId DAG 实例 ID
   * @return 节点实例列表
   */
  @Operation(summary = "查询 DAG 实例的节点列表")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
  @GetMapping("/{instanceId}/nodes")
  public YdszResponse<List<JobDagNodeInstanceVO>> listNodes(@PathVariable String instanceId) {
    return YdszResponse.success(
        CronjobConverter.INSTANT.jobDagNodeInstanceListToVO(
            jobDagInstanceService.listNodes(instanceId)));
  }

  /**
   * 获取 DAG 实例可视化数据（P4-1）。
   *
   * <p>返回节点/边的实时状态、运行时长、错误堆栈等， 供前端 DAG 查看器渲染节点颜色（绿/黄/红/灰）和边动画。
   *
   * @param instanceId DAG 实例 ID
   * @return 可视化数据 VO
   */
  @Operation(summary = "获取 DAG 实例可视化数据（P4-1）")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
  @GetMapping("/{instanceId}/visualization")
  public YdszResponse<DagInstanceVisualizationVO> getVisualization(
      @PathVariable String instanceId) {
    return YdszResponse.success(jobDagInstanceService.getVisualization(instanceId));
  }

  /**
   * P2-2: 获取 DAG 实例的 Mermaid 图表文本。
   *
   * <p>返回 Mermaid {@code graph TD} 格式文本，可直接粘贴到支持 Mermaid 的 Markdown 编辑器中渲染。 节点颜色反映实时执行状态（绿=成功
   * / 橙=运行中 / 红=失败 / 灰=待执行）。
   *
   * @param instanceId DAG 实例 ID
   * @return Mermaid 图表文本（包含在 ```mermaid 代码块中）
   */
  @Operation(summary = "获取 DAG 实例 Mermaid 图表文本（P2-2）")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
  @GetMapping("/{instanceId}/mermaid")
  public YdszResponse<String> getMermaidDiagram(@PathVariable String instanceId) {
    return YdszResponse.success(jobDagInstanceService.getMermaidDiagram(instanceId));
  }

  /**
   * 暂停 DAG 实例。
   *
   * <p>将 RUNNING 转为 PAUSED：调度器暂停派发后续节点，正在执行的节点继续完成。 可通过 {@link #resumeInstance} 恢复。已
   * SUCCESS/FAILED/CANCELLED 的实例禁止暂停。
   *
   * @param instanceId DAG 实例 ID
   * @return 统一响应结果
   */
  @Operation(summary = "暂停 DAG 实例")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobDagInstanceController:pauseInstance:lock", ttlSeconds = 5)
  @RateLimit(resource = "cronjob.jobdaginstance.pauseInstance", threshold = 50)
  @PutMapping("/{instanceId}/pause")
  @Audit(
      module = "DAG实例控制",
      type = AuditType.OPERATION,
      action = AuditAction.OTHER,
      content = "'pauseInstance'")
  public YdszResponse<Void> pauseInstance(@PathVariable String instanceId) {
    jobDagInstanceService.pauseInstance(instanceId);
    return YdszResponse.success();
  }

  /**
   * 恢复 DAG 实例。
   *
   * <p>将 PAUSED 转回 RUNNING，调度器继续派发后续节点。 已 SUCCESS/FAILED/CANCELLED 的实例禁止恢复。
   *
   * @param instanceId DAG 实例 ID
   * @return 统一响应结果
   */
  @Operation(summary = "恢复 DAG 实例")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobDagInstanceController:resumeInstance:lock", ttlSeconds = 5)
  @RateLimit(resource = "cronjob.jobdaginstance.resumeInstance", threshold = 50)
  @PutMapping("/{instanceId}/resume")
  @Audit(
      module = "DAG实例控制",
      type = AuditType.OPERATION,
      action = AuditAction.OTHER,
      content = "'resumeInstance'")
  public YdszResponse<Void> resumeInstance(@PathVariable String instanceId) {
    jobDagInstanceService.resumeInstance(instanceId);
    return YdszResponse.success();
  }

  /**
   * 取消 DAG 实例。
   *
   * <p>强制终止实例：正在执行的节点收到取消信号（{@code Thread.interrupt}）， 未执行的节点被跳过。终态为 CANCELLED，不可恢复。
   *
   * @param instanceId DAG 实例 ID
   * @return 统一响应结果
   */
  @Operation(summary = "取消 DAG 实例")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobDagInstanceController:cancelInstance:lock", ttlSeconds = 5)
  @RateLimit(resource = "cronjob.jobdaginstance.cancelInstance", threshold = 50)
  @PutMapping("/{instanceId}/cancel")
  @Audit(
      module = "DAG实例控制",
      type = AuditType.OPERATION,
      action = AuditAction.OTHER,
      content = "'cancelInstance'")
  public YdszResponse<Void> cancelInstance(@PathVariable String instanceId) {
    jobDagInstanceService.cancelInstance(instanceId);
    return YdszResponse.success();
  }

  /**
   * 更新 DAG 实例上下文（用于节点间参数传递）。
   *
   * <p>运行时上下文（runtime context）由各节点读写，实现"上游节点产出 → 下游节点消费"。 该接口用于外部系统注入上下文或运维人工修正。
   *
   * @param instanceId DAG 实例 ID
   * @param contextJson 上下文 JSON 字符串（会被合并而非覆盖已有上下文）
   * @return 统一响应结果
   */
  @Operation(summary = "更新 DAG 实例上下文")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobDagInstanceController:updateContext:lock", ttlSeconds = 5)
  @RateLimit(resource = "cronjob.jobdaginstance.updateContext", threshold = 50)
  @PutMapping("/{instanceId}/context")
  @Audit(
      module = "DAG实例控制",
      type = AuditType.OPERATION,
      action = AuditAction.OTHER,
      content = "'updateContext'")
  public YdszResponse<Void> updateContext(
      @PathVariable String instanceId, @RequestBody String contextJson) {
    jobDagInstanceService.updateContext(instanceId, contextJson);
    return YdszResponse.success();
  }
}
