package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 回收站条目 DTO
 *
 * <p>用于回收站条目的创建和更新操作，作为 Repository 接口 CUD 方法的入参。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "回收站条目数据传输对象")
public class TrashItemDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "主键ID（更新时必填）")
  private String id;

  @Schema(description = "原文件节点ID")
  private String fileNodeId;

  @Schema(description = "原文件名")
  private String originalName;

  @Schema(description = "原始路径")
  private String originalPath;

  @Schema(description = "原始父节点ID")
  private String originalParentId;

  @Schema(description = "节点类型：folder / file")
  private String nodeType;

  @Schema(description = "文件大小（字节）")
  private Long size;

  @Schema(description = "删除时间")
  private LocalDateTime deletedTime;

  @Schema(description = "预计永久删除时间")
  private LocalDateTime purgeTime;

  @Schema(description = "状态：in_trash / restored / purged")
  private String status;
}
