package com.njydsz.userinfo.server.config;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.exception.BusinessException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局异常处理器。
 *
 * <p>统一处理 BusinessException、参数校验异常、其他未捕获异常，
 * 转换为标准 BaseResponse 格式返回，并根据错误类型映射不同的 HTTP 状态码。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("Business exception: code={}, msg={}", e.getCode(), e.getMessage());
        HttpStatus status = resolveHttpStatus(e.getCode());
        BaseResponse<Void> response = BaseResponse.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String errors = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", errors);
        BaseResponse<Void> response = BaseResponse.error(BaseResultCode.VALIDATION_FAILED.getCode(), errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<BaseResponse<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        String errors = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("Constraint violation: {}", errors);
        BaseResponse<Void> response = BaseResponse.error(BaseResultCode.VALIDATION_FAILED.getCode(), errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        BaseResponse<Void> response = BaseResponse.error(BaseResultCode.BAD_REQUEST.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleGenericException(Exception e) {
        log.error("Unexpected error", e);
        BaseResponse<Void> response = BaseResponse.error(BaseResultCode.INTERNAL_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 根据业务错误码映射 HTTP 状态码。
     *
     * <p>映射规则：
     * <ul>
     *   <li>USER_NOT_FOUND → 404</li>
     *   <li>PASSWORD_INCORRECT / TOKEN_INVALID / MFA → 401</li>
     *   <li>ACCOUNT_LOCKED → 423</li>
     *   <li>其余业务异常 → 400</li>
     * </ul>
     */
    private HttpStatus resolveHttpStatus(String code) {
        if (code == null) {
            return HttpStatus.BAD_REQUEST;
        }
        if (UserInfoResultCode.USER_NOT_FOUND.getCode().equals(code)) {
            return HttpStatus.NOT_FOUND;
        }
        if (UserInfoResultCode.PASSWORD_INCORRECT.getCode().equals(code)
                || UserInfoResultCode.TOKEN_INVALID.getCode().equals(code)
                || UserInfoResultCode.MFA_REQUIRED.getCode().equals(code)
                || UserInfoResultCode.MFA_INVALID.getCode().equals(code)) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (UserInfoResultCode.ACCOUNT_LOCKED.getCode().equals(code)) {
            return HttpStatus.LOCKED;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
