package com.njydsz.nextwiki.domain.query;

import java.io.Serializable;

import com.njydsz.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
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
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(description = "文件节点分页查询参数")
public class FileNodeQuery extends PageQuery implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "父目录ID")
  private String parentId;

  @Schema(description = "节点类型过滤（file/folder，null 或 all 表示不过滤）")
  private String nodeType;

  @Schema(description = "排序字段：name / size / time")
  private String sortBy;

  @Schema(description = "排序方向：asc / desc")
  private String sortDir;
}
