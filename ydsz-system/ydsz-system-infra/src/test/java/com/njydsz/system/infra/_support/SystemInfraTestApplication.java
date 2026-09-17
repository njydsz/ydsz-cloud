package com.njydsz.system.infra._support;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 集成测试专用 Spring Boot 应用启动类（仅用于 IT 测试，不打包发布）。
 *
 * <p>显式排除 {@link DataSourceAutoConfiguration} 以避免无 datasource 配置时报错，
 * 由 {@link SystemTestInitializer} 通过 {@code @DynamicPropertySource} 注入 Testcontainers 的 PG 连接信息。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>componentScan 仅到 {@code com.njydsz.system} 避免加载无关模块</li>
 *   <li>ydsz-common-jdbc 自动配置（MybatisPlus + DynamicDataSource）通过 {@code ENTITY_IMPORTS}加载</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@SpringBootApplication(
    scanBasePackages = {"com.njydsz.system"},
    exclude = {DataSourceAutoConfiguration.class})
public class SystemInfraTestApplication {
  /** 仅作为 IT 测试入口，不允许实例化到正式上下文 */
}
