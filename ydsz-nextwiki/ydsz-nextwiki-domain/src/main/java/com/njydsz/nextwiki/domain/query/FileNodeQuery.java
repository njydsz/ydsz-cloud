package com.njydsz.nextwiki.domain.query;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文件节点分页查询 Query
 *
 * <p>用于文件节点的分页查询，作为 Repository 接口查询方法的入参。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "文件节点分页查询参数")
public class FileNodeQuery implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "父目录ID")
  private String parentId;

  @Schema(description = "节点类型过滤（file/folder，null 或 all 表示不过滤）")
  private String nodeType;

  @Schema(description = "排序字段：name / size / time")
  private String sortBy;

  @Schema(description = "排序方向：asc / desc")
  private String sortDir;

  @Schema(description = "页码（从 1 开始）")
  private Integer page;

  @Schema(description = "每页大小")
  private Integer pageSize;
}
