package com.njydsz.common.search.api;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 搜索建议（自动补全 / "您是不是要找" / 热门搜索）。
 *
 * <p>封装搜索引擎返回的查询建议结果，包含三种来源：
 * <ul>
 *   <li>{@link SuggestionSource#ENGINE} — 搜索引擎原生建议（自动补全词 / 纠错词）</li>
 *   <li>{@link SuggestionSource#HOT_KEYWORD} — 热门搜索词（基于 {@code SearchAnalyticsService} 统计）</li>
 *   <li>{@link SuggestionSource#DID_YOU_MEAN} — 零结果纠错建议</li>
 *   <li>{@link SuggestionSource#PERSONALIZED} — 用户历史搜索个性化推荐</li>
 * </ul>
 *
 * <p>前端可通过 {@link SuggestionItem#source} 字段区分展示样式（图标、分组、快捷操作等）。
 *
 * <p>典型用途：搜索框下拉联想词（分组展示引擎建议 + 热门搜索）、搜索无结果时的智能纠错提示。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "搜索建议")
public class SearchSuggestion implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 建议主类型（用于前端分组标题渲染） */
  @Schema(description = "建议主类型")
  private SuggestionType type;

  /** 分类建议条目（带来源标签），替代 {@code suggestions} 字段 */
  @Schema(description = "分类建议条目（含来源标签）")
  @Builder.Default
  private List<SuggestionItem> items = Collections.emptyList();

  /**
   * 建议文本列表（向后兼容字段）。
   *
   * <p>当 {@link #items} 非空时，本字段由 {@link #items} 自动派生（取各条目文本）； 当 {@link #items} 为空时，保留旧有的纯文本列表语义。
   */
  @Schema(description = "建议文本列表（向后兼容）")
  @Builder.Default
  private List<String> suggestions = Collections.emptyList();

  /** 原始输入 */
  @Schema(description = "原始输入")
  private String originalInput;

  /**
   * 建议主类型（用于前端判断展示模板）。
   */
  public enum SuggestionType {
    /** 自动补全下拉 */
    AUTOCOMPLETE,
    /** 零结果纠错 */
    DID_YOU_MEAN,
    /** 热门搜索聚合 */
    HOT_SEARCH
  }

  /**
   * 建议条目来源（前端分组与图标判断依据）。
   */
  public enum SuggestionSource {
    /** 搜索引擎原生自动补全 */
    ENGINE,
    /** 热门搜索词 */
    HOT_KEYWORD,
    /** 零结果纠错 */
    DID_YOU_MEAN,
    /** 个性化推荐 */
    PERSONALIZED
  }

  /**
   * 分类建议条目。
   *
   * @param text 建议文本
   * @param source 来源类型（用于前端分组与图标渲染）
   * @param highlight 高亮片段（可选，含 {@code <em>} 标签的 HTML）
   */
  @Getter
  @AllArgsConstructor
  @Schema(description = "分类建议条目")
  public static class SuggestionItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "建议文本")
    private final String text;

    @Schema(description = "来源类型")
    private final SuggestionSource source;

    @Schema(description = "高亮片段（HTML，含 <em> 标签）")
    private String highlight;

    public SuggestionItem(String text, SuggestionSource source) {
      this.text = text;
      this.source = source;
      this.highlight = null;
    }
  }

  /**
   * 从 items 派生 suggestions 列表（辅助方法）。
   *
   * <p>当 items 非空但其对应的 suggestions 未设置时，自动从 items 提取纯文本列表， 保持向后兼容性。
   *
   * @return 不会为 {@code null}
   */
  public List<String> getSuggestions() {
    if (!suggestions.isEmpty()) {
      return suggestions;
    }
    if (items == null || items.isEmpty()) {
      return Collections.emptyList();
    }
    return items.stream().map(SuggestionItem::getText).collect(Collectors.toList());
  }
}
