package com.njydsz.common.web.annotation;

import java.lang.annotation.*;

/**
 * API 版本注解
 *
 * <p>用于标注 REST API 接口的版本信息，支持版本生命周期管理：
 * 引入版本 → 废弃通知 → 下线迁移。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @ApiVersion(since = "v1", deprecatedAt = "v2", migrateTo = "/api/v2/projects")
 * @GetMapping("/api/v1/projects")
 * public List<ProjectVO> listProjectsV1() { ... }
 *
 * @ApiVersion(since = "v2")
 * @GetMapping("/api/v2/projects")
 * public List<ProjectVO> listProjectsV2() { ... }
 * }</pre>
 *
 * <h3>版本响应头注入</h3>
 * <p>网关层自动解析此注解并向响应注入头部：
 * <ul>
 *   <li>{@code X-API-Version}: 当前版本号</li>
 *   <li>{@code X-API-Deprecated}: 是否已废弃（true/false）</li>
 *   <li>{@code Sunset}: 下线日期（RFC 7231 格式）</li>
 *   <li>{@code Sunset-Migrate-To}: 迁移目标路径</li>
 * </ul>
 *
 * <h3>版本策略</h3>
 * <ul>
 *   <li>向后兼容至少 12 个月（对齐 Google API Design Guide）</li>
 *   <li>废弃时需提前 30 天通知（通过响应头 + 文档告知）</li>
 *   <li>v1/v2 多版本可共存，网关按路径路由</li>
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

    /**
     * 引入此版本的 API 版本号
     *
     * @return 版本号，如 "v1"、"v2"
     */
    String since();

    /**
     * 废弃此版本的版本号（即从此版本开始标记废弃）
     *
     * <p>为 null 表示未废弃。
     *
     * @return 废弃起始版本，如 "v2" 表示 v2 起 v1 被标记为 deprecated
     */
    String deprecatedAt() default "";

    /**
     * 此 API 完全下线的日期（ISO 8601 格式）
     *
     * <p>用于 Sunset 响应头，客户端应在此日期前完成迁移。
     *
     * @return 下线日期，如 "2027-08-01"
     */
    String sunsetAt() default "";

    /**
     * 迁移目标 API 路径
     *
     * <p>指向替代版本的路径，客户端应迁移到此 API。
     *
     * @return 新 API 路径，如 "/api/v2/projects"
     */
    String migrateTo() default "";
}
