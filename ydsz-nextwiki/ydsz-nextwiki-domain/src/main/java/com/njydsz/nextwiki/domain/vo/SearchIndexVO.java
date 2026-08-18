package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 搜索索引 VO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "搜索索引信息")
public class SearchIndexVO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "索引记录ID")
  private String id;

  @Schema(description = "关联的文件节点ID")
  private String fileNodeId;

  @Schema(description = "文件名（用于搜索）")
  private String name;

  @Schema(description = "目录路径")
  private String path;

  @Schema(description = "索引内容")
  private String content;

  @Schema(description = "文件后缀")
  private String suffix;

  @Schema(description = "MIME 类型")
  private String mimeType;

  @Schema(description = "文件大小（字节）")
  private Long size;

  @Schema(description = "标签（逗号分隔）")
  private String tags;

  @Schema(description = "创建人")
  private String createdBy;

  @Schema(description = "创建时间")
  private java.time.LocalDateTime createdAt;
}
