package com.njydsz.pmis.project.web.controller.common;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.domain.dto.AlertDispatchDTO;
import com.njydsz.pmis.project.domain.entity.AlertDispatchDO;
import com.njydsz.pmis.project.server.service.AlertDispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/alertDispatch")
@RequiredArgsConstructor
@Validated
public class AlertDispatchController {

    /** 预警分级推送服务 */
    private final AlertDispatchService service;

    /**
     * 提交预警（自动按 level 解析目标角色）
     *
     * @param dto 预警提交参数
     * @return 预警记录 ID
     */
    @Operation(summary = "提交预警（自动按 level 解析目标角色）")
    @Idempotent(key = "alertDispatch:submit", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> submit(@Valid @RequestBody AlertDispatchDTO dto) {
        return Result.ok(service.submit(dto));
    }

    /**
     * 立即分发
     *
     * @param id 预警记录 ID
     * @return 分发是否成功
     */
    @Operation(summary = "立即分发")
    @Idempotent(key = "alertDispatch:dispatchNow", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/dispatch")
    public Result<Boolean> dispatchNow(@PathVariable String id) {
        return Result.ok(service.dispatchNow(id));
    }

    /**
     * 重试失败预警
     *
     * @param maxRetry 最大重试次数，默认 3
     * @return 重试成功的预警数量
     */
    @Operation(summary = "重试失败预警")
    @Idempotent(key = "alertDispatch:retryFailed", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/retry")
    public Result<Integer> retryFailed(@RequestParam(defaultValue = "3") int maxRetry) {
        return Result.ok(service.retryFailed(maxRetry));
    }

    /**
     * 取消预警
     *
     * @param id     预警记录 ID
     * @param reason 取消原因，可选
     * @return 空结果
     */
    @Operation(summary = "取消预警")
    @Idempotent(key = "alertDispatch:cancel", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable String id, @RequestParam(required = false) String reason) {
        service.cancel(id, reason);
        return Result.ok();
    }

    /**
     * 按等级+状态查询
     *
     * @param level  预警等级，可选
     * @param status 预警状态，可选
     * @return 预警记录列表
     */
    @Operation(summary = "按等级+状态查询")
    @GetMapping("/list")
    public Result<List<AlertDispatchDO>> list(@RequestParam(required = false) String level,
                                         @RequestParam(required = false) String status) {
        return Result.ok(service.listByLevelAndStatus(level, status));
    }

    /**
     * 按类型 × 等级 聚合统计
     *
     * @param tenantId 租户 ID，可选
     * @return 聚合统计列表
     */
    @Operation(summary = "按类型 × 等级 聚合统计")
    @GetMapping("/aggregate")
    public Result<List<Map<String, Object>>> aggregate(@RequestParam(required = false) String tenantId) {
        return Result.ok(service.aggregateByTypeAndLevel(tenantId));
    }

    /**
     * 解析等级对应目标角色（黄 → PM/PMO；红 → PMO/GM/CFO）
     *
     * @param level 预警等级
     * @return 目标角色列表
     */
    @Operation(summary = "解析等级对应目标角色（黄 → PM/PMO；红 → PMO/GM/CFO）")
    @GetMapping("/resolveRoles")
    public Result<List<String>> resolveRoles(@RequestParam String level) {
        return Result.ok(service.resolveTargetRoles(level));
    }
}
