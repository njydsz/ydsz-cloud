package com.njydsz.message.domain.dto.template;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 模板灰度发布请求 DTO（P2-F4）。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Data
@Schema(description = "模板灰度发布请求")
public class TemplateCanaryDTO {

  /** 模板编码 */
  @NotBlank(message = "模板编码不能为空")
  private String templateCode;

  /** 待灰度的版本号 */
  @NotNull(message = "版本号不能为空")
  private Integer version;

  /** 灰度流量百分比（1-100） */
  @NotNull(message = "灰度百分比不能为空")
  @Min(value = 1, message = "灰度百分比最小为 1")
  @Max(value = 100, message = "灰度百分比最大为 100")
  private Integer canaryPercent;

  /** 灰度描述 */
  private String description;
}
