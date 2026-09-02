package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 全文搜索结果 VO
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@Schema(description = "搜索结果")
public class SearchResultVO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "搜索结果列表")
  private List<SearchHitVO> hits;

  @Schema(description = "总匹配数")
  private Long total;

  @Schema(description = "当前页码")
  private Integer page;

  @Schema(description = "每页大小")
  private Integer pageSize;

  @Schema(description = "搜索耗时（毫秒）")
  private Long tookMs;

  /** 单条搜索命中 */
  @Data
  @Builder
  @Schema(description = "搜索命中条目")
  public static class SearchHitVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "文件节点ID")
    private String fileNodeId;

    @Schema(description = "文件名")
    private String name;

    @Schema(description = "文件路径")
    private String path;

    @Schema(description = "节点类型")
    private String nodeType;

    @Schema(description = "文件扩展名")
    private String suffix;

    @Schema(description = "文件大小")
    private Long size;

    @Schema(description = "内容高亮片段")
    private String highlight;

    @Schema(description = "匹配分数")
    private Float score;

    @Schema(description = "标签列表")
    private List<String> tags;

    @Schema(description = "创建人")
    private String createdBy;

    @Schema(description = "更新时间")
    private String updatedAt;
  }
}
