package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存对话记忆请求 DTO。
 *
 * <p>封装写入单条对话记忆的消息数据。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Data
@Schema(description = "保存对话记忆请求")
public class SaveMemoryRequest implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 消息角色（SYSTEM/USER/ASSISTANT/TOOL） */
  @NotBlank(message = "消息角色不能为空")
  @Schema(description = "消息角色")
  private String role;

  /** 消息内容 */
  @Schema(description = "消息内容")
  private String content;

  /** 工具调用 ID（Tool 角色必填） */
  @Schema(description = "工具调用 ID")
  private String toolCallId;
}
