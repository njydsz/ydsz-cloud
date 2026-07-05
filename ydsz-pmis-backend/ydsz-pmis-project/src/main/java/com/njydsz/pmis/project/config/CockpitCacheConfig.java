package com.njydsz.pmis.project.config;

/**
 * 驾驶舱缓存配置（P1-10 已废弃，保留类仅用于文档）
 *
 * <p><b>历史</b>：原为 CockpitReportService 单独定义 RedisCacheManager，TTL 5 分钟。
 *
 * <p><b>P1-10 变更</b>：统一由 {@link com.njydsz.pmis.common.config.PmisCacheConfig} 接管，
 * cockpit 缓存名称 {@code cockpit:report} 的 TTL 已在 PmisCacheConfig 中配置为 5 分钟。
 *
 * <p><b>清理说明</b>：本类不再注册任何 Bean，仅作为历史文档保留。
 * 如需移除，可直接删除本文件 + 引用处即可。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @deprecated P1-10 起由 {@link com.njydsz.pmis.common.config.PmisCacheConfig} 统一接管
 */
@Deprecated(forRemoval = true)
public class CockpitCacheConfig {
    // P1-10: 已迁移至 com.njydsz.pmis.common.config.PmisCacheConfig
    // cockpit:report 缓存 TTL 5 分钟已在 PmisCacheConfig 中配置
}
