package com.njydsz.pmis.common.search.api;

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
 * @author ydsz-pmis-team
 * @since 1.4.0
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

    /**
     * 建议类型
     */
    public enum SuggestionType {
        /** 自动补全 */
        AUTOCOMPLETE,
        /** "您是不是要找"（零结果纠错建议） */
        DID_YOU_MEAN
    }
}
