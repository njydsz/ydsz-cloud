package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务异常错误码视图对象。
 *
 * <p>用于 {@code GET /system/error-codes} 端点，将全局错误码注册表（{@link
 * com.njydsz.common.exception.code.ErrorCodeTable}）中的错误码暴露给前端/第三方系统。
 *
 * <p>前端可根据此数据自动生成错误码 → i18n 文案映射，消除手工同步 {@code errors.ts} 的维护成本。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "业务异常错误码")
public class ErrorCodeVO {

  /** 错误码（如 B90001） */
  @Schema(description = "错误码", example = "B90001")
  private String code;

  /** i18n 消息键（如 system.config.not.found） */
  @Schema(description = "i18n 消息键", example = "system.config.not.found")
  private String messageKey;

  /** HTTP 状态码（如 404） */
  @Schema(description = "HTTP 状态码", example = "404")
  private int httpStatus;

  /** 模块名（如 system） */
  @Schema(description = "所属模块", example = "system")
  private String module;

  /** 模块描述（如 系统管理） */
  @Schema(description = "模块描述", example = "系统管理")
  private String moduleDescription;
}
