package com.njydsz.pmis.common.exception.custom;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

import lombok.ToString;

/**
 * 业务异常类
 *
 * <p>用于封装业务逻辑中的异常情况，支持国际化消息处理、异常分类、级别定义等功能。
 * 异常包含错误码、消息键、参数、HTTP状态码等完整上下文信息。
 *
 * <p><b>HTTP状态码映射规则：</b>
 * <ul>
 *   <li>400 Bad Request：参数校验失败、非法请求</li>
 *   <li>401 Unauthorized：未登录或登录过期</li>
 *   <li>403 Forbidden：权限不足</li>
 *   <li>404 Not Found：资源不存在</li>
 *   <li>429 Too Many Requests：请求过于频繁</li>
 *   <li>500 Internal Server Error：系统内部错误</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * throw new BusinessException(ExceptionCode.DATA_NOT_FOUND);
 * throw BusinessException.of("user.not.found").params(userId).httpStatus(404).build();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 3.0.0
 */
@ToString(callSuper = true)
public class BusinessException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    private transient ConcurrentHashMap<String, Object> dataMap;

    // ==================== 保留的常用构造函数 ====================

    public BusinessException() {
        super();
        this.httpStatus = HttpStatus.BAD_REQUEST.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.BUSINESS;
    }

    /**
     * @param exceptionCode 异常码枚举
     */
    public BusinessException(ExceptionCode exceptionCode) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.BUSINESS;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public BusinessException(Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.BAD_REQUEST.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.BUSINESS;
        this.code = UnifiedExceptionCode.FAIL.getCode();
    }

    // ==================== 业务方法 ====================

    /**
     * 添加附加数据（链式调用）
     *
     * @param key   数据键
     * @param value 数据值
     * @return 当前异常对象
     */
    public BusinessException data(String key, Object value) {
        if (this.dataMap == null) {
            this.dataMap = new ConcurrentHashMap<>();
        }
        this.dataMap.put(key, value);
        return this;
    }

    public ExceptionInfo toExceptionInfo() {
        return buildExceptionInfo();
    }

    /**
     * 获取业务异常构建器
     *
     * @return BusinessExceptionBuilder 实例
     */
    public static BusinessExceptionBuilder builder() {
        return new BusinessExceptionBuilder();
    }

    public static BusinessException of(ExceptionCode exceptionCode) {
        return new BusinessException(exceptionCode);
    }

    // ==================== Builder ====================

    /**
     * 业务异常构建器，预置默认的错误码、HTTP状态码、级别和分类
     */
    public static class BusinessExceptionBuilder extends YdszExceptionBuilder<BusinessException, BusinessExceptionBuilder> {

        @Override
        protected BusinessExceptionBuilder self() {
            return this;
        }

        public BusinessExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.FAIL.getCode();
            this.httpStatus = HttpStatus.BAD_REQUEST.value();
            this.level = ExceptionLevel.ERROR;
            this.category = ExceptionCategory.BUSINESS;
        }

        @Override
        protected BusinessException doBuild(String code, String key, Object[] params, int httpStatus,
                                            ExceptionLevel level, ExceptionCategory category,
                                            Throwable cause, String path, Object extData, String message) {
            BusinessException exception = new BusinessException();
            exception.initFields(code, key, params);
            exception.setHttpStatus(httpStatus);
            exception.setLevel(level);
            exception.setCategory(category);
            exception.setPath(path);
            exception.setExtData(extData);
            if (cause != null) {
                exception.initCause(cause);
            }
            return exception;
        }
    }
}
