package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文件评论 DTO
 *
 * <p>用于文件评论的创建和更新操作，作为 Repository 接口 CUD 方法的入参。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "文件评论数据传输对象")
public class FileCommentDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "主键ID（更新时必填）")
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
}
