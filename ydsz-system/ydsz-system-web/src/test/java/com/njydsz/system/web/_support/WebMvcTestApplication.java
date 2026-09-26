package com.njydsz.system.web._support;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Web MVC 切片测试专用启动类（最小上下文）。
 *
 * <p>排除依赖外部中间件（Redis/MQ/Nacos/ES）以及 auth/safe/audit 模块；
 * 由 application-test.yml 通过 spring.autoconfigure.exclude 控制。
 *
 * @author ydsz-team
 * @since 26.09.26
 */
@SpringBootApplication(scanBasePackages = "com.njydsz.system")
public class WebMvcTestApplication {
  /** 仅作为 WebMvcTest 上下文入口，不进入 production 包 */
}
