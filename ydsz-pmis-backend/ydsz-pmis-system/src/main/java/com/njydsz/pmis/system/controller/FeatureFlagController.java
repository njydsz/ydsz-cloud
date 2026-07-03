package com.njydsz.pmis.config.controller;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.featureflag.FeatureFlag;
import com.njydsz.pmis.common.featureflag.FeatureFlagService;
import com.njydsz.pmis.common.featureflag.FeatureFlagSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 特性开关管理接口 (批次 20 P2-3)
 *
 * <p>前端控制台 / 灰度发布面板通过此接口管理 feature flag.
 *
 * <p>权限码:
 * <ul>
 *   <li>{@code sys:feature-flag:view} - 查看快照</li>
 *   <li>{@code sys:feature-flag:update} - 启停 / 灰度</li>
 *   <li>{@code sys:feature-flag:check} - 业务方判断某 flag 是否启用 (无权限要求)</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次20)
 */
@Tag(name = "系统-特性开关")
@RestController
@RequestMapping("/api/v1/feature-flags")
@RequiredArgsConstructor
public class FeatureFlagController {

    /** 特性开关服务 */
    private final FeatureFlagService featureFlagService;

    /**
     * 获取全量 flag 快照
     *
     * @return 统一响应结果，包含 flag 快照列表
     */
    @Operation(summary = "获取全量 flag 快照")
    @PrePermission("sys:feature-flag:view")
    @GetMapping("/snapshot")
    public Result<List<FeatureFlagSnapshot>> snapshot() {
        return Result.ok(featureFlagService.snapshot());
    }

    /**
     * 按分类聚合快照
     *
     * @return 统一响应结果，包含按分类分组的 flag 快照
     */
    @Operation(summary = "按分类聚合快照")
    @PrePermission("sys:feature-flag:view")
    @GetMapping("/snapshot/grouped")
    public Result<Map<String, List<FeatureFlagSnapshot>>> snapshotByCategory() {
        return Result.ok(featureFlagService.snapshotByCategory());
    }

    /**
     * 业务方判断 flag 是否启用（无权限校验）
     *
     * @param key    flag 键
     * @param userId 用户 ID（可选，用于灰度判断）
     * @return 统一响应结果，包含是否启用
     */
    @Operation(summary = "业务方判断 flag 是否启用 (无权限校验)")
    @GetMapping("/check")
    public Result<Boolean> check(@RequestParam @NotNull String key,
                            @RequestParam(required = false) Long userId) {
        FeatureFlag flag;
        try {
            flag = FeatureFlag.valueOf(key);
        } catch (IllegalArgumentException e) {
            return Result.ok(false);
        }
        return Result.ok(featureFlagService.isEnabled(flag, userId));
    }

    /**
     * 启停指定 flag
     *
     * @param key     flag 键
     * @param enabled 是否启用
     * @return 统一响应结果，包含生效状态
     */
    @Operation(summary = "启停指定 flag")
    @PrePermission("sys:feature-flag:update")
    @OperationLog(module = "特性开关", action = "更新开关", bizType = "FEATURE_FLAG")
    @PutMapping("/{key}/enabled")
    public Result<Boolean> setEnabled(@PathVariable String key,
                                 @RequestParam boolean enabled) {
        FeatureFlag flag = parseFlag(key);
        boolean effective = featureFlagService.setEnabled(flag, enabled);
        return Result.ok(effective);
    }

    /**
     * 设置灰度发布比例（0-100）
     *
     * @param key        flag 键
     * @param percentage 灰度百分比
     * @return 统一响应结果，包含实际生效的百分比
     */
    @Operation(summary = "设置灰度发布比例 (0-100)")
    @PrePermission("sys:feature-flag:update")
    @OperationLog(module = "特性开关", action = "更新灰度", bizType = "FEATURE_FLAG")
    @PutMapping("/{key}/rollout")
    public Result<Integer> setRollout(@PathVariable String key,
                                 @RequestParam @Min(0) @Max(100) int percentage) {
        FeatureFlag flag = parseFlag(key);
        int clamped = featureFlagService.setRolloutPercentage(flag, percentage);
        return Result.ok(clamped);
    }

    /**
     * 强制刷新本地缓存
     *
     * @return 统一响应结果
     */
    @Operation(summary = "强制刷新本地缓存")
    @PrePermission("sys:feature-flag:update")
    @OperationLog(module = "特性开关", action = "刷新缓存", bizType = "FEATURE_FLAG")
    @PostMapping("/refresh")
    public Result<Void> refresh() {
        featureFlagService.refresh();
        return Result.ok();
    }

    /**
     * 解析 flag 键为枚举
     *
     * @param key flag 键
     * @return 特性开关枚举
     * @throws IllegalArgumentException 当 key 未知时抛出
     */
    private static FeatureFlag parseFlag(String key) {
        try {
            return FeatureFlag.valueOf(key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知的特性开关: " + key);
        }
    }
}
