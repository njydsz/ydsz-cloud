package com.njydsz.workflow.web.controller.instance;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.workflow.domain.entity.FlowAuditLog;
import com.njydsz.workflow.infra.mapper.FlowAuditLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 加签历史独立视图 Controller（P1-8）
 *
 * <p>对标钉钉/飞书审批"加签历史"能力，提供独立的加签操作查询接口， 前端可在审批详情页以独立卡片/抽屉展示加签轨迹，与普通审批时间线区分。
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/engine/countersign/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li>{@code GET /history/instance/{instanceId}} — 查询某实例的全部加签历史（按时间正序）
 *   <li>{@code GET /history/task/{taskId}} — 查询某任务上的加签轨迹
 * </ul>
 *
 * <p><b>加签类型：</b>
 *
 * <ul>
 *   <li>前加签（{@code COUNTERSIGN_BEFORE}）- 在当前审批人之前增加审批人
 *   <li>后加签（{@code COUNTERSIGN_AFTER}）- 在当前审批人之后增加审批人
 *   <li>并加签（{@code COUNTERSIGN_PARALLEL}）- 与当前审批人并行审批
 *   <li>减签（{@code COUNTERSIGN_REMOVE}）- 从会签中移除审批人
 * </ul>
 *
 * <p><b>与 FlowAuditLog 的关系：</b>加签历史是 {@code FlowAuditLog} 中 {@code action} 字段 以 {@code
 * COUNTERSIGN_*} 开头的子集。本 Controller 通过 {@code COUNTERSIGN_ACTIONS} 常量做过滤， 仅返回加签相关记录，避免业务方在前端做二次过滤。
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传与类型过滤；数据查询委托 {@link FlowAuditLogMapper}， 不做业务逻辑编排（保持视图层纯粹性）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.infra.mapper.FlowAuditLogMapper 审计日志 Mapper
 * @see com.njydsz.workflow.domain.entity.FlowAuditLog 审计日志实体
 */
@Slf4j
@RestController
@Tag(name = "workflow-countersign-history", description = "加签历史独立视图接口")
@RequestMapping("/api/v1/workflow/engine/countersign")
@RequiredArgsConstructor
@Validated
public class FlowCountersignHistoryController {

  private final FlowAuditLogMapper auditLogMapper;

  /** 加签类型常量 */
  private static final List<String> COUNTERSIGN_ACTIONS =
      List.of(
          "COUNTERSIGN_BEFORE", "COUNTERSIGN_AFTER", "COUNTERSIGN_PARALLEL", "COUNTERSIGN_REMOVE");

  /**
   * 查询指定流程实例的加签历史记录。
   *
   * @param instanceId 流程实例 ID
   * @param pageNo 页码（默认 1）
   * @param pageSize 每页大小（默认 20，上限 100）
   * @return 加签历史列表
   */
  @GetMapping("/instance/{instanceId}")
  @Operation(summary = "查询流程实例的加签历史")
  public BaseResponse<List<Map<String, Object>>> byInstanceId(
      @PathVariable String instanceId,
      @RequestParam(defaultValue = "1") @Min(1) int pageNo,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
    List<FlowAuditLog> logs = auditLogMapper.selectByInstanceId(instanceId);
    List<Map<String, Object>> filtered =
        logs == null
            ? List.of()
            : logs.stream()
                .filter(log -> COUNTERSIGN_ACTIONS.contains(log.getAction()))
                .map(this::toCountersignVO)
                .toList();
    int total = filtered.size();
    int fromIndex = Math.min((pageNo - 1) * pageSize, total);
    int toIndex = Math.min(fromIndex + pageSize, total);
    List<Map<String, Object>> pageData = filtered.subList(fromIndex, toIndex);
    return PageResponse.success((long) total, (long) pageNo, (long) pageSize, pageData);
  }

  /**
   * 查询指定任务的加签历史记录。
   *
   * @param taskId 任务 ID
   * @param pageNo 页码（默认 1）
   * @param pageSize 每页大小（默认 20，上限 100）
   * @return 加签历史列表
   */
  @GetMapping("/task/{taskId}")
  @Operation(summary = "查询任务的加签历史")
  public BaseResponse<List<Map<String, Object>>> byTaskId(
      @PathVariable String taskId,
      @RequestParam(defaultValue = "1") @Min(1) int pageNo,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
    List<FlowAuditLog> logs = auditLogMapper.selectByTaskId(taskId);
    List<Map<String, Object>> filtered =
        logs == null
            ? List.of()
            : logs.stream()
                .filter(log -> COUNTERSIGN_ACTIONS.contains(log.getAction()))
                .map(this::toCountersignVO)
                .toList();
    int total = filtered.size();
    int fromIndex = Math.min((pageNo - 1) * pageSize, total);
    int toIndex = Math.min(fromIndex + pageSize, total);
    List<Map<String, Object>> pageData = filtered.subList(fromIndex, toIndex);
    return PageResponse.success((long) total, (long) pageNo, (long) pageSize, pageData);
  }

