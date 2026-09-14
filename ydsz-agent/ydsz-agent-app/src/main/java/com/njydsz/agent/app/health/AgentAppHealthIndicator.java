package com.njydsz.agent.app.health;

import org.springframework.boot.health.contributor.Health;

import com.njydsz.common.web.health.AbstractModuleHealthIndicator;

/**
 * Agent 模块 App 端健康检查指示器。
 *
 * <p>检测 Agent App 端各子系统的健康状态，暴露 {@code /actuator/health} 端点中 Agent 模块的 App 侧状态。
 *
 * <p>继承 common-web 统一基类 {@link AbstractModuleHealthIndicator}（P1-5 整改：由
 * {@code implements HealthIndicator} 样板改为模板方法复用）。当前为预留实现，
 * 待 App 端控制器接入后补充模块特有探针（如 LLM Provider 连通性、RAG 向量存储等）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.14 改继承 AbstractModuleHealthIndicator，消除样板（P1-5 整改）
 */
public class AgentAppHealthIndicator extends AbstractModuleHealthIndicator {

  @Override
  protected void doHealthCheck(Health.Builder builder) {
    builder.up();
    builder.withDetail("module", "agent");
    builder.withDetail("platform", "app");
    // TODO: 接入 App 端特有探针（LLM Provider / RAG / 对话记忆等）
  }
}
