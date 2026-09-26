package com.njydsz.system.web._support;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Web MVC 切片测试专用启动类（最小上下文，由 application-test.yml 控制中间件依赖开关）。
 *
 * 设计目标：为 @AutoConfigureMockMvc 测试提供轻量级 Spring 上下文。
 * 通过 application-test.yml 排除依赖外部中间件（Redis/MQ/Nacos/ES）以及 auth/safe/audit 模块。
 *
 * 使用方式：
 *   @SpringBootTest(classes = WebMvcTestApplication.class)
 *   @AutoConfigureMockMvc
 *   @ActiveProfiles("test")
 *   class MyControllerTest {
 *       @MockBean private MyService myService;
 *       @Autowired private MockMvc mockMvc;
 *   }
 *
 * @author ydsz-team
 * @since 26.09.26
 */
@SpringBootApplication(scanBasePackages = "com.njydsz.system")
public class WebMvcTestApplication {
  /** 仅作为 WebMvcTest 上下文入口，不进入 production 包 */
}
