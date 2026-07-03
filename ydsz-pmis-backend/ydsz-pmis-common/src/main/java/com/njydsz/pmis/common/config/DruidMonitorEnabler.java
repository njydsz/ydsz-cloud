package com.njydsz.pmis.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Druid 慢 SQL 监控自动启用（P2-15）
 *
 * <p>通过 {@link EnvironmentPostProcessor} 在所有引入 ydsz-pmis-common 的微服务启动早期
 * 注入 Druid 监控默认配置，无需各服务逐个修改 application.yml。
 *
 * <p>启用项：
 * <ul>
 *   <li>{@code stat-filter} — SQL 执行统计，慢 SQL 阈值 1 秒（与 PG log_min_duration_statement 对齐），自动记录到日志</li>
 *   <li>{@code web-stat-filter} — URL 访问统计（请求次数/执行时间）</li>
 *   <li>{@code stat-view-servlet} — 监控页面 /druid/* <b>默认关闭</b>，需显式启用并强制配置账号密码 + IP 白名单</li>
 *   <li>{@code aop-patterns} — AOP 拦截 Service 层方法，统计方法级 SQL 执行</li>
 * </ul>
 *
 * <p>覆盖策略：业务服务可在自身 application.yml 中显式设置同名属性覆盖默认值，
 * 例如启用监控页面：{@code spring.datasource.druid.stat-view-servlet.enabled=true}（必须同时配置 login-username/password）。
 *
 * <p>注册：{@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}
 *
 * <p>注：{@code EnvironmentPostProcessor} 在 Spring Boot 4.0+ 标记为 for-removal，
 * 但当前仍为唯一稳定的「在 SpringApplication 启动前注入默认配置」的官方扩展点。
 * 待 Spring Boot 提供正式替代 API 后再迁移。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SuppressWarnings("removal")
public class DruidMonitorEnabler implements EnvironmentPostProcessor {

    /** 默认慢 SQL 阈值（毫秒），与 PostgreSQL log_min_duration_statement=500ms 对齐（取 1000ms 平衡噪音与可视性） */
    public static final String SLOW_SQL_MILLIS_DEFAULT = "1000";

    /** Druid DataSource 全限定类名，用于检测 classpath 是否存在 Druid */
    public static final String DRUID_DATASOURCE_CLASS = "com.alibaba.druid.pool.DruidDataSource";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 仅在 classpath 上存在 Druid 时启用（避免 common 模块独立运行时报错）
        if (!isDruidPresent()) {
            return;
        }
        environment.getPropertySources()
                .addLast(new MapPropertySource("pmis-druid-monitor-defaults", buildDefaults()));
    }

    /**
     * 检测 classpath 是否存在 Druid DataSource
     *
     * <p>抽取为独立方法便于单元测试 mock。
     *
     * @return Druid 存在返回 true
     */
    protected boolean isDruidPresent() {
        try {
            Class.forName(DRUID_DATASOURCE_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 构建 Druid 监控默认配置
     *
     * <p>抽取为独立方法便于单元测试直接验证，无需 Druid 在 classpath。
     *
     * @return 配置属性 Map
     */
    protected Map<String, Object> buildDefaults() {
        Map<String, Object> defaults = new HashMap<>();
        // SQL 执行统计
        defaults.put("spring.datasource.druid.stat-filter.enabled", true);
        defaults.put("spring.datasource.druid.stat-filter.slow-sql-millis", SLOW_SQL_MILLIS_DEFAULT);
        defaults.put("spring.datasource.druid.stat-filter.log-slow-sql", true);
        defaults.put("spring.datasource.druid.stat-filter.merge-sql", true);

        // Web 请求统计
        defaults.put("spring.datasource.druid.web-stat-filter.enabled", true);
        defaults.put("spring.datasource.druid.web-stat-filter.url-patterns", "/*");
        defaults.put("spring.datasource.druid.web-stat-filter.exclusions",
                "*.js,*.gif,*.jpg,*.png,*.css,*.ico,/druid/*");

        // 监控页面（/druid/*）—— H4.1 修复：默认关闭，避免生产裸奔
        // 启用时必须显式配置 login-username/password 与 IP 白名单（allow/deny）
        defaults.put("spring.datasource.druid.stat-view-servlet.enabled", false);
        defaults.put("spring.datasource.druid.stat-view-servlet.url-pattern", "/druid/*");
        defaults.put("spring.datasource.druid.stat-view-servlet.reset-enable", false);

        // AOP 拦截 Service 层，统计方法级 SQL 执行情况
        defaults.put("spring.datasource.druid.aop-patterns",
                "com.njydsz.pmis.*.service.impl.*,com.njydsz.pmis.*.controller.*");
        return defaults;
    }
}
