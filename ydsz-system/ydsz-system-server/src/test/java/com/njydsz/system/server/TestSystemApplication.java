package com.njydsz.system.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 集成测试专用 Spring Boot 应用入口。
 *
 * <p>提供最小化的 Spring 上下文，用于 Testcontainers 集成测试场景。
 * 扫描 system 模块（server + infra）和 common 模块组件，
 * 排除 {@code @AutoConfiguration} 类避免重复加载。
 *
 * <p><b>注意：</b>本类仅用于测试，不部署生产环境。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@SpringBootApplication
@MapperScan("com.njydsz.system.infra.mapper")
@ComponentScan(
    basePackages = {"com.njydsz.system", "com.njydsz.common"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        classes = org.springframework.boot.autoconfigure.AutoConfiguration.class))
public class TestSystemApplication {

  /**
   * 启动 Spring 应用上下文（测试入口）。
   *
   * @param args 命令行参数
   */
  public static void main(String[] args) {
    SpringApplication.run(TestSystemApplication.class, args);
  }
}
