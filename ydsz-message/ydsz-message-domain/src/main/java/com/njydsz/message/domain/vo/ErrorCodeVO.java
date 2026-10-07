package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务异常错误码视图对象。
 *
 * <p>封装 {@link com.njydsz.common.exception.enums.ExceptionCode} 枚举的核心元信息，供前端 TypeScript
 * 代码生成器消费，自动建立错误码 → i18n 文案的映射关系，避免手工维护。
 *
 * <p><b>字段说明：</b>
 *
 * <ul>
 *   <li>{@link #code} — 业务错误码（如 B91001），全局唯一
 *   <li>{@link #messageKey} — i18n 消息键（如 message.template.not.found）
 *   <li>{@link #httpStatus} — HTTP 状态码（如 404）
 *   <li>{@link #description} — 错误码英文描述（非中文文案，避免硬编码）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "业务异常错误码元信息")
public class ErrorCodeVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 业务错误码（全局唯一，如 B91001） */
  @Schema(description = "业务错误码", example = "B91001")
  private String code;

  /** i18n 消息键 */
  @Schema(description = "i18n 消息键", example = "message.template.not.found")
  private String messageKey;

  /** HTTP 状态码 */
  @Schema(description = "HTTP 状态码", example = "404")
  private int httpStatus;

  /** 错误码描述（英文，非中文文案） */
  @Schema(description = "错误码描述", example = "Message template not found")
  private String description;
}
