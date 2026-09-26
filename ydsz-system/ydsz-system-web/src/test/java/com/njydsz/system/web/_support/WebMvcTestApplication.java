package com.njydsz.system.web._support;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;

/**
 * Web MVC 切片测试专用启动类（排除依赖中间件的自动配置，仅加载 MVC 层 + 内存 Mock）。
 *
 * <p><b>设计目标：</b>为 {@code @AutoConfigureMockMvc} 测试提供轻量级 Spring 上下文，避免 Redis / MQ / Nacos / ES 等外部依赖导致测试无法启动。
 *
 * <p><b>排除的自动配置：</b>
 * <ul>
 *   <li>{@link RedisAutoConfiguration} — Redis 连接（{@code IdempotentAspect} / {@code RateLimitAspect} 等依赖）</li>
 *   <li>{@link RabbitAutoConfiguration} — RabbitMQ 连接</li>
 *   <li>{@link ElasticsearchRestClientAutoConfiguration} — ES 客户端</li>
 *   <li>Auth / Safe / Nacos Discovery Nacos Config — 通过扫描排除规则隐式排除</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * &#64;SpringBootTest(classes = WebMvcTestApplication.class)
 * &#64;AutoConfigureMockMvc
 * class MyControllerTest {
 *     &#64;MockBean private MyService myService;
 *     &#64;Autowired private MockMvc mockMvc;
 * }
 * }</pre>
 *
 * <p><b>注意：</b>本启动类承载测试期间临时测试上下文，不进入 production 包（与 {@code SystemApplication} 隔离）。
 *
 * @author ydsz-team
 * @since 26.09.26
 */
@SpringBootApplication(
    scanBasePackages = "com.njydsz.system",
    exclude = {
      RedisAutoConfiguration.class,
      RedisRepositoriesAutoConfiguration.class,
      RabbitAutoConfiguration.class,
      ElasticsearchRestClientAutoConfiguration.class,
    })
public class WebMvcTestApplication {
  /** 仅作为 WebMvcTest 上下文入口，不对外暴露 */
}
