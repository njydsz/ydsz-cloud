package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 标签 VO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "标签信息")
public class TagVO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "标签ID")
  private String id;

  @Schema(description = "标签名称")
  private String name;

  @Schema(description = "标签颜色（十六进制颜色码）")
  private String color;

  @Schema(description = "标签类型：manual / auto / system")
  private String type;

  @Schema(description = "使用次数")
  private Integer usageCount;

  @Schema(description = "创建人")
  private String createdBy;

  @Schema(description = "创建时间")
  private java.time.LocalDateTime createdAt;
}
