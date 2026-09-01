package com.njydsz.workflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 审批常用语 DTO
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Schema(description = "审批常用语")
public class FlowQuickCommentDTO {

  @Schema(description = "ID（编辑时传）")
  private String id;

  @Schema(description = "常用语内容", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "常用语内容不能为空")
  @Size(max = 500, message = "常用语内容不能超过500字")
  @Xss(message = "常用语内容包含非法字符")
  private String content;

  @Schema(description = "意见分类：AGREE/DISAGREE/SUGGEST/INQUIRE")
  private String commentType;

  @Schema(description = "排序号（越小越靠前，默认0）")
  private Integer sortNum;
}
