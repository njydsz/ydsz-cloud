package com.njydsz.common.search.api;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 搜索命中条目
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@Schema(description = "搜索命中条目")
public class SearchHit implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 文档 ID */
  @Schema(description = "文档ID")
  private String id;

  /** 实体类型（project/contract/wiki/...） */
  @Schema(description = "实体类型")
  private String type;

  /** 标题 */
  @Schema(description = "标题")
  private String title;

  /** 副标题 */
  @Schema(description = "副标题")
  private String subtitle;

  /** 摘要/片段 */
  @Schema(description = "摘要")
  private String snippet;

  /** 内容高亮片段（HTML 格式，含 &lt;em&gt; 标签） */
  @Schema(description = "高亮片段")
  private String highlight;

  /** 匹配分数（越高越相关） */
  @Schema(description = "匹配分数")
  @Builder.Default
  private float score = 0.0f;

  /** 跳转路径（前端路由） */
  @Schema(description = "跳转路径")
  private String path;

  /** 状态 */
  @Schema(description = "状态")
  private String status;

  /** 标签列表 */
  @Schema(description = "标签列表")
  @Builder.Default
  private List<String> tags = Collections.emptyList();

  /** 扩展字段 */
  @Schema(description = "扩展字段")
  @Builder.Default
  private Map<String, Object> metadata = Collections.emptyMap();

  /** 创建时间（ISO 格式字符串） */
  @Schema(description = "创建时间")
  private String createdAt;

  /** 更新时间（ISO 格式字符串） */
  @Schema(description = "更新时间")
  private String updatedAt;
}
