package com.njydsz.agent.app.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Agent 模块 App 端健康检查指示器。
 *
 * <p>检测 Agent App 端各子系统的健康状态，暴露 {@code /actuator/health} 端点中 Agent 模块的 App 侧状态。
 *
 * <p>当前为预留实现，待 App 端控制器接入后补充模块特有探针（如 LLM Provider 连通性、RAG 向量存储等）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class AgentAppHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("module", "agent");
    details.put("platform", "app");
    // TODO: 接入 App 端特有探针（LLM Provider / RAG / 对话记忆等）
    return Health.up().withDetails(details).build();
  }
}
