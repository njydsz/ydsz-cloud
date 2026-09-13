package com.njydsz.agent.domain.workspace;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 工作区值对象 — 对标 AgentScope Workspace 的结构化目录概念。
 *
 * <p>将所有 Agent 运行时状态表达为结构化的"人格/记忆/技能/计划"文件集合，
 * 支持可插拔存储后端（本地磁盘 / Redis / 数据库）。
 *
 * <p><b>对标 AgentScope</b>：AgentScope 的工作区包含 AGENTS.md（人格）、MEMORY.md（记忆）、
 * subagents/（子Agent声明）、skills/（技能）。本工作区对齐此结构，提供标准化的 Agent 运行环境。
 *
 * <p><b>线程安全</b>：值对象不可变，读写通过 {@link AgentWorkspaceStore} 完成。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
public final class AgentWorkspace implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 工作区 ID（对应 Agent 编码） */
  private final String workspaceId;

  /** 人格文档内容（AGENTS.md 等价物） */
  private final String personality;

  /** 长期记忆内容（MEMORY.md 等价物） */
  private final String memory;

  /** 当前执行计划 JSON */
  private final String executionPlan;

  /** 技能装配 JSON（技能名 → 配置） */
  private final String skills;

  /** 工作区版本号（每次更新递增） */
  private final long version;

  /** 最后更新时间 */
  private final LocalDateTime updatedAt;

  public AgentWorkspace(
      String workspaceId,
      String personality,
      String memory,
      String executionPlan,
      String skills,
      long version,
      LocalDateTime updatedAt) {
    this.workspaceId = workspaceId;
    this.personality = personality != null ? personality : "";
    this.memory = memory != null ? memory : "";
    this.executionPlan = executionPlan != null ? executionPlan : "";
    this.skills = skills != null ? skills : "";
    this.version = version;
    this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
  }

  public String getWorkspaceId() {
    return workspaceId;
  }

  public String getPersonality() {
    return personality;
  }

  public String getMemory() {
    return memory;
  }

  public String getExecutionPlan() {
    return executionPlan;
  }

  public String getSkills() {
    return skills;
  }

  public long getVersion() {
    return version;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  /**
   * 创建只更新记忆的新工作区副本。
   *
   * @param newMemory 新记忆内容
   * @return 携带新记忆和递增版本的新工作区实例
   */
  public AgentWorkspace withMemory(String newMemory) {
    return new AgentWorkspace(
        workspaceId, personality, newMemory, executionPlan, skills,
        version + 1, LocalDateTime.now());
  }

  /**
   * 创建只更新人格的新工作区副本。
   *
   * @param newPersonality 新人格内容
   * @return 携带新人格和递增版本的新工作区实例
   */
  public AgentWorkspace withPersonality(String newPersonality) {
    return new AgentWorkspace(
        workspaceId, newPersonality, memory, executionPlan, skills,
        version + 1, LocalDateTime.now());
  }

  /**
   * 创建 Builder 入口。
   *
   * @return 新的 Builder 实例
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * AgentWorkspace 构建器。
   */
  public static final class Builder {
    private String workspaceId;
    private String personality = "";
    private String memory = "";
    private String executionPlan = "";
    private String skills = "";
    private long version = 1L;

    public Builder workspaceId(String workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public Builder personality(String personality) {
      this.personality = personality;
      return this;
    }

    public Builder memory(String memory) {
      this.memory = memory;
      return this;
    }

    public Builder executionPlan(String executionPlan) {
      this.executionPlan = executionPlan;
      return this;
    }

    public Builder skills(String skills) {
      this.skills = skills;
      return this;
    }

    public Builder version(long version) {
      this.version = version;
      return this;
    }

    public AgentWorkspace build() {
      return new AgentWorkspace(
          workspaceId, personality, memory, executionPlan, skills,
          version, LocalDateTime.now());
    }
  }
}
