package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * AgentDefinition 统一请求 DTO。
 *
 * <p>不区分 Create / Update，使用统一 DTO：创建时 {@code id} 字段不传，更新时传入 {@code id}。
 * {@code id} 字段不加 {@code @NotBlank}，通过 infra 层 Converter 的 {@code dtoToEntity}（ignore id，创建场景）
 * 与 {@code dtoToEntityWithId}（保留 id，更新场景）区分行为。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变入参载体；仅在单次请求绑定期间使用，框架按请求单线程处理，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class AgentDefinitionDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * 主键 ID。
   *
   * <p>创建场景不传；更新场景必须传入。
   */
  private String id;

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
