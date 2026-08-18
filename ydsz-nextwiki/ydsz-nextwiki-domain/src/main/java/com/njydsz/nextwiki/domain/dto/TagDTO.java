package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 标签 DTO
 *
 * <p>用于标签的创建和更新操作，作为 Repository 接口 CUD 方法的入参。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "标签数据传输对象")
public class TagDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "主键ID（更新时必填）")
  private String id;

  @Schema(description = "标签名称")
  private String name;

  @Schema(description = "标签颜色（十六进制颜色码）")
  private String color;

  @Schema(description = "标签类型：manual / auto / system")
  private String type;

  @Schema(description = "使用次数")
  private Integer usageCount;
}
