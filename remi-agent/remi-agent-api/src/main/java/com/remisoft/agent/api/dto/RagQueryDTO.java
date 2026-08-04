package com.remisoft.agent.api.dto;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * RAG 查询请求 DTO
 *
 * <p>封装对 RAG 知识库的检索请求参数，
 * 包括查询文本、返回数量、相似度阈值等。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Schema(description = "RAG 查询请求")
public class RagQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 查询文本（必填，将向量化后进行相似度检索） */
    @NotBlank(message = "查询内容不能为空")
    @Schema(description = "查询文本", requiredMode = Schema.RequiredMode.REQUIRED)
    private String query;

    /** 返回前 K 条结果（默认 5） */
    @Schema(description = "返回前 K 条（默认 5）")
    private Integer topK;

    /** 最小相似度阈值（0-1，默认 0.7，低于此值的结果将被过滤） */
    @Schema(description = "最小相似度阈值（0-1，默认 0.7）")
    private Double minScore;

    /** 是否包含上下文文本（默认 true，false 时仅返回元数据） */
    @Schema(description = "是否包含上下文文本（默认 true）")
    private Boolean includeContext;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
    public Double getMinScore() { return minScore; }
    public void setMinScore(Double minScore) { this.minScore = minScore; }
    public Boolean getIncludeContext() { return includeContext; }
    public void setIncludeContext(Boolean includeContext) { this.includeContext = includeContext; }
}
