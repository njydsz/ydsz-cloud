package com.njydsz.pmis.workflow.web.controller.integration;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.njydsz.pmis.common.audit.annotation.OperationLog;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.workflow.domain.entity.FlowAutoTriggerDO;
import com.njydsz.pmis.workflow.server.service.FlowAutoTriggerService;
import com.njydsz.pmis.workflow.domain.dto.FlowAutoTriggerCreateDTO;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 流程自动触发规则 HTTP API
 *
 * <p>提供触发规则的 CRUD 管理接口，支持列表查询、创建、删除、启用/禁用切换。
 * 触发规则在流程实例完成时自动生效，无需手动调用。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
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
    public BaseResponse<List<FlowAutoTriggerDO>> list() {
        return BaseResponse.ok(autoTriggerService.listAll());
    }

    /**
     * 创建触发规则
     *
     * @param body 请求体，包含 sourceFlowCode / targetFlowCode / conditionExpression / description
     * @return 创建结果
     */
    @Operation(summary = "创建触发规则")
    @Idempotent(key = "flowAutoTrigger:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<Void> create(@Valid @RequestBody FlowAutoTriggerCreateDTO dto) {
        String sourceFlowCode = dto.getSourceFlowCode();
        String targetFlowCode = dto.getTargetFlowCode();
        String conditionExpression = dto.getConditionExpression();
        autoTriggerService.registerTrigger(sourceFlowCode, targetFlowCode, conditionExpression);
        return BaseResponse.ok();
    }

    /**
     * 删除触发规则
     *
     * @param id 规则 ID
     * @return 删除结果
     */
    @Operation(summary = "删除触发规则")
    @OperationLog(module = "工作流", action = "删除触发规则", bizType = "FLOW_AUTO_TRIGGER")
    @Idempotent(key = "flowAutoTrigger:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        autoTriggerService.deleteById(id);
        return BaseResponse.ok();
    }

    /**
     * 启用/禁用触发规则
     *
     * @param id 规则 ID
     * @return 切换后的状态
     */
    @Operation(summary = "启用/禁用触发规则")
    @Idempotent(key = "flowAutoTrigger:toggle", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/toggle")
    public BaseResponse<Map<String, Object>> toggle(@PathVariable String id) {
        boolean enabled = autoTriggerService.toggleEnabled(id);
        return BaseResponse.ok(Map.of("id", id, "enabled", enabled));
    }
}