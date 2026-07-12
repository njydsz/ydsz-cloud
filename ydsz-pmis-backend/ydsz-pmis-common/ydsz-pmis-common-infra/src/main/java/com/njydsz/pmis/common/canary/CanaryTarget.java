package com.njydsz.pmis.common.canary;

/**
 * 灰度目标 SPI 接口（P2-1 架构优化）。
 *
 * <p>各业务模块通过实现此接口注册灰度目标，由统一路由器
 * {@link CanaryRouter} 根据灰度策略决定流量分配。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Component
 * public class MessageCanaryTarget implements CanaryTarget {
 *     public String getModule() { return "message"; }
 *     public String selectVersion(CanaryContext ctx) {
 *         int hash = Math.abs(ctx.getUserId().hashCode());
 *         return hash % 100 < ctx.getCanaryPercent() ? "v2" : "v1";
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface CanaryTarget {

    /**
     * 获取模块名。
     *
     * @return 模块标识（如 message / literule / workflow / project）
     */
    String getModule();

    /**
     * 根据灰度上下文选择版本。
     *
     * @param context 灰度上下文（用户 ID / 请求 ID / 自定义属性）
     * @return 选中的版本标识（如 "v1" / "v2"）
     */
    String selectVersion(CanaryContext context);
}
