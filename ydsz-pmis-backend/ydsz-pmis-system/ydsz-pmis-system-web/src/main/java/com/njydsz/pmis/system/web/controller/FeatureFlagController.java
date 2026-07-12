package com.njydsz.pmis.system.web.controller.config;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.featureflag.FeatureFlag;
import com.njydsz.pmis.common.featureflag.FeatureFlagService;
import com.njydsz.pmis.common.featureflag.FeatureFlagSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
@Tag(name = "系统-特性开关", description = "特性开关管理、灰度发布接口")
@RestController
@RequestMapping("/featureFlags")
@RequiredArgsConstructor
@Validated
public class FeatureFlagController {

    /** 特性开关服务 */
    private final FeatureFlagService featureFlagService;

    /**
     * 获取全量 flag 快照
     *
     * @return 统一响应结果，包含 flag 快照列表
     */
    @Operation(summary = "获取全量 flag 快照")
    @PrePermission("sys:featureFlag:view")
    @GetMapping("/snapshot")
    public BaseResponse<List<FeatureFlagSnapshot>> snapshot() {
        return BaseResponse.ok(featureFlagService.snapshot());
    }

    /**
     * 按分类聚合快照
     *
     * @return 统一响应结果，包含按分类分组的 flag 快照
     */
    @Operation(summary = "按分类聚合快照")
    @PrePermission("sys:featureFlag:view")
    @GetMapping("/snapshot/grouped")
    public BaseResponse<Map<String, List<FeatureFlagSnapshot>>> snapshotByCategory() {
        return BaseResponse.ok(featureFlagService.snapshotByCategory());
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
    public BaseResponse<Boolean> check(
            @Parameter(description = "flag 键") @RequestParam @NotNull String key,
            @Parameter(description = "用户ID（可选，用于灰度判断）") @RequestParam(required = false) String userId) {
        FeatureFlag flag;
        try {
            flag = FeatureFlag.valueOf(key);
        } catch (IllegalArgumentException e) {
            return BaseResponse.ok(false);
        }
        return BaseResponse.ok(featureFlagService.isEnabled(flag, userId));
    }

    /**
     * 启停指定 flag
     *
     * @param key     flag 键
     * @param enabled 是否启用
     * @return 统一响应结果，包含生效状态
     */
    @Operation(summary = "启停指定 flag")
    @PrePermission("sys:featureFlag:update")
    @OperationLog(module = "特性开关", action = "更新开关", bizType = "FEATURE_FLAG")
    @Idempotent(key = "featureFlag:setEnabled", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{key}/enabled")
    public BaseResponse<Boolean> setEnabled(
            @Parameter(description = "flag 键") @PathVariable String key,
            @Parameter(description = "是否启用") @RequestParam boolean enabled) {
        FeatureFlag flag = parseFlag(key);
        boolean effective = featureFlagService.setEnabled(flag, enabled);
        return BaseResponse.ok(effective);
    }

    /**
     * 设置灰度发布比例（0-100）
     *
     * @param key        flag 键
     * @param percentage 灰度百分比
     * @return 统一响应结果，包含实际生效的百分比
     */
    @Operation(summary = "设置灰度发布比例 (0-100)")
    @PrePermission("sys:featureFlag:update")
    @OperationLog(module = "特性开关", action = "更新灰度", bizType = "FEATURE_FLAG")
    @Idempotent(key = "featureFlag:setRollout", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{key}/rollout")
    public BaseResponse<Integer> setRollout(
            @Parameter(description = "flag 键") @PathVariable String key,
            @Parameter(description = "灰度百分比（0-100）") @RequestParam @Min(0) @Max(100) int percentage) {
        FeatureFlag flag = parseFlag(key);
        int clamped = featureFlagService.setRolloutPercentage(flag, percentage);
        return BaseResponse.ok(clamped);
    }

    /**
     * 强制刷新本地缓存
     *
     * @return 统一响应结果
     */
    @Operation(summary = "强制刷新本地缓存")
    @PrePermission("sys:featureFlag:update")
    @OperationLog(module = "特性开关", action = "刷新缓存", bizType = "FEATURE_FLAG")
    @Idempotent(key = "featureFlag:refresh", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/refresh")
    public BaseResponse<Void> refresh() {
        featureFlagService.refresh();
        return BaseResponse.ok();
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
