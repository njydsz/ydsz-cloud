package com.njydsz.agent.domain.config.properties;

import java.time.Duration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A2A（Agent-to-Agent）协议配置。
 *
 * <p>控制 ydsz-agent 作为 A2A Client 连接外部 Agent 的能力。
 *
 * <p>YAML 前缀：{@code ydsz.agent.a2a}
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class A2aProperties {

  /** A2A 调用超时默认秒数 */
  private static final long DEFAULT_TIMEOUT_SECONDS = 60L;

  /** A2A 轮询间隔默认秒数 */
  private static final long DEFAULT_POLL_INTERVAL_SECONDS = 2L;

  /** 是否启用 A2A 协议 Client */
  private boolean isEnabled = false;

  /** A2A 调用超时时间 */
  private Duration timeout = Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS);

  /** 默认 A2A Server URL（可被任务级 URL 覆盖） */
  private String defaultServerUrl;

  /** 默认认证 Token（Bearer） */
  private String defaultAuthToken;

  /** 是否验证服务端 TLS 证书 */
  private boolean verifyTls = true;

  /** 最大轮询重试次数（轮询任务状态时） */
  private int maxPollRetries = 10;

  /** 轮询间隔 */
  private Duration pollInterval = Duration.ofSeconds(DEFAULT_POLL_INTERVAL_SECONDS);
}
