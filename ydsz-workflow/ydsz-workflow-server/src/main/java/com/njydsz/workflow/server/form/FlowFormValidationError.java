package com.njydsz.workflow.server.form;

import java.io.Serial;
import java.io.Serializable;

/**
 * 表单校验错误（P0-3 表单引擎 MVP）
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public class FlowFormValidationError implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 出错字段 key（子表单字段格式为 parentKey[index].childKey） */
  private final String fieldKey;

  /** 错误消息 */
  private final String message;

  /** 错误码（REQUIRED/MIN/MAX/PATTERN/MIN_LENGTH/MAX_LENGTH/MIN_SELECTED 等） */
  private final String code;

  
  /**
   * 构造表单字段校验错误。
   *
   * @param fieldKey 字段 key
   * @param code 错误码
   * @param message 错误信息
   */
  public FlowFormValidationError(String fieldKey, String code, String message) {
    this.fieldKey = fieldKey;
    this.code = code;
    this.message = message;
  }

    /** @return 字段 key。 */
  public String getFieldKey() {
    return fieldKey;
  }

    /** @return 错误信息。 */
  public String getMessage() {
    return message;
  }

    /** @return 错误码。 */
  public String getCode() {
    return code;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return fieldKey + ": " + message + " (" + code + ")";
  }
}