  /**
   * 查询当前用户发起的加签历史。
   *
   * @param pageNo 页码（默认 1）
   * @param pageSize 每页大小（默认 20，上限 100）
   * @return 加签历史列表
   */
  @GetMapping("/myInitiated")
  @Operation(summary = "查询当前用户发起的加签历史")
  public BaseResponse<List<Map<String, Object>>> myInitiated(
      @RequestParam(defaultValue = "1") @Min(1) int pageNo,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
    String currentUserId = AuthContextUtils.getUserId();
    if (currentUserId == null) {
      return PageResponse.empty((long) pageNo, (long) pageSize);
    }
    List<FlowAuditLog> logs = auditLogMapper.selectByOperatorId(currentUserId);
    List<Map<String, Object>> filtered =
        logs == null
            ? List.of()
            : logs.stream()
                .filter(log -> COUNTERSIGN_ACTIONS.contains(log.getAction()))
                .map(this::toCountersignVO)
                .toList();
    int total = filtered.size();
    int fromIndex = Math.min((pageNo - 1) * pageSize, total);
    int toIndex = Math.min(fromIndex + pageSize, total);
    List<Map<String, Object>> pageData = filtered.subList(fromIndex, toIndex);
    return PageResponse.success((long) total, (long) pageNo, (long) pageSize, pageData);
  }

  /**
   * 查询当前用户被加签的记录（即加签目标）。
   *
   * @param pageNo 页码（默认 1）
   * @param pageSize 每页大小（默认 20，上限 100）
   * @return 加签历史列表
   */
  @GetMapping("/myReceived")
  @Operation(summary = "查询当前用户被加签的记录")
  public BaseResponse<List<Map<String, Object>>> myReceived(
      @RequestParam(defaultValue = "1") @Min(1) int pageNo,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
    String currentUserId = AuthContextUtils.getUserId();
    if (currentUserId == null) {
      return PageResponse.empty((long) pageNo, (long) pageSize);
    }
    List<FlowAuditLog> logs = auditLogMapper.selectByTargetId(currentUserId);
    List<Map<String, Object>> filtered =
        logs == null
            ? List.of()
            : logs.stream()
                .filter(log -> COUNTERSIGN_ACTIONS.contains(log.getAction()))
                .map(this::toCountersignVO)
                .toList();
    int total = filtered.size();
    int fromIndex = Math.min((pageNo - 1) * pageSize, total);
    int toIndex = Math.min(fromIndex + pageSize, total);
    List<Map<String, Object>> pageData = filtered.subList(fromIndex, toIndex);
    return PageResponse.success((long) total, (long) pageNo, (long) pageSize, pageData);
  }

  // ==================== 内部辅助方法 ====================

  /** 将审计日志转换为加签视图 VO */
  private Map<String, Object> toCountersignVO(FlowAuditLog log) {
    Map<String, Object> vo = new LinkedHashMap<>();
    vo.put("id", log.getId());
    vo.put("instanceId", log.getInstanceId());
    vo.put("taskId", log.getTaskId());
    vo.put("flowCode", log.getFlowCode());
    vo.put("nodeCode", log.getNodeCode());
    vo.put("nodeName", log.getNodeName());
    vo.put("action", log.getAction());
    vo.put("actionName", getActionName(log.getAction()));
    vo.put("operatorId", log.getOperatorId());
    vo.put("operatorName", log.getOperatorName());
    vo.put("targetId", log.getTargetId());
    vo.put("targetName", log.getTargetName());
    vo.put("comment", log.getComment());
    vo.put("operatedAt", log.getOperatedAt());
    return vo;
  }

  /** 获取加签操作名称 */
  private String getActionName(String action) {
    if (action == null) {
      return "未知";
    }
    return switch (action) {
      case "COUNTERSIGN_BEFORE" -> "前加签";
      case "COUNTERSIGN_AFTER" -> "后加签";
      case "COUNTERSIGN_PARALLEL" -> "并加签";
      case "COUNTERSIGN_REMOVE" -> "减签";
      default -> "未知加签操作";
    };
  }
}
