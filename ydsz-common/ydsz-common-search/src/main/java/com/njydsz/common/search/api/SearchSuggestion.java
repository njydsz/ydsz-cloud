package com.njydsz.common.search.api;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索建议（自动补全 / "您是不是要找"）
 *
 * <p>封装搜索引擎返回的查询建议结果，包含两种类型：
 * <ul>
 *   <li>{@link SuggestionType#AUTOCOMPLETE} — 输入时自动补全，返回 TOP-N 候选词</li>
 *   <li>{@link SuggestionType#DID_YOU_MEAN} — 零结果纠错建议，返回"您是不是要找"候选词</li>
 * </ul>
 *
 * <p>典型用途：搜索框下拉联想词、搜索无结果时的智能纠错提示。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "搜索建议")
public class SearchSuggestion implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 建议类型 */
  @Schema(description = "建议类型")
  private SuggestionType type;

  /** 建议文本列表 */
  @Schema(description = "建议文本列表")
  @Builder.Default
  private List<String> suggestions = Collections.emptyList();

  /** 原始输入 */
  @Schema(description = "原始输入")
  private String originalInput;

  /** 建议类型 */
  public enum SuggestionType {
    /** 自动补全 */
    AUTOCOMPLETE,
    /** "您是不是要找"（零结果纠错建议） */
    DID_YOU_MEAN
  }
}
