package com.njydsz.pmis.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DruidMonitorEnabler 单元测试
 *
 * <p>验证 EnvironmentPostProcessor 注入的 Druid 监控默认配置正确，
 * 且业务服务可覆盖默认值。
 *
 * <p>说明：通过覆写 {@link DruidMonitorEnabler#isDruidPresent()} 绕过 classpath 检测，
 * 直接验证 {@link DruidMonitorEnabler#buildDefaults()} 的输出。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DruidMonitorEnabler 慢 SQL 监控配置测试")
class DruidMonitorEnablerTest {

    /** 测试用桩：强制返回 Druid 已存在，跳过真实 classpath 检测 */
    private final DruidMonitorEnabler enabler = new DruidMonitorEnabler() {
        @Override
        protected boolean isDruidPresent() {
            return true;
        }
    };

    @Test
    @DisplayName("buildDefaults 应包含 stat-filter 默认配置")
    void buildDefaults_shouldContainStatFilter() {
        Map<String, Object> defaults = enabler.buildDefaults();

        assertThat(defaults.get("spring.datasource.druid.stat-filter.enabled")).isEqualTo(true);
        assertThat(defaults.get("spring.datasource.druid.stat-filter.slow-sql-millis"))
                .isEqualTo(DruidMonitorEnabler.SLOW_SQL_MILLIS_DEFAULT);
        assertThat(defaults.get("spring.datasource.druid.stat-filter.log-slow-sql")).isEqualTo(true);
        assertThat(defaults.get("spring.datasource.druid.stat-filter.merge-sql")).isEqualTo(true);
    }

    @Test
    @DisplayName("buildDefaults 应包含 web-stat-filter 默认配置")
    void buildDefaults_shouldContainWebStatFilter() {
        Map<String, Object> defaults = enabler.buildDefaults();

        assertThat(defaults.get("spring.datasource.druid.web-stat-filter.enabled")).isEqualTo(true);
        assertThat(defaults.get("spring.datasource.druid.web-stat-filter.url-patterns")).isEqualTo("/*");
        assertThat((String) defaults.get("spring.datasource.druid.web-stat-filter.exclusions"))
                .contains("*.js", "*.css", "/druid/*");
    }

    @Test
    @DisplayName("buildDefaults 应包含 stat-view-servlet 默认配置")
    void buildDefaults_shouldContainStatViewServlet() {
        Map<String, Object> defaults = enabler.buildDefaults();

        assertThat(defaults.get("spring.datasource.druid.stat-view-servlet.enabled")).isEqualTo(true);
        assertThat(defaults.get("spring.datasource.druid.stat-view-servlet.url-pattern")).isEqualTo("/druid/*");
        assertThat(defaults.get("spring.datasource.druid.stat-view-servlet.reset-enable")).isEqualTo(false);
    }

    @Test
    @DisplayName("buildDefaults 应包含 aop-patterns 拦截 Service 层")
    void buildDefaults_shouldContainAopPatterns() {
        Map<String, Object> defaults = enabler.buildDefaults();

        String patterns = (String) defaults.get("spring.datasource.druid.aop-patterns");
        assertThat(patterns).contains("com.njydsz.pmis.*.service.impl.*");
        assertThat(patterns).contains("com.njydsz.pmis.*.controller.*");
    }

    @Test
    @DisplayName("postProcessEnvironment 应将默认配置注入到 Environment（Druid 存在时）")
    void postProcessEnvironment_shouldInjectDefaultsWhenDruidPresent() {
        ConfigurableEnvironment env = new MockEnvironment();
        enabler.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.datasource.druid.stat-filter.enabled")).isEqualTo("true");
        assertThat(env.getProperty("spring.datasource.druid.stat-filter.slow-sql-millis"))
                .isEqualTo(DruidMonitorEnabler.SLOW_SQL_MILLIS_DEFAULT);
        assertThat(env.getProperty("spring.datasource.druid.web-stat-filter.enabled")).isEqualTo("true");
        assertThat(env.getProperty("spring.datasource.druid.stat-view-servlet.enabled")).isEqualTo("true");
    }

    @Test
    @DisplayName("postProcessEnvironment Druid 不存在时应跳过注入")
    void postProcessEnvironment_shouldSkipWhenDruidAbsent() {
        DruidMonitorEnabler absentEnabler = new DruidMonitorEnabler() {
            @Override
            protected boolean isDruidPresent() {
                return false;
            }
        };
        ConfigurableEnvironment env = new MockEnvironment();
        absentEnabler.postProcessEnvironment(env, new SpringApplication());

        // 不应注入任何 Druid 配置
        assertThat(env.getProperty("spring.datasource.druid.stat-filter.enabled")).isNull();
    }

    @Test
    @DisplayName("SLOW_SQL_MILLIS_DEFAULT 常量应为 3000")
    void slowSqlMillisConstant() {
        assertThat(DruidMonitorEnabler.SLOW_SQL_MILLIS_DEFAULT).isEqualTo("3000");
    }

    @Test
    @DisplayName("DRUID_DATASOURCE_CLASS 常量应为 DruidDataSource 全限定类名")
    void druidDatasourceClassConstant() {
        assertThat(DruidMonitorEnabler.DRUID_DATASOURCE_CLASS)
                .isEqualTo("com.alibaba.druid.pool.DruidDataSource");
    }
}
