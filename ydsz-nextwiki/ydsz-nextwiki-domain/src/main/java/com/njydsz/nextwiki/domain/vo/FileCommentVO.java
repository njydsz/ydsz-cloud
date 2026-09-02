package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 文件评论 VO
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@Schema(description = "文件评论信息")
public class FileCommentVO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "评论ID")
  private String id;

  @Schema(description = "关联的文件节点ID")
  private String fileNodeId;

  @Schema(description = "评论内容")
  private String content;

  @Schema(description = "父评论ID（用于回复，null 表示顶级评论）")
  private String parentCommentId;

  @Schema(description = "是否已解决")
  private Boolean resolved;

  @Schema(description = "评论位置信息（JSON）")
  private String position;

  @Schema(description = "是否被编辑过")
  private Boolean edited;

  @Schema(description = "创建人")
  private String createdBy;

  @Schema(description = "创建时间")
  private LocalDateTime createdAt;
}
