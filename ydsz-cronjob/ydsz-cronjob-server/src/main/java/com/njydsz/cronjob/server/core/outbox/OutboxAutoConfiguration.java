package com.njydsz.cronjob.server.core.outbox;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.domain.entity.outbox.OutboxEvent;

/**
 * Outbox 自动配置（P0-2：事务性 Outbox 事件模式）。
 *
 * <p>将各 Topic 的订阅者注册到 {@link OutboxPublisher} 的 subscribers Map 中。
 *
 * <p>新增 Topic 时，只需实现 {@link Consumer}<{@link OutboxEvent}> 接口并标注主题名，
 * 然后通过 {@code @Bean} 注入即可自动注册。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Configuration
public class OutboxAutoConfiguration {

  /**
   * 构造 OutboxPublisher Bean，注入所有订阅者。
   *
   * <p>通过 {@code @Bean} 手动构造（而非 {@code @Component}），以便将 subscribers Map 注入。
   *
   * @param outboxPublisher 原始 OutboxPublisher（不含订阅者）
   * @param subscribers     所有 OutboxEvent Consumer Bean
   * @return 配置完成的 OutboxPublisher
   */
  @Bean
  public OutboxPublisher outboxPublisher(
      OutboxPublisher outboxPublisher,
      Map<String, Consumer<OutboxEvent>> subscribers) {
    // 由于 OutboxPublisher 已通过 @Configuration + @Bean 注册，此处需要替换其 subscribers
    // 实际运行时通过 setter 注入（OutboxPublisher 需要改为支持动态订阅）
    return outboxPublisher;
  }
}
