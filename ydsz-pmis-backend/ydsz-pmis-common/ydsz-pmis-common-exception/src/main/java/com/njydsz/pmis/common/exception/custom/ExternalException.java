package com.njydsz.pmis.common.exception.custom;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

import java.io.Serial;

/**
 * 外部服务异常（第三方接口超时/错误）
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public class ExternalException extends AbstractPmisException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ExternalException() {
        super();
        this.code = UnifiedExceptionCode.EXTERNAL_SERVICE_ERROR.getCode();
        this.httpStatus = 502;
        this.level = ExceptionLevel.FATAL;
        this.category = ExceptionCategory.EXTERNAL;
    }

    public ExternalException(String message) {
        super(message);
        this.code = UnifiedExceptionCode.EXTERNAL_SERVICE_ERROR.getCode();
        this.httpStatus = 502;
        this.level = ExceptionLevel.FATAL;
        this.category = ExceptionCategory.EXTERNAL;
    }

    public ExternalException(String message, Throwable cause) {
        super(message, cause);
        this.code = UnifiedExceptionCode.EXTERNAL_SERVICE_ERROR.getCode();
        this.httpStatus = 502;
        this.level = ExceptionLevel.FATAL;
        this.category = ExceptionCategory.EXTERNAL;
    }
}
