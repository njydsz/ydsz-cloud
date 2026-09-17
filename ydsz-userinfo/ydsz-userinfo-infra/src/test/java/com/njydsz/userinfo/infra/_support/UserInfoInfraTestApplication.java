package com.njydsz.userinfo.infra._support;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 集成测试专用 Spring Boot 应用启动类（仅用于 IT 测试，不打包发布）。
 *
 * <p>设计要点：
 *
 * <ul>
 *   <li>scanBasePackages 仅扫描 {@code com.njydsz.userinfo} 避免加载无关模块</li>
 *   <li>ydsz-common-jdbc 自动配置通过 {@code META-INF/spring/} 加载</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.userinfo"})
public class UserInfoInfraTestApplication {
  /** 仅作为 IT 测试入口，不允许实例化到正式上下文 */
}
