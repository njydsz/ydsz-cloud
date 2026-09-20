package com.njydsz.common.search.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索请求被拒绝 / 出错时的错误信息。
 *
 * <p>当 {@link SearchResponse#error} 非 {@code null} 时，表示请求未能正常检索（限流、参数错误、引擎故障）， 调用方应据此决定 UX 展示（如： toast 提示 vs. 展示空结果页）。
 *
 * <p>该错误码采用"不抛异常"风格返回，永不因错误码导致搜索链路抛出运行时异常。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "搜索错误信息。null 表示请求正常")
public class SearchError {

  /** 错误码（参见 {@link SearchErrorCode}） */
  @Schema(description = "错误码")
  private int code;

  /** i18n 消息 key */
  @Schema(description = "i18n 消息 key，前端据此加载具体语言文案")
  private String messageKey;

  /** 面向开发者的兜底英文描述（可用于日志，不应直接给终端用户看） */
  @Schema(description = "开发者英文描述（仅用于日志，不直接展示给用户）")
  private String detail;

  public static SearchError of(SearchErrorCode errorCode, String detail) {
    return new SearchError(errorCode.getCode(), errorCode.getMessageKey(), detail);
  }

  public static SearchError of(SearchErrorCode errorCode) {
    return new SearchError(errorCode.getCode(), errorCode.getMessageKey(), null);
  }
}
