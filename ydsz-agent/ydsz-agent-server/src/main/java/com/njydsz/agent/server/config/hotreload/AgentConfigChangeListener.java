package com.njydsz.agent.server.config.hotreload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.config.hotreload.ConfigChangeListener;

/**
 * Agent 模块配置变更监听器（P1-B2：接入统一 ConfigChangeBridge）。
 *
 * <p>监听 agent 模块相关的配置中心变更（{@code ydsz.agent.*}），将 Spring Cloud 配置变更事件桥接到运行时状态感知。
 *
 * <p><b>适用范围：</b>通过 Nacos / Apollo 动态调整 agent 模块行为参数（如 LLM provider 配置、护栏阈值、
 * Prompt 模板参数等），无需重启服务即可感知变更。
 *
 * <p><b>设计说明：</b>本监听器实现 {@link ConfigChangeListener} 接口，由 {@code ydsz-common-config} 的 {@code
 * ConfigChangeBridge} 自动分发配置变更事件。配置属性的热加载由 Spring Cloud 原生 {@code @ConfigurationProperties}
 * 自动处理，本监听器仅负责需要<b>主动响应</b>的变更场景（如运行时日志记录、告警通知等）。
 *
 * <h3>当前支持的变更响应</h3>
 *
 * <ul>
 *   <li>{@code ydsz.agent.enabled}：模块总开关变更 → 日志感知（运行时无法热切换）
 *   <li>{@code ydsz.agent.llm.*}：LLM 配置变更 → 日志感知
 *   <li>{@code ydsz.agent.guardrail.*}：护栏配置变更 → 日志感知
 *   <li>{@code ydsz.agent.cache.*}：缓存配置变更 → 日志感知
 *   <li>{@code ydsz.agent.quota.*}：配额配置变更 → 日志感知
 * </ul>
 *
 * <p><b>注意：</b>Agent 核心运行时（LLM 客户端连接池、会话管理器）在启动时初始化，大部分配置变更后需重启生效。
 * 本监听器主要提供变更审计和运维感知能力。后续可增强为触发 LLM 客户端重建等深度热更新逻辑。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
public class AgentConfigChangeListener implements ConfigChangeListener {

  /** agent 模块配置属性前缀 */
  private static final String AGENT_CONFIG_PREFIX = "ydsz.agent.";

  /**
   * 接收配置变更回调。
   *
   * <p>仅处理 {@code ydsz.agent.} 前缀的配置项，其他配置变更忽略。
   *
   * @param key 变更的配置键（如 ydsz.agent.llm.default-provider）
   * @param oldValue 变更前的值
   * @param newValue 变更后的值
   */
  @Override
  public void onChange(String key, String oldValue, String newValue) {
    if (key == null || !key.startsWith(AGENT_CONFIG_PREFIX)) {
      return;
    }

    log.info("[Agent] 配置变更通知: key={}, {} -> {}", key, oldValue, newValue);

    // 模块总开关变更 → 提示需重启
    if ("ydsz.agent.enabled".equals(key)) {
      log.warn("[Agent] Agent 模块启用状态变更: {} -> {}，需重启生效", oldValue, newValue);
    }
  }

  /**
   * 获取监听器执行顺序。
   *
   * <p>agent 配置监听器优先级为 20（普通优先级）。
   *
   * @return 20
   */
  @Override
  public int getOrder() {
    return 20;
  }
}
