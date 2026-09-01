package com.njydsz.literule.api.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 规则批量启停请求体 DTO
 *
 * <p>用于 {@code /rules/batch-toggle} 接口，批量启用/停用规则。 启用时校验 status=PUBLISHED，未发布的规则不能启用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Schema(description = "规则批量启停请求体")
public class RuleBatchToggleDTO {

  /** 规则编码列表 */
  @Schema(description = "规则编码列表", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotEmpty(message = "{validation.project.msg_e5c6d7e5}")
  private List<String> ruleCodes;

  /** 是否启用（true=启用，false=停用） */
  @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull(message = "{validation.project.msg_f6d7e8f6}")
  private Boolean enabled;
}
