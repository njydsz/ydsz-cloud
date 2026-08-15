package com.njydsz.common.lock.exception;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;


/**
 * 接口幂等性异常
 *
 * <p>在 {@link com.njydsz.common.lock.annotation.Idempotent} 注解拦截到重复提交时抛出。
 * 与 {@link DistributedLockException} 不同，本异常表示"同一业务键在 TTL 窗口内已处理过"，
 * 属于业务约束冲突，对应 HTTP 409 Conflict。
 *
 * <p>错误码使用 {@link CoreExceptionCode#IDEMPOTENT_REJECT}（A07001），
 * i18n 消息键 {@code idempotent.reject}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class IdempotentException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造幂等性异常
     *
     * @param message 异常消息（通常来自 {@code @Idempotent.message()}）
     */
    public IdempotentException(String message) {
        super();
        initFields(CoreExceptionCode.IDEMPOTENT_REJECT.getCode(),
                CoreExceptionCode.IDEMPOTENT_REJECT.getKey(), new Object[]{});
        setHttpStatus(CoreExceptionCode.IDEMPOTENT_REJECT.getHttpStatus());
        setLevel(ExceptionLevel.WARN);
        setCategory(ExceptionCategory.BUSINESS);
        // 使用 setMessage 确保 @Idempotent.message() 用户可读文案优先展示
        setMessage(message);
    }

    /**
     * 构造幂等性异常（带幂等键，便于日志追踪）
     *
     * @param message      异常消息
     * @param idempotentKey 触发幂等的 Redis 键
     */
    public IdempotentException(String message, String idempotentKey) {
        super();
        initFields(CoreExceptionCode.IDEMPOTENT_REJECT.getCode(),
                CoreExceptionCode.IDEMPOTENT_REJECT.getKey(), new Object[]{idempotentKey});
        setHttpStatus(CoreExceptionCode.IDEMPOTENT_REJECT.getHttpStatus());
        setLevel(ExceptionLevel.WARN);
        setCategory(ExceptionCategory.BUSINESS);
        // 使用 setMessage 确保 @Idempotent.message() 用户可读文案优先展示
        setMessage(message);
    }
}
