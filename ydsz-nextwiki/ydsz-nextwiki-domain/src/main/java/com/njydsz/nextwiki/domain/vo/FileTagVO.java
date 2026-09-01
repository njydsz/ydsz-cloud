package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 文件-标签关联 VO
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@Schema(description = "文件-标签关联信息")
public class FileTagVO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "关联记录ID")
  private String id;

  @Schema(description = "文件节点ID")
  private String fileNodeId;

  @Schema(description = "标签ID")
  private String tagId;

  @Schema(description = "创建人")
  private String createdBy;

  @Schema(description = "创建时间")
  private java.time.LocalDateTime createdAt;
}
