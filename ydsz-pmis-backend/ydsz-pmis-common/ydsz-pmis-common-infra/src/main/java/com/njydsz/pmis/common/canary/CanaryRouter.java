package com.njydsz.pmis.common.canary;

import java.util.List;

/**
 * 统一灰度路由器接口（P2-1 架构优化）。
 *
 * <p>提供全局统一的灰度/Canary 发布路由能力，替代 message、literule、workflow、project
 * 各模块各自实现的 CanaryService。
 *
 * <p>各模块通过实现 {@link CanaryTarget} SPI 注册灰度目标，
 * 由统一路由器根据灰度策略决定流量分配。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 注册灰度目标
 * @Component
 * public class MessageCanaryTarget implements CanaryTarget {
 *     public String getModule() { return "message"; }
 *     public String selectVersion(CanaryContext ctx) {
 *         // 根据用户 ID 哈希选择版本
 *         int hash = Math.abs(ctx.getUserId().hashCode());
 *         return hash % 100 < ctx.getCanaryPercent() ? "v2" : "v1";
 *     }
 * }
 *
 * // 使用路由器
 * String version = canaryRouter.route("message", CanaryContext.of(userId));
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface CanaryRouter {

    /**
     * 路由到指定模块的灰度版本。
     *
     * @param module  模块名（message / literule / workflow / project）
     * @param context 灰度上下文（用户 ID / 请求 ID / 自定义属性）
     * @return 选中的版本标识（如 "v1" / "v2"），无灰度配置时返回 "default"
     */
    String route(String module, CanaryContext context);

    /**
     * 查询指定模块的灰度配置。
     *
     * @param module 模块名
     * @return 灰度配置列表（可能包含多个灰度规则）
     */
    List<CanaryConfig> getConfigs(String module);

    /**
     * 更新灰度配置。
     *
     * @param module  模块名
     * @param config  灰度配置
     */
    void updateConfig(String module, CanaryConfig config);
}
