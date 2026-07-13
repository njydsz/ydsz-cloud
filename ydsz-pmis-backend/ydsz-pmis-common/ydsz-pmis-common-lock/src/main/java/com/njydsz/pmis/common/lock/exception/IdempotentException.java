package com.njydsz.pmis.common.lock.exception;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

/**
 * 接口幂等性异常
 *
 * <p>在 {@link com.njydsz.pmis.common.lock.annotation.Idempotent} 注解拦截到重复提交时抛出。
 * 与 {@link DistributedLockException} 不同，本异常表示"同一业务键在 TTL 窗口内已处理过"，
 * 属于业务约束冲突，对应 HTTP 409 Conflict。
 *
 * <p>错误码：{@code IDEMPOTENT_REJECT}，消息键同错误码，前端可直接展示 message。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.1.0
 */
public class IdempotentException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * 默认错误码
     */
    private static final String DEFAULT_CODE = "IDEMPOTENT_REJECT";

    /**
     * 构造幂等性异常
     *
     * @param message 异常消息（通常来自 {@code @Idempotent.message()}）
     */
    public IdempotentException(String message) {
        super();
        this.httpStatus = 409;
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.BUSINESS;
        this.code = DEFAULT_CODE;
        this.key = DEFAULT_CODE;
        this.params = new Object[]{};
        this.message = message;
        this.messageKey = DEFAULT_CODE;
        this.messageParams = this.params;
    }

    /**
     * 构造幂等性异常（带幂等键，便于日志追踪）
     *
     * @param message 异常消息
     * @param idempotentKey 触发幂等的 Redis 键
     */
    public IdempotentException(String message, String idempotentKey) {
        super();
        this.httpStatus = 409;
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.BUSINESS;
        this.code = DEFAULT_CODE;
        this.key = DEFAULT_CODE;
        this.params = new Object[]{idempotentKey};
        this.message = message;
        this.messageKey = DEFAULT_CODE;
        this.messageParams = this.params;
    }
}
