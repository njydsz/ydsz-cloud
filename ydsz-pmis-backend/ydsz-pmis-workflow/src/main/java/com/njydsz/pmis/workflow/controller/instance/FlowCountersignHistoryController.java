package com.njydsz.pmis.workflow.controller.instance;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.entity.analytics.FlowAuditLogDO;
import com.njydsz.pmis.workflow.mapper.analytics.FlowAuditLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 加签历史独立视图 Controller
 *
 * <p>P1-8: 对标钉钉/飞书审批"加签历史"能力，提供独立的加签操作查询接口，
 * 前端可在审批详情页以独立卡片/抽屉展示加签轨迹，与普通审批时间线区分。
 *
 * <p>加签类型包括：
 * <ul>
 *   <li>前加签（COUNTERSIGN_BEFORE）- 在当前审批人之前增加审批人</li>
 *   <li>后加签（COUNTERSIGN_AFTER）- 在当前审批人之后增加审批人</li>
 *   <li>并加签（COUNTERSIGN_PARALLEL）- 与当前审批人并行审批</li>
 *   <li>减签（COUNTERSIGN_REMOVE）- 从会签中移除审批人</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-countersign-history", description = "加签历史独立视图接口")
@RequestMapping("/workflow/engine/countersign")
@RequiredArgsConstructor
@Validated
public class FlowCountersignHistoryController {

    private final FlowAuditLogMapper auditLogMapper;

    /**
     * 加签类型常量
     */
    private static final List<String> COUNTERSIGN_ACTIONS = List.of(
            "COUNTERSIGN_BEFORE",
            "COUNTERSIGN_AFTER",
            "COUNTERSIGN_PARALLEL",
            "COUNTERSIGN_REMOVE"
    );

    /**
     * 查询指定流程实例的加签历史记录。
     *
     * @param instanceId 流程实例 ID
     * @param pageNo     页码（默认 1）
     * @param pageSize   每页大小（默认 20，上限 100）
     * @return 加签历史列表
     */
    @GetMapping("/instance/{instanceId}")
    @Operation(summary = "查询流程实例的加签历史")
    public Result<PageResult<Map<String, Object>>> byInstanceId(
            @PathVariable String instanceId,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        List<FlowAuditLogDO> logs = auditLogMapper.selectByInstanceId(instanceId);
        List<Map<String, Object>> filtered = logs == null ? List.of() :
                logs.stream()
                        .filter(log -> COUNTERSIGN_ACTIONS.contains(log.getAction()))
                        .map(this::toCountersignVO)
                        .toList();
        int total = filtered.size();
        int fromIndex = Math.min((pageNo - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Map<String, Object>> pageData = filtered.subList(fromIndex, toIndex);
        return Result.ok(PageResult.of(pageData, total, pageNo, pageSize));
    }

    /**
     * 查询指定任务的加签历史记录。
     *
     * @param taskId 任务 ID
     * @param pageNo  页码（默认 1）
     * @param pageSize 每页大小（默认 20，上限 100）
     * @return 加签历史列表
     */
    @GetMapping("/task/{taskId}")
    @Operation(summary = "查询任务的加签历史")
    public Result<PageResult<Map<String, Object>>> byTaskId(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        List<FlowAuditLogDO> logs = auditLogMapper.selectByTaskId(taskId);
        List<Map<String, Object>> filtered = logs == null ? List.of() :
                logs.stream()
                        .filter(log -> COUNTERSIGN_ACTIONS.contains(log.getAction()))
                        .map(this::toCountersignVO)
                        .toList();
        int total = filtered.size();
        int fromIndex = Math.min((pageNo - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Map<String, Object>> pageData = filtered.subList(fromIndex, toIndex);
        return Result.ok(PageResult.of(pageData, total, pageNo, pageSize));
    }

    /**
     * 查询当前用户发起的加签历史。
     *
     * @param pageNo   页码（默认 1）
     * @param pageSize 每页大小（默认 20，上限 100）
     * @return 加签历史列表
     */
    @GetMapping("/myInitiated")
    @Operation(summary = "查询当前用户发起的加签历史")
    public Result<PageResult<Map<String, Object>>> myInitiated(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        String currentUserId = SecurityContext.getUserId();
        if (currentUserId == null) {
            return Result.ok(PageResult.empty());
        }
        List<FlowAuditLogDO> logs = auditLogMapper.selectByOperatorId(currentUserId);
        List<Map<String, Object>> filtered = logs == null ? List.of() :
                logs.stream()
                        .filter(log -> COUNTERSIGN_ACTIONS.contains(log.getAction()))
                        .map(this::toCountersignVO)
                        .toList();
        int total = filtered.size();
        int fromIndex = Math.min((pageNo - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Map<String, Object>> pageData = filtered.subList(fromIndex, toIndex);
        return Result.ok(PageResult.of(pageData, total, pageNo, pageSize));
    }

    /**
     * 查询当前用户被加签的记录（即加签目标）。
     *
     * @param pageNo   页码（默认 1）
     * @param pageSize 每页大小（默认 20，上限 100）
     * @return 加签历史列表
     */
    @GetMapping("/myReceived")
    @Operation(summary = "查询当前用户被加签的记录")
    public Result<PageResult<Map<String, Object>>> myReceived(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        String currentUserId = SecurityContext.getUserId();
        if (currentUserId == null) {
            return Result.ok(PageResult.empty());
        }
        List<FlowAuditLogDO> logs = auditLogMapper.selectByTargetId(currentUserId);
        List<Map<String, Object>> filtered = logs == null ? List.of() :
                logs.stream()
                        .filter(log -> COUNTERSIGN_ACTIONS.contains(log.getAction()))
                        .map(this::toCountersignVO)
                        .toList();
        int total = filtered.size();
        int fromIndex = Math.min((pageNo - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Map<String, Object>> pageData = filtered.subList(fromIndex, toIndex);
        return Result.ok(PageResult.of(pageData, total, pageNo, pageSize));
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 将审计日志转换为加签视图 VO
     */
    private Map<String, Object> toCountersignVO(FlowAuditLogDO log) {
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

    /**
     * 获取加签操作名称
     */
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