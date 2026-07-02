package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.execution.dto.AlertDispatchDTO;
import com.njydsz.pmis.execution.entity.AlertDispatchDO;
import com.njydsz.pmis.execution.service.AlertDispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 预警分级推送 Controller（P4-2）
 *
 * <p>黄色预警 → PM + PMO；红色预警 → PMO + GM + CFO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "预警分级推送")
@RestController
@RequestMapping("/api/v1/execution/alert-dispatch")
@RequiredArgsConstructor
public class AlertDispatchController {

    private final AlertDispatchService service;

    @Operation(summary = "提交预警（自动按 level 解析目标角色）")
    @PostMapping
    public Result<Long> submit(@Valid @RequestBody AlertDispatchDTO dto) {
        return Result.ok(service.submit(dto));
    }

    @Operation(summary = "立即分发")
    @PutMapping("/{id}/dispatch")
    public Result<Boolean> dispatchNow(@PathVariable Long id) {
        return Result.ok(service.dispatchNow(id));
    }

    @Operation(summary = "重试失败预警")
    @PostMapping("/retry")
    public Result<Integer> retryFailed(@RequestParam(defaultValue = "3") int maxRetry) {
        return Result.ok(service.retryFailed(maxRetry));
    }

    @Operation(summary = "取消预警")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        service.cancel(id, reason);
        return Result.ok();
    }

    @Operation(summary = "按等级+状态查询")
    @GetMapping("/list")
    public Result<List<AlertDispatchDO>> list(@RequestParam(required = false) String level,
                                         @RequestParam(required = false) String status) {
        return Result.ok(service.listByLevelAndStatus(level, status));
    }

    @Operation(summary = "按类型 × 等级 聚合统计")
    @GetMapping("/aggregate")
    public Result<List<Map<String, Object>>> aggregate(@RequestParam(required = false) Long tenantId) {
        return Result.ok(service.aggregateByTypeAndLevel(tenantId));
    }

    @Operation(summary = "解析等级对应目标角色（黄 → PM/PMO；红 → PMO/GM/CFO）")
    @GetMapping("/resolve-roles")
    public Result<List<String>> resolveRoles(@RequestParam String level) {
        return Result.ok(service.resolveTargetRoles(level));
    }
}
