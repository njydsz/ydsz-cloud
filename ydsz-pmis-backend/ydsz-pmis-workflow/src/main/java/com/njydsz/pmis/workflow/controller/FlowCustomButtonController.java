package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.service.FlowCustomButtonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 节点自定义按钮 Controller（P2-4）。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow/custom-buttons")
@RequiredArgsConstructor
@Tag(name = "节点自定义按钮", description = "流程节点的自定义操作按钮管理")
public class FlowCustomButtonController {

    /** 自定义按钮服务，负责节点按钮配置的查询、保存与执行 */
    private final FlowCustomButtonService customButtonService;

    /**
     * 获取节点的自定义按钮列表。
     *
     * @param definitionId 流程定义 ID
     * @param nodeCode     节点编码
     * @return 按钮配置列表
     */
    @GetMapping
    @Operation(summary = "获取节点的自定义按钮列表")
    public Result<List<Map<String, Object>>> list(
            @RequestParam String definitionId,
            @RequestParam String nodeCode) {
        return Result.ok(customButtonService.getCustomButtons(definitionId, nodeCode));
    }

    /**
     * 保存节点的自定义按钮配置。
     *
     * @param definitionId 流程定义 ID
     * @param nodeCode     节点编码
     * @param buttons      按钮配置列表
     * @return 空响应
     */
    @Idempotent(key = "flow-custom-button:save", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    @Operation(summary = "保存节点的自定义按钮配置")
    public Result<Void> save(
            @RequestParam String definitionId,
            @RequestParam String nodeCode,
            @RequestBody List<Map<String, Object>> buttons) {
        customButtonService.saveCustomButtons(definitionId, nodeCode, buttons);
        return Result.ok();
    }

    /**
     * 执行自定义按钮操作。
     *
     * @param taskId    任务 ID
     * @param buttonCode 按钮编码
     * @param comment   审批意见（可选）
     * @param variables 流程变量（可选）
     * @return 按钮执行结果
     */
    @Idempotent(key = "flow-custom-button:execute", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/execute")
    @Operation(summary = "执行自定义按钮操作")
    public Result<Map<String, Object>> execute(
            @RequestParam String taskId,
            @RequestParam String buttonCode,
            @RequestParam(required = false) String comment,
            @RequestBody(required = false) Map<String, Object> variables) {
        String userId = SecurityContext.getUserId();
        return Result.ok(customButtonService.executeButton(taskId, buttonCode, userId, comment, variables));
    }
}
