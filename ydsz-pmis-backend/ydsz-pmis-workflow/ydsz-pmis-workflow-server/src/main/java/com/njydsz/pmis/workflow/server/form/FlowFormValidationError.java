package com.njydsz.pmis.workflow.server.form;

import java.io.Serial;
import java.io.Serializable;

/**
 * 表单校验错误（P0-3 表单引擎 MVP）
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
public class FlowFormValidationError implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 出错字段 key（子表单字段格式为 parentKey[index].childKey） */
    private final String fieldKey;

    /** 错误消息 */
    private final String message;

    /** 错误码（REQUIRED/MIN/MAX/PATTERN/MIN_LENGTH/MAX_LENGTH/MIN_SELECTED 等） */
    private final String code;

    public FlowFormValidationError(String fieldKey, String code, String message) {
        this.fieldKey = fieldKey;
        this.code = code;
        this.message = message;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public String getMessage() {
        return message;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return fieldKey + ": " + message + " (" + code + ")";
    }
}
