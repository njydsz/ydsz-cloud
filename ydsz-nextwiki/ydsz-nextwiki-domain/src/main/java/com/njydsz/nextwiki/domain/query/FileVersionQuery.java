package com.njydsz.nextwiki.domain.query;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文件版本查询 Query
 *
 * <p>用于文件版本的查询，作为 Repository 接口查询方法的入参。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "文件版本查询参数")
public class FileVersionQuery implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "文件节点ID")
  private String fileNodeId;

  @Schema(description = "版本号")
  private Integer versionNumber;
}
