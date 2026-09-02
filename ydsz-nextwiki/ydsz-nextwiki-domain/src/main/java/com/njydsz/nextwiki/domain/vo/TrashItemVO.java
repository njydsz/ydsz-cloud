package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 回收站条目 VO
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@Schema(description = "回收站条目信息")
public class TrashItemVO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "回收站条目ID")
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

  @Schema(description = "创建人")
  private String createdBy;

  @Schema(description = "创建时间")
  private LocalDateTime createdAt;
}
