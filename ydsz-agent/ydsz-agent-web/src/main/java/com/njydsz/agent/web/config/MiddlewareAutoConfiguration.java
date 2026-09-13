package com.njydsz.agent.web.config;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.agent.domain.middleware.AgentMiddleware;
import com.njydsz.agent.domain.middleware.MiddlewareChain;
import com.njydsz.agent.server.middleware.MiddlewareChainImpl;

/**
 * 中间件链自动配置 — 将所有 {@link AgentMiddleware} 实现组装成 {@link MiddlewareChain}。
 *
 * <p>通过 Spring 自动扫描所有 AgentMiddleware Bean，按 {@link AgentMiddleware#getPriority()} 排序后串接。
 * 业务方新增中间件只需实现 AgentMiddleware 接口并标注 @Component，无需修改配置类。
 *
 * <p><b>条件装配</b>：
 * <ul>
 *   <li>存在至少一个 AgentMiddleware Bean 时创建链</li>
 *   <li>前置条件 {@code ydsz.agent.enabled=true} 与 AgentAutoConfiguration 对齐</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Configuration
@ConditionalOnProperty(
    prefix = "ydsz.agent",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Slf4j
public class MiddlewareAutoConfiguration {

  /**
   * 装配中间件链 — 将所有 AgentMiddleware 实现按优先级排序后组装。
   *
   * @param middlewares Spring 容器中所有 AgentMiddleware 实现
   * @return 中间件链（无中间件时返回空操作链）
   */
  @Bean
  @ConditionalOnMissingBean(MiddlewareChain.class)
  public MiddlewareChain middlewareChain(List<AgentMiddleware> middlewares) {
    if (middlewares == null || middlewares.isEmpty()) {
      log.info("[Middleware] 未发现 AgentMiddleware 实现，中间件链为空");
      return new MiddlewareChainImpl(List.of());
    }
    log.info("[Middleware] 装配中间件链, 数量={}", middlewares.size());
    return new MiddlewareChainImpl(middlewares);
  }
}
