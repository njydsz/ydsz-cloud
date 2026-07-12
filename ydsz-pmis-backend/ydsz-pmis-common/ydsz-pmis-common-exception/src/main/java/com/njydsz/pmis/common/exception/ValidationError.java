package com.njydsz.pmis.common.exception;

import org.springframework.validation.BindingResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 校验异常详情
 *
 * <p>封装字段校验错误信息。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public class ValidationError {

    private String field;
    private String message;
    private Object rejectedValue;

    public ValidationError() {
    }

    public ValidationError(String field, String message, Object rejectedValue) {
        this.field = field;
        this.message = message;
        this.rejectedValue = rejectedValue;
    }

    public static ValidationError of(String field, String message) {
        return new ValidationError(field, message, null);
    }

    public static ValidationError of(String field, String message, Object rejectedValue) {
        return new ValidationError(field, message, rejectedValue);
    }

    /**
     * 从 BindingResult 创建校验错误列表
     *
     * @param bindingResult Spring BindingResult
     * @return 校验错误列表
     */
    public static List<ValidationError> from(BindingResult bindingResult) {
        List<ValidationError> errors = new ArrayList<>();
        if (bindingResult != null && bindingResult.hasErrors()) {
            bindingResult.getFieldErrors().forEach(fe ->
                    errors.add(new ValidationError(
                            fe.getField(),
                            fe.getDefaultMessage(),
                            fe.getRejectedValue()
                    ))
            );
        }
        return errors;
    }

    public String getField() {
        return field;
    }

    public String getMessage() {
        return message;
    }

    public Object getRejectedValue() {
        return rejectedValue;
    }

    public void setField(String field) {
        this.field = field;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setRejectedValue(Object rejectedValue) {
        this.rejectedValue = rejectedValue;
    }
}
