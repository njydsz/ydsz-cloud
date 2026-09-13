package com.njydsz.agent.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 对话记忆视图对象。
 *
 * <p>用于返回单条对话记忆消息的展示数据。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变视图载体；在单次响应序列化前于单线程内填充，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Data
@Schema(description = "对话记忆视图对象")
public class MemoryVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 消息唯一 ID */
  @Schema(description = "消息唯一 ID")
  private String id;

  /** 消息角色（SYSTEM/USER/ASSISTANT/TOOL） */
  @Schema(description = "消息角色")
  private String role;

  /** 消息内容 */
  @Schema(description = "消息内容")
  private String content;

  /** 所属对话 ID */
  @Schema(description = "所属对话 ID")
  private String conversationId;

  /** 创建时间 */
  @Schema(description = "创建时间")
  private LocalDateTime createdAt;

  /** 工具调用 ID（Tool 角色使用） */
  @Schema(description = "工具调用 ID")
  private String toolCallId;
}
