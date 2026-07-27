package com.njydsz.workflow.web.controller.integration;

import java.util.List;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.workflow.domain.dto.FlowAutoTriggerCreateDTO;
import com.njydsz.workflow.domain.entity.FlowAutoTrigger;
import com.njydsz.workflow.server.service.FlowAutoTriggerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.vo.FlowAutoTriggerVO;

/**
 * 流程自动触发规则 HTTP API
 *
 * <p>提供触发规则的 CRUD 管理接口，支持列表查询、创建、删除、启用/禁用切换。
 * 触发规则在流程实例完成时自动生效，无需手动调用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "流程自动触发规则")
@RestController
@RequestMapping("/workflow/trigger")
@RequiredArgsConstructor
@Validated
public class FlowAutoTriggerController {

    /** 流程自动触发规则服务，负责规则注册、删除与启用/禁用管理 */
    private final FlowAutoTriggerService autoTriggerService;

    /**
     * 列出所有触发规则
     *
     * @return 触发规则列表
     */
    @Operation(summary = "列出所有触发规则")
    @GetMapping("/list")
    public BaseResponse<List<FlowAutoTriggerVO>> list() {
        return BaseResponse.success(WorkflowConverter.INSTANT.flowAutoTriggerListToVO(autoTriggerService.listAll()));
    }

    /**
     * 创建触发规则
     *
     * @param body 请求体，包含 sourceFlowCode / targetFlowCode / conditionExpression / description
     * @return 创建结果
     */
    @Operation(summary = "创建触发规则")
    @Idempotent(key = "ydsz:workflow:FlowAutoTriggerController:create:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowautotrigger.create", threshold = 50)
    @PostMapping
    public BaseResponse<Void> create(@Valid @RequestBody FlowAutoTriggerCreateDTO dto) {
        String sourceFlowCode = dto.getSourceFlowCode();
        String targetFlowCode = dto.getTargetFlowCode();
        String conditionExpression = dto.getConditionExpression();
        autoTriggerService.registerTrigger(sourceFlowCode, targetFlowCode, conditionExpression);
        return BaseResponse.success();
    }

    /**
     * 删除触发规则
     *
     * @param id 规则 ID
     * @return 删除结果
     */
    @Operation(summary = "删除触发规则")
    @Idempotent(key = "ydsz:workflow:FlowAutoTriggerController:delete:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowautotrigger.delete", threshold = 50)
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        autoTriggerService.deleteById(id);
        return BaseResponse.success();
    }

    /**
     * 启用/禁用触发规则
     *
     * @param id 规则 ID
     * @return 切换后的状态
     */
    @Operation(summary = "启用/禁用触发规则")
    @Idempotent(key = "ydsz:workflow:FlowAutoTriggerController:toggle:lock", ttlSeconds = 5)
    @PutMapping("/{id}/toggle")
    public BaseResponse<Map<String, Object>> toggle(@PathVariable String id) {
        boolean enabled = autoTriggerService.toggleEnabled(id);
        return BaseResponse.success(Map.of("id", id, "enabled", enabled));
    }
}