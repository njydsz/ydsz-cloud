package com.njydsz.agent.domain.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.njydsz.agent.domain.model.ChatMessage;

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
   * 上层（Harness）预置的上下文历史消息，不可变列表。
   *
   * <p>非空时执行器以其替代「从 {@code ConversationMemory} 按滑动窗口加载历史」的默认行为，
   * 使上下文预算裁剪 / 压缩策略在执行前即生效（见 {@code AgentHarness}）。
   * 为空表示执行器自行加载历史，保持既有行为不变。
   */
  private final List<ChatMessage> contextMessages;

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
   * @param contextMessages 上层预置的上下文历史消息（null/空表示由执行器自行加载）
   */
  public AgentExecutionRequest(
      String agentCode,
      String conversationId,
      String userInput,
      String systemPrompt,
      Map<String, Object> variables,
      int maxIterations,
      List<String> enabledTools,
      List<ChatMessage> contextMessages) {
    this.agentCode = agentCode;
    this.conversationId = conversationId;
    this.userInput = Objects.requireNonNull(userInput, "userInput 不能为 null");
    this.systemPrompt = systemPrompt;
    this.variables = variables != null ? Map.copyOf(variables) : Collections.emptyMap();
    // 未指定迭代上限时默认 10 轮，作为 ReAct 循环的兜底上限，避免工具调用死循环
    this.maxIterations = maxIterations > 0 ? maxIterations : 10;
    this.enabledTools = enabledTools != null ? List.copyOf(enabledTools) : Collections.emptyList();
    this.contextMessages =
        contextMessages != null ? List.copyOf(contextMessages) : Collections.emptyList();
  }

  /**
   * 兼容构造（无预置上下文消息，由执行器自行加载历史）。
   *
   * @param agentCode Agent 编码
   * @param conversationId 对话 ID
   * @param userInput 本轮用户输入原文
   * @param systemPrompt 系统提示词
   * @param variables Prompt 模板渲染变量
   * @param maxIterations ReAct 循环最大迭代轮次
   * @param enabledTools 工具名白名单
   */
  public AgentExecutionRequest(
      String agentCode,
      String conversationId,
      String userInput,
      String systemPrompt,
      Map<String, Object> variables,
      int maxIterations,
      List<String> enabledTools) {
    this(
        agentCode,
        conversationId,
        userInput,
        systemPrompt,
        variables,
        maxIterations,
        enabledTools,
        null);
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
   * 获取上层预置的上下文历史消息。
   *
   * @return 不可变上下文消息列表（空表示由执行器自行加载历史）
   */
  public List<ChatMessage> getContextMessages() {
    return contextMessages;
  }

  /**
   * 判断是否已由上层预置上下文历史消息。
   *
   * @return true 表示 {@link #getContextMessages()} 非空
   */
  public boolean hasContextMessages() {
    return !contextMessages.isEmpty();
  }

  /**
   * 创建携带预置上下文历史消息的副本。
   *
   * <p>供 {@code AgentHarness} 在执行前注入「预算裁剪 + 压缩」后的历史，
   * 不修改原对象（本类不可变）。传入 null 或空列表表示清除预置消息、回退到执行器自加载。
   *
   * @param newContextMessages 预置上下文消息
   * @return 携带新上下文消息的副本
   */
  public AgentExecutionRequest withContextMessages(List<ChatMessage> newContextMessages) {
    return new AgentExecutionRequest(
        agentCode,
        conversationId,
        userInput,
        systemPrompt,
        variables,
        maxIterations,
        enabledTools,
        newContextMessages);
  }

  /**
   * 派生用于子 Agent 的执行请求，强制「管控继承」而非重新授权。
   *
   * <p>对标 AgentScope 的「子代理强制继承父级 DENY 规则」：子请求的工具白名单为
   * <b>父级白名单与子级自带工具的交集</b>，保证子 Agent 的工具面不会宽于父级
   * （父级白名单为空表示父级不限制，此时子级回退到父级不限制语义）。
   *
   * <p>其余推理参数（迭代上限、预置上下文）沿用父级，避免子级自行放大。
   *
   * @param subUserInput 子任务用户输入
   * @param subConversationId 子任务对话 ID
   * @param childTools 子 Agent 自身声明的工具（可为 null，表示不额外收窄）
   * @return 收紧后的子请求
   */
  public AgentExecutionRequest deriveForSubAgent(
      String subUserInput, String subConversationId, List<String> childTools) {
    List<String> inherited = intersectTools(enabledTools, childTools);
    return new AgentExecutionRequest(
        agentCode,
        subConversationId,
        subUserInput,
        systemPrompt,
        variables,
        maxIterations,
        inherited,
        Collections.emptyList());
  }

  /**
   * 工具白名单交集计算。
   *
   * <p>规则：父级不限制（空）→ 沿用子级；子级未声明（空）→ 沿用父级；两者均非空 → 取交集。
   *
   * @param parentTools 父级工具白名单
   * @param childTools 子级工具白名单
   * @return 收紧后的工具白名单
   */
  private static List<String> intersectTools(List<String> parentTools, List<String> childTools) {
    boolean parentUnlimited = parentTools == null || parentTools.isEmpty();
    boolean childUnlimited = childTools == null || childTools.isEmpty();
    if (parentUnlimited && childUnlimited) {
      return Collections.emptyList();
    }
    if (parentUnlimited) {
      return List.copyOf(childTools);
    }
    if (childUnlimited) {
      return List.copyOf(parentTools);
    }
    List<String> intersection = new ArrayList<>(parentTools.size());
    for (String tool : parentTools) {
      if (childTools.contains(tool)) {
        intersection.add(tool);
      }
    }
    return intersection;
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
    private List<ChatMessage> contextMessages;

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
     * 设置上层预置的上下文历史消息。
     *
     * @param contextMessages 预置上下文消息（null/空表示由执行器自行加载历史）
     * @return 当前 Builder
     */
    public Builder contextMessages(List<ChatMessage> contextMessages) {
      this.contextMessages = contextMessages;
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
          enabledTools,
          contextMessages);
    }
  }
}
