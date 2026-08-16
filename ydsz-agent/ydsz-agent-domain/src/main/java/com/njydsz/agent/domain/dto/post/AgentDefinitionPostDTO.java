package com.njydsz.agent.domain.dto.post;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * AgentDefinition 新增请求 DTO。
 *
 * <p>用于 Controller POST 接口接收 Agent 定义创建请求。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变入参载体；仅在单次请求绑定期间使用，框架按请求单线程处理，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AgentDefinitionPostDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** Agent 编码 */
  private String agentCode;

  /** Agent 名称 */
  private String agentName;

  /** Agent 类型 */
  private String agentType;

  /** 描述 */
  private String description;

  /** 系统提示词 */
  private String systemPrompt;

  /** 模型配置 JSON */
  private String modelConfig;

  /** 工具名称列表 JSON */
  private String toolNames;

  /** 温度参数 */
  private Double temperature;

  /** 最大生成 Token 数 */
  private Integer maxTokens;
}
