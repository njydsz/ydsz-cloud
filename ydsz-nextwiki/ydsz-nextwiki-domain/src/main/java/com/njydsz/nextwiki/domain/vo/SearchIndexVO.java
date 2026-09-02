package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 搜索索引 VO
 *
 * <p>用于搜索索引的查询结果返回，作为 Repository 接口查询方法的返回值。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@Schema(description = "搜索索引视图对象")
public class SearchIndexVO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "主键ID")
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

  @Schema(description = "更新时间")
  private LocalDateTime updatedAt;
}
