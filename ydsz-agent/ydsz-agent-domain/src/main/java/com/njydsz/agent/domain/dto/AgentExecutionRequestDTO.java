package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Agent 执行请求 DTO
 *
 * <p>封装通过指定 Agent 执行任务的请求参数， 支持 ReAct、Plan-Execute、Router 等多种 Agent 模式。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Schema(description = "Agent 执行请求")
public class AgentExecutionRequestDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** Agent 编码（指定使用哪个 Agent 执行） */
  @Schema(description = "Agent 编码（指定使用哪个 Agent）")
  private String agentCode;

  /** 对话 ID（null 表示新建对话） */
  @Schema(description = "对话 ID（null 表示新建对话）")
  private String conversationId;

  /** 请求幂等键（可选，防止重复调用 LLM 扣费） */
  @Schema(description = "请求幂等键（可选，防止重复调用 LLM 扣费）")
  private String requestId;

  /** 用户输入内容（必填） */
  @NotBlank(message = "用户输入不能为空")
  @Schema(description = "用户输入", requiredMode = Schema.RequiredMode.REQUIRED)
  private String userInput;

  /** 系统提示词（可选，覆盖 Agent 默认配置） */
  @Schema(description = "系统提示词（可选，覆盖默认）")
  private String systemPrompt;

  /** 最大迭代次数（ReAct 模式下生效，默认 10） */
  @Schema(description = "最大迭代次数（ReAct 模式）")
  private Integer maxIterations;

  /** 启用的工具列表（可选，为空则使用 Agent 默认工具配置） */
  @Schema(description = "启用的工具列表（可选）")
  private List<String> enabledTools;

  /** 是否流式输出（true 时通过 SSE 逐块返回结果） */
  @Schema(description = "是否流式输出")
  private boolean stream;
}
