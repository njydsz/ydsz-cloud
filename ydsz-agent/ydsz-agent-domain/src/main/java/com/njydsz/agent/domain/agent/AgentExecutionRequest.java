package com.njydsz.agent.domain.agent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agent 执行请求
 *
 * <p>封装一次 Agent 调用所需的全部上下文：
 *
 * <ul>
 *   <li>agentCode — Agent 编码（用于按编码路由到特定 Agent 定义）</li>
 *   <li>用户输入消息</li>
 *   <li>对话 ID（用于记忆检索）</li>
 *   <li>系统提示词（覆盖 Agent 默认）</li>
 *   <li>额外变量（Prompt 模板渲染）</li>
 *   <li>最大迭代次数（ReAct 模式）</li>
 * </ul>
 *
 * <p><b>线程安全</b>：全部字段 final 且集合经不可变封装，实例不可变、可安全跨线程传递。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class AgentExecutionRequest {

  /** Agent 编码，用于按编码路由到特定 Agent 定义；为 {@code null} 时使用默认执行器 */
  private final String agentCode;

  /** 对话 ID，用于从记忆组件回溯历史消息；为 {@code null} 时按单轮无记忆会话处理 */
  private final String conversationId;

  /** 本轮用户输入原文，不可为 {@code null}，构造时强校验 */
  private final String userInput;

  /** 系统提示词，非空时覆盖 Agent 的默认人设；为 {@code null} 时沿用 Agent 定义 */
  private final String systemPrompt;

  /** Prompt 模板渲染变量，不可变映射；未传入时为空 Map 而非 {@code null} */
  private final Map<String, Object> variables;

  /** ReAct 循环最大迭代轮次，非正数按默认 10 处理，用于兜底防止工具调用死循环与 token 失控 */
  private final int maxIterations;

  /** 本次允许调用的工具名白名单，不可变列表；为空表示不限制、使用 Agent 注册的全部工具 */
  private final List<String> enabledTools;

  /**
   * 全参构造。
   *
   * @param agentCode Agent 编码
   * @param conversationId 对话 ID（null 时按单轮无记忆会话处理）
   * @param userInput 本轮用户输入原文（不可为 null）
   * @param systemPrompt 系统提示词（null 时沿用 Agent 定义）
   * @param variables Prompt 模板渲染变量（null 时按空 Map 处理）
   * @param maxIterations ReAct 循环最大迭代轮次（非正数按默认 10 处理）
   * @param enabledTools 工具名白名单（null 时表示不限制）
   */
  public AgentExecutionRequest(
      String agentCode,
      String conversationId,
      String userInput,
      String systemPrompt,
      Map<String, Object> variables,
      int maxIterations,
      List<String> enabledTools) {
    this.agentCode = agentCode;
    this.conversationId = conversationId;
    this.userInput = Objects.requireNonNull(userInput, "userInput 不能为 null");
    this.systemPrompt = systemPrompt;
    this.variables = variables != null ? Map.copyOf(variables) : Collections.emptyMap();
    // 未指定迭代上限时默认 10 轮，作为 ReAct 循环的兜底上限，避免工具调用死循环
    this.maxIterations = maxIterations > 0 ? maxIterations : 10;
    this.enabledTools = enabledTools != null ? List.copyOf(enabledTools) : Collections.emptyList();
  }

  /**
   * 获取 Agent 编码。
   *
   * @return Agent 编码（可为 null）
   */
  public String getAgentCode() {
    return agentCode;
  }

  /**
   * 获取对话 ID。
   *
   * @return 对话 ID（单轮会话为 null）
   */
  public String getConversationId() {
    return conversationId;
  }

  /**
   * 获取用户输入原文。
   *
   * @return 本轮用户输入原文
   */
  public String getUserInput() {
    return userInput;
  }

  /**
   * 获取系统提示词。
   *
   * @return 系统提示词（未覆盖时为 null）
   */
  public String getSystemPrompt() {
    return systemPrompt;
  }

  /**
   * 获取 Prompt 模板渲染变量。
   *
   * @return 不可变变量映射（未传入时为空 Map）
   */
  public Map<String, Object> getVariables() {
    return variables;
  }

  /**
   * 获取最大迭代轮次。
   *
   * @return ReAct 循环最大迭代轮次
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * 获取工具名白名单。
   *
   * @return 不可变工具白名单（空表示不限制）
   */
  public List<String> getEnabledTools() {
    return enabledTools;
  }

  /**
   * 创建 {@link AgentExecutionRequest} 的构建器入口。
   *
   * <p>仅 {@link Builder#userInput(String)} 为必填，其余字段均有安全默认值， 未显式设置时不会因空指针中断构造。
   *
   * @return 新的 {@link Builder} 实例，方法链式调用后通过 {@link Builder#build()} 产出请求对象
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * {@link AgentExecutionRequest} 的构建器。
   *
   * <p>所有 setter 均返回自身以支持链式调用；{@link #build()} 时会以「构造兜底 + 不可变拷贝」 的方式固化集合与默认值，确保产出的请求实例不可变、可安全跨线程传递。
   */
  public static final class Builder {
    private String agentCode;
    private String conversationId;
    private String userInput;
    private String systemPrompt;
    private Map<String, Object> variables;
    private int maxIterations = 10; // Builder 默认值，与构造兜底保持一致，避免未设值时陷入无限迭代
    private List<String> enabledTools;

    /**
     * 设置 Agent 编码。
     *
     * @param agentCode Agent 编码
     * @return 当前 Builder
     */
    public Builder agentCode(String agentCode) {
      this.agentCode = agentCode;
      return this;
    }

    /**
     * 绑定对话 ID 以启用历史记忆检索。
     *
     * @param conversationId 对话 ID
     * @return 当前 Builder
     */
    public Builder conversationId(String conversationId) {
      this.conversationId = conversationId;
      return this;
    }

    /**
     * 设置用户输入原文，必填；未设置时 {@link #build()} 会抛出 {@link NullPointerException}。
     *
     * @param userInput 用户输入原文
     * @return 当前 Builder
     */
    public Builder userInput(String userInput) {
      this.userInput = userInput;
      return this;
    }

    /**
     * 设置系统提示词。
     *
     * @param systemPrompt 系统提示词
     * @return 当前 Builder
     */
    public Builder systemPrompt(String systemPrompt) {
      this.systemPrompt = systemPrompt;
      return this;
    }

    /**
     * 设置 Prompt 模板渲染变量。
     *
     * @param variables Prompt 模板渲染变量
     * @return 当前 Builder
     */
    public Builder variables(Map<String, Object> variables) {
      this.variables = variables;
      return this;
    }

    /**
     * 设置 ReAct 循环最大迭代轮次。
     *
     * @param maxIterations 最大迭代轮次
     * @return 当前 Builder
     */
    public Builder maxIterations(int maxIterations) {
      this.maxIterations = maxIterations;
      return this;
    }

    /**
     * 设置允许调用的工具名白名单。
     *
     * @param enabledTools 工具名白名单
     * @return 当前 Builder
     */
    public Builder enabledTools(List<String> enabledTools) {
      this.enabledTools = enabledTools;
      return this;
    }

    /**
     * 构建 {@link AgentExecutionRequest} 实例。
     *
     * @return 新的不可变请求实例
     */
    public AgentExecutionRequest build() {
      return new AgentExecutionRequest(
          agentCode,
          conversationId,
          userInput,
          systemPrompt,
          variables,
          maxIterations,
          enabledTools);
    }
  }
}
