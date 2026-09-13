package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 记忆整合请求 DTO。
 *
 * <p>用于触发对指定对话的历史记忆执行整合（提取事实 + 画像刷新）。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Data
@Schema(description = "记忆整合请求")
public class ConsolidateMemoryRequest implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 租户 ID（可选，缺省使用当前租户） */
  @Schema(description = "租户 ID")
  private String tenantId;
}
