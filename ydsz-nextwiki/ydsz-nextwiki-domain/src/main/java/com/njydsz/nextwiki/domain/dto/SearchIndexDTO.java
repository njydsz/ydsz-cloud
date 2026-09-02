package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 搜索索引 DTO
 *
 * <p>用于搜索索引的创建和更新操作，作为 Repository 接口 CUD 方法的入参。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "搜索索引数据传输对象")
public class SearchIndexDTO implements Serializable {

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
}
