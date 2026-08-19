package com.njydsz.agent.domain.agent;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Agent 执行上下文值对象
 *
 * <p>贯穿 Agent 执行全链路的上下文容器，携带租户、用户、请求追踪等关键信息。 从 Controller 层创建，经由 Facade → Executor → LLM Client 各层透传。
 *
 * <p><b>线程安全</b>：全字段 final 不可变值对象，可安全跨线程共享。由 {@link #withExecutionId(String)} 生成变体（而非修改原对象）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class AgentExecutionContext implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 执行唯一 ID（贯穿全链路，关联日志、链路、事件） */
  private final String executionId;

  /** 租户 ID */
  private final String tenantId;

  /** 用户 ID（发起请求的用户） */
  private final String userId;

  /** 对话 ID（用于记忆检索，可为 null） */
  private final String conversationId;

  /** 请求来源（WEB / APP / API / SCHEDULE） */
  private final String source;

  /** 客户端 IP（用于审计） */
  private final String clientIp;

  public AgentExecutionContext(
      String executionId,
      String tenantId,
      String userId,
      String conversationId,
      String source,
      String clientIp) {
    this.executionId = executionId != null ? executionId : UUID.randomUUID().toString().replace("-", "");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId 不能为 null");
    this.userId = Objects.requireNonNull(userId, "userId 不能为 null");
    this.conversationId = conversationId;
    this.source = source != null ? source : "API";
    this.clientIp = clientIp;
  }

  /**
   * 创建执行上下文的构建器入口。
   *
   * @return 新的构建器实例
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * 基于当前上下文生成携带新 executionId 的副本。
   *
   * @param newExecutionId 新的执行 ID
   * @return 新的上下文实例
   */
  public AgentExecutionContext withExecutionId(String newExecutionId) {
    return new AgentExecutionContext(
        newExecutionId, tenantId, userId, conversationId, source, clientIp);
  }

  public String getExecutionId() {
    return executionId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getUserId() {
    return userId;
  }

  public String getConversationId() {
    return conversationId;
  }

  public String getSource() {
    return source;
  }

  public String getClientIp() {
    return clientIp;
  }

  @Override
  public String toString() {
    return "AgentExecutionContext{executionId='"
        + executionId
        + "', tenantId='"
        + tenantId
        + "', userId='"
        + userId
        + "', source='"
        + source
        + "'}";
  }

  /** AgentExecutionContext 构建器 */
  public static final class Builder {
    private String executionId;
    private String tenantId;
    private String userId;
    private String conversationId;
    private String source = "API";
    private String clientIp;

    public Builder executionId(String executionId) {
      this.executionId = executionId;
      return this;
    }

    public Builder tenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder userId(String userId) {
      this.userId = userId;
      return this;
    }

    public Builder conversationId(String conversationId) {
      this.conversationId = conversationId;
      return this;
    }

    public Builder source(String source) {
      this.source = source;
      return this;
    }

    public Builder clientIp(String clientIp) {
      this.clientIp = clientIp;
      return this;
    }

    public AgentExecutionContext build() {
      return new AgentExecutionContext(executionId, tenantId, userId, conversationId, source, clientIp);
    }
  }
}
