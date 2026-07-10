package com.njydsz.pmis.system.controller.chaos;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.chaos.ChaosExperiment;
import com.njydsz.pmis.common.chaos.ChaosOutcome;
import com.njydsz.pmis.common.chaos.ChaosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 混沌工程管理接口 (批次 20 P3-3)
 *
 * <p>前端"混沌工程控制台"通过此接口:
 * <ul>
 *   <li>注册 / 修改 / 注销 / 启停实验</li>
 *   <li>查看实验列表与最近 100 条历史</li>
 *   <li>主动触发一次注入以验证容错 (dry-run)</li>
 * </ul>
 *
 * <p>权限码:
 * <ul>
 *   <li>{@code sys:chaos:view} - 列表 / 历史</li>
 *   <li>{@code sys:chaos:create} - 注册 / 修改</li>
 *   <li>{@code sys:chaos:delete} - 注销</li>
 *   <li>{@code sys:chaos:trigger} - 主动触发 / 启停</li>
 * </ul>
 *
 * <p><b>注意</b>: 本接口仅在 {@code spring.profiles.active in [dev, staging]} 时暴露, 生产环境禁用.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次20)
 */
@Slf4j
@Tag(name = "系统-混沌工程", description = "混沌工程实验管理接口（仅 dev/staging 环境）")
@RestController
@RequestMapping("/chaos")
@RequiredArgsConstructor
@Validated
public class ChaosController {

    /** 混沌工程服务 */
    private final ChaosService chaosService;

    /**
     * 列出全部已注册实验
     *
     * @return 统一响应结果，包含实验列表
     */
    @Operation(summary = "列出全部已注册实验")
    @PrePermission("sys:chaos:view")
    @GetMapping("/experiments")
    public Result<List<ChaosExperiment>> list() {
        return Result.ok(chaosService.list());
    }

    /**
     * 按 target 查询实验
     *
     * @param target 实验目标标识
     * @return 统一响应结果，包含实验信息
     */
    @Operation(summary = "按 target 查询实验")
    @PrePermission("sys:chaos:view")
    @GetMapping("/experiments/{target}")
    public Result<ChaosExperiment> get(
            @Parameter(description = "实验目标标识") @PathVariable @NotBlank String target) {
        ChaosExperiment found = chaosService.list().stream()
                .filter(e -> target.equals(e.getTarget()))
                .findFirst()
                .orElse(null);
        return Result.ok(found);
    }

    /**
     * 注册新实验
     *
     * @param experiment 实验配置
     * @return 统一响应结果
     */
    @Operation(summary = "注册新实验")
    @PrePermission("sys:chaos:create")
    @OperationLog(module = "混沌工程", action = "注册实验", bizType = "CHAOS_EXPERIMENT")
    @Idempotent(key = "chaos:register", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/experiments")
    public Result<Void> register(@RequestBody @Valid ChaosExperiment experiment) {
        chaosService.register(experiment);
        return Result.ok();
    }

    /**
     * 修改实验（按 target 覆盖）
     *
     * @param target     实验目标标识
     * @param experiment 实验配置
     * @return 统一响应结果
     */
    @Operation(summary = "修改实验 (按 target 覆盖)")
    @PrePermission("sys:chaos:create")
    @OperationLog(module = "混沌工程", action = "更新实验", bizType = "CHAOS_EXPERIMENT")
    @Idempotent(key = "chaos:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/experiments/{target}")
    public Result<Void> update(
            @Parameter(description = "实验目标标识") @PathVariable @NotBlank String target,
            @RequestBody @Valid ChaosExperiment experiment) {
        experiment.setTarget(target);
        chaosService.register(experiment);
        return Result.ok();
    }

    /**
     * 启停实验
     *
     * @param target  实验目标标识
     * @param enabled 是否启用
     * @return 统一响应结果
     */
    @Operation(summary = "启停实验")
    @PrePermission("sys:chaos:trigger")
    @OperationLog(module = "混沌工程", action = "启停实验", bizType = "CHAOS_EXPERIMENT")
    @Idempotent(key = "chaos:toggle", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/experiments/{target}/enabled")
    public Result<Void> toggle(
            @Parameter(description = "实验目标标识") @PathVariable @NotBlank String target,
            @Parameter(description = "是否启用") @RequestParam boolean enabled) {
        ChaosExperiment exp = chaosService.list().stream()
                .filter(e -> target.equals(e.getTarget()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("实验不存在: " + target));
        exp.setEnabled(enabled);
        chaosService.register(exp);
        return Result.ok();
    }

    /**
     * 注销实验
     *
     * @param target 实验目标标识
     * @return 统一响应结果
     */
    @Operation(summary = "注销实验")
    @PrePermission("sys:chaos:delete")
    @OperationLog(module = "混沌工程", action = "注销实验", bizType = "CHAOS_EXPERIMENT")
    @Idempotent(key = "chaos:unregister", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/experiments/{target}")
    public Result<Void> unregister(
            @Parameter(description = "实验目标标识") @PathVariable @NotBlank String target) {
        chaosService.unregister(target);
        return Result.ok();
    }

    /**
     * 查看最近 100 条实验历史
     *
     * @return 统一响应结果，包含事件历史列表
     */
    @Operation(summary = "查看最近 100 条实验历史")
    @PrePermission("sys:chaos:view")
    @GetMapping("/history")
    public Result<List<ChaosService.ChaosEvent>> history() {
        return Result.ok(chaosService.recentHistory());
    }

    /**
     * 清空实验历史
     *
     * @return 统一响应结果
     */
    @Operation(summary = "清空历史")
    @PrePermission("sys:chaos:trigger")
    @OperationLog(module = "混沌工程", action = "清空实验历史", bizType = "CHAOS_EXPERIMENT")
    @Idempotent(key = "chaos:clear-history", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/history/clear")
    public Result<Void> clearHistory() {
        chaosService.clearHistory();
        return Result.ok();
    }

    /**
     * dry-run：主动触发一次注入以验证容错（需 captureMode 包裹异常）
     *
     * @param target 实验目标标识
     * @return 统一响应结果，包含 target、outcome、error 信息
     */
    @Operation(summary = "dry-run: 主动触发一次注入以验证容错 (需 captureMode 包裹异常)")
    @PrePermission("sys:chaos:trigger")
    @Idempotent(key = "chaos:dry-run", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/dry-run")
    public Result<Map<String, Object>> dryRun(
            @Parameter(description = "实验目标标识") @RequestParam @NotBlank String target) {
        // 包装异常: ChaosService 注入时会抛, 这里把异常转成 outcome 字符串
        ChaosOutcome outcome;
        String error = null;
        try {
            outcome = chaosService.maybeInject(target);
        } catch (RuntimeException ex) {
            outcome = ChaosOutcome.INJECTED;
            error = ex.getClass().getName() + ": " + ex.getMessage();
        }
        return Result.ok(Map.of(
                "target", target,
                "outcome", outcome.name(),
                "error", error == null ? "" : error
        ));
    }
}
