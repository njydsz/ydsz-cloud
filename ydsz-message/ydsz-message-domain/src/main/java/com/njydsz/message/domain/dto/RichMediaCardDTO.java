package com.njydsz.message.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 富媒体卡片消息结构。
 *
 * <p>用于企微/钉钉/飞书等 IM 通道的结构化消息，支持标题、内容、按钮、链接等丰富交互元素。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "富媒体卡片消息")
public class RichMediaCardDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 卡片标题 */
  @Schema(description = "卡片标题", example = "您有一条新通知")
  private String title;

  /** 卡片内容（支持 Markdown） */
  @Schema(description = "卡片内容")
  private String content;

  /** 卡片底部按钮列表 */
  @Schema(description = "按钮列表")
  private List<CardButton> buttons;

  /** 跳转链接（点击卡片跳转） */
  @Schema(description = "跳转链接")
  private String url;

  /** 卡片图片 URL */
  @Schema(description = "图片 URL")
  private String imageUrl;

  /**
   * 卡片按钮。
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "卡片按钮")
  public static class CardButton implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 按钮文本 */
    @Schema(description = "按钮文本", example = "查看详情")
    private String text;

    /** 按钮动作类型：URL / CALLBACK */
    @Schema(description = "动作类型", example = "URL")
    private String actionType;

    /** 动作值（URL 或回调 key） */
    @Schema(description = "动作值")
    private String actionValue;
  }
}
