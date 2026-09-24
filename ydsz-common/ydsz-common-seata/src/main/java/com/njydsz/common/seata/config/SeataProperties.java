package com.njydsz.common.seata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Seata 控制开关（ydsz.seata.*）。
 *
 * <p>仅控制 <strong>common 模块内部行为开关</strong>（如是否注册 XID 透传拦截器/过滤器、
 * 是否启用全局事务切面、是否暴露 SeataHealthIndicator）；Seata 客户端自身运行
 * （TC 地址、service.vgroupMapping、grouplist 等）仍由业务 application.yml
 * 中标准的 {@code seata.*} 前缀配置，与本前缀互不干扰。
 *
 * <p>此处的 enabled 默认 false：业务方需主动在 application 配置
 * {@code ydsz.seata.enabled=true} 才激活 common 封装行为，同时仍需引入
 * Seata 客户端 jar（由根 pom 版本管控）。
 *
 * @author ydsz-team
 * @since ACC-1
 */
@Data
@ConfigurationProperties(prefix = SeataProperties.PREFIX)
public class SeataProperties {

  /** 配置前缀 */
  public static final String PREFIX = "ydsz.seata";

  /** 是否启用 Seata 分布式事务能力（默认 false，需业务方显式打开） */
  private boolean enabled = false;

  /** 是否在请求入口（XidServletFilter）自动绑定下游传入的 XID */
  private boolean xidFilterEnabled = true;

  /** 是否在 Feign 发出端（FeignXidRequestInterceptor）自动注入 XID Header */
  private boolean xidInterceptorEnabled = true;

  /** 是否在 Actuator /health 中暴露 seata 组件状态 */
  private boolean healthIndicatorEnabled = true;

  /** XID 透传使用的 HTTP 请求头名称（上下游需一致） */
  private String xidHeaderName = "TX_XID";
}
