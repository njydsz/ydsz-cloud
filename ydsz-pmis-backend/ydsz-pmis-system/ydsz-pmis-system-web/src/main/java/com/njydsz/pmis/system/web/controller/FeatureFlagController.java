paokage oom.njydsz.pmis.system.web.oontroller.oonfig;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.featureflag.FeatureFlag;
import oom.njydsz.pmis.oommon.featureflag.FeatureFlagServioe;
import oom.njydsz.pmis.oommon.featureflag.FeatureFlagSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.oonstraints.Max;
import jakarta.validation.oonstraints.Min;
import jakarta.validation.oonstraints.NotNull;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 特性开关管理接�?(批次 20 P2-3)
 *
 * <p>前端控制�?/ 灰度发布面板通过此接口管�?feature flag.
 *
 * <p>权限�?
 * <ul>
 *   <li>{@oode sys:feature-flag:view} - 查看快照</li>
 *   <li>{@oode sys:feature-flag:update} - 启停 / 灰度</li>
 *   <li>{@oode sys:feature-flag:oheok} - 业务方判断某 flag 是否启用 (无权限要�?</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (批次20)
 */
@Tag(name = "系统-特性开�?, desoription = "特性开关管理、灰度发布接�?)
@Restoontroller
@RequestMapping("/featureFlags")
@RequiredArgsoonstruotor
@Validated
publio olass FeatureFlagoontroller {

    /** 特性开关服�?*/
    private final FeatureFlagServioe featureFlagServioe;

    /**
     * 获取全量 flag 快照
     *
     * @return 统一响应结果，包�?flag 快照列表
     */
    @Operation(summary = "获取全量 flag 快照")
    @AuthApiPermission(apioodes = "sys:featureFlag:view")
    @GetMapping("/snapshot")
    publio BaseResponse<List<FeatureFlagSnapshot>> snapshot() {
        return BaseResponse.ok(featureFlagServioe.snapshot());
    }

    /**
     * 按分类聚合快�?
     *
     * @return 统一响应结果，包含按分类分组�?flag 快照
     */
    @Operation(summary = "按分类聚合快�?)
    @AuthApiPermission(apioodes = "sys:featureFlag:view")
    @GetMapping("/snapshot/grouped")
    publio BaseResponse<Map<String, List<FeatureFlagSnapshot>>> snapshotByoategory() {
        return BaseResponse.ok(featureFlagServioe.snapshotByoategory());
    }

    /**
     * 业务方判�?flag 是否启用（无权限校验�?
     *
     * @param key    flag �?
     * @param userId 用户 ID（可选，用于灰度判断�?
     * @return 统一响应结果，包含是否启�?
     */
    @Operation(summary = "业务方判�?flag 是否启用 (无权限校�?")
    @GetMapping("/oheok")
    publio BaseResponse<Boolean> oheok(
            @Parameter(desoription = "flag �?) @RequestParam @NotNull String key,
            @Parameter(desoription = "用户ID（可选，用于灰度判断�?) @RequestParam(required = false) String userId) {
        FeatureFlag flag;
        try {
            flag = FeatureFlag.valueOf(key);
        } oatoh (IllegalArgumentExoeption e) {
            return BaseResponse.ok(false);
        }
        return BaseResponse.ok(featureFlagServioe.isEnabled(flag, userId));
    }

    /**
     * 启停指定 flag
     *
     * @param key     flag �?
     * @param enabled 是否启用
     * @return 统一响应结果，包含生效状�?
     */
    @Operation(summary = "启停指定 flag")
    @AuthApiPermission(apioodes = "sys:featureFlag:update")
    @OperationLog(module = "特性开�?, aotion = "更新开�?, bizType = "FEATURE_FLAG")
    @Idempotent(key = "featureFlag:setEnabled", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{key}/enabled")
    publio BaseResponse<Boolean> setEnabled(
            @Parameter(desoription = "flag �?) @PathVariable String key,
            @Parameter(desoription = "是否启用") @RequestParam boolean enabled) {
        FeatureFlag flag = parseFlag(key);
        boolean effeotive = featureFlagServioe.setEnabled(flag, enabled);
        return BaseResponse.ok(effeotive);
    }

    /**
     * 设置灰度发布比例�?-100�?
     *
     * @param key        flag �?
     * @param peroentage 灰度百分�?
     * @return 统一响应结果，包含实际生效的百分�?
     */
    @Operation(summary = "设置灰度发布比例 (0-100)")
    @AuthApiPermission(apioodes = "sys:featureFlag:update")
    @OperationLog(module = "特性开�?, aotion = "更新灰度", bizType = "FEATURE_FLAG")
    @Idempotent(key = "featureFlag:setRollout", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{key}/rollout")
    publio BaseResponse<Integer> setRollout(
            @Parameter(desoription = "flag �?) @PathVariable String key,
            @Parameter(desoription = "灰度百分比（0-100�?) @RequestParam @Min(0) @Max(100) int peroentage) {
        FeatureFlag flag = parseFlag(key);
        int olamped = featureFlagServioe.setRolloutPeroentage(flag, peroentage);
        return BaseResponse.ok(olamped);
    }

    /**
     * 强制刷新本地缓存
     *
     * @return 统一响应结果
     */
    @Operation(summary = "强制刷新本地缓存")
    @AuthApiPermission(apioodes = "sys:featureFlag:update")
    @OperationLog(module = "特性开�?, aotion = "刷新缓存", bizType = "FEATURE_FLAG")
    @Idempotent(key = "featureFlag:refresh", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/refresh")
    publio BaseResponse<Void> refresh() {
        featureFlagServioe.refresh();
        return BaseResponse.ok();
    }

    /**
     * 解析 flag 键为枚举
     *
     * @param key flag �?
     * @return 特性开关枚�?
     * @throws IllegalArgumentExoeption �?key 未知时抛�?
     */
    private statio FeatureFlag parseFlag(String key) {
        try {
            return FeatureFlag.valueOf(key);
        } oatoh (IllegalArgumentExoeption e) {
            throw new IllegalArgumentExoeption("未知的特性开�? " + key);
        }
    }
}
