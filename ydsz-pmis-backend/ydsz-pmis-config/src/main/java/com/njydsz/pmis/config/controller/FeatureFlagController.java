package com.njydsz.pmis.config.controller;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
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

    private final FeatureFlagService featureFlagService;

    @Operation(summary = "获取全量 flag 快照")
    @PrePermission("sys:feature-flag:view")
    @GetMapping("/snapshot")
    public R<List<FeatureFlagSnapshot>> snapshot() {
        return R.ok(featureFlagService.snapshot());
    }

    @Operation(summary = "按分类聚合快照")
    @PrePermission("sys:feature-flag:view")
    @GetMapping("/snapshot/grouped")
    public R<Map<String, List<FeatureFlagSnapshot>>> snapshotByCategory() {
        return R.ok(featureFlagService.snapshotByCategory());
    }

    @Operation(summary = "业务方判断 flag 是否启用 (无权限校验)")
    @GetMapping("/check")
    public R<Boolean> check(@RequestParam @NotNull String key,
                            @RequestParam(required = false) Long userId) {
        FeatureFlag flag;
        try {
            flag = FeatureFlag.valueOf(key);
        } catch (IllegalArgumentException e) {
            return R.ok(false);
        }
        return R.ok(featureFlagService.isEnabled(flag, userId));
    }

    @Operation(summary = "启停指定 flag")
    @PrePermission("sys:feature-flag:update")
    @OperationLog(module = "特性开关", action = "更新开关", bizType = "FEATURE_FLAG")
    @PutMapping("/{key}/enabled")
    public R<Boolean> setEnabled(@PathVariable String key,
                                 @RequestParam boolean enabled) {
        FeatureFlag flag = parseFlag(key);
        boolean effective = featureFlagService.setEnabled(flag, enabled);
        return R.ok(effective);
    }

    @Operation(summary = "设置灰度发布比例 (0-100)")
    @PrePermission("sys:feature-flag:update")
    @OperationLog(module = "特性开关", action = "更新灰度", bizType = "FEATURE_FLAG")
    @PutMapping("/{key}/rollout")
    public R<Integer> setRollout(@PathVariable String key,
                                 @RequestParam @Min(0) @Max(100) int percentage) {
        FeatureFlag flag = parseFlag(key);
        int clamped = featureFlagService.setRolloutPercentage(flag, percentage);
        return R.ok(clamped);
    }

    @Operation(summary = "强制刷新本地缓存")
    @PrePermission("sys:feature-flag:update")
    @OperationLog(module = "特性开关", action = "刷新缓存", bizType = "FEATURE_FLAG")
    @PostMapping("/refresh")
    public R<Void> refresh() {
        featureFlagService.refresh();
        return R.ok();
    }

    private static FeatureFlag parseFlag(String key) {
        try {
            return FeatureFlag.valueOf(key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知的特性开关: " + key);
        }
    }
}
