package com.njydsz.pmis.common.domain.exception;

/**
 * 领域异常基类
 *
 * <p>所有领域层抛出的业务异常都应继承此类。领域异常表示业务规则的违反，
 * 区别于系统异常（如 NullPointerException、IOException 等）。
 *
 * <p><b>异常分类：</b>
 * <ul>
 *   <li><b>领域异常（DomainException）</b>：业务规则违反，可预期，需向用户展示友好信息</li>
 *   <li><b>系统异常（RuntimeException）</b>：系统级错误，不可预期，需记录日志并返回通用错误</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class OrderDomainService {
 *
 *     public void cancel(Order order) {
 *         if (order.getStatus() == OrderStatus.COMPLETED) {
 *             throw new DomainException("已完成的订单不能取消");
 *         }
 *         if (order.getStatus() == OrderStatus.CANCELLED) {
 *             throw new DomainException("ORDER_ALREADY_CANCELLED", "订单已取消，请勿重复操作");
 *         }
 *         order.setStatus(OrderStatus.CANCELLED);
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 *
 * @see AggregateNotFoundException
 * @see ConcurrencyConflictException
 */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 默认错误码
     */
    public static final String DEFAULT_ERROR_CODE = "DOMAIN_ERROR";

    /**
     * 错误码，用于前端定位具体错误类型
     */
    private final String errorCode;

    /**
     * 构造领域异常
     *
     * @param message 错误信息
     */
    public DomainException(String message) {
        super(message);
        this.errorCode = DEFAULT_ERROR_CODE;
    }

    /**
     * 构造领域异常（指定错误码）
     *
     * @param errorCode 错误码
     * @param message   错误信息
     */
    public DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造领域异常（带原因）
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    public DomainException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = DEFAULT_ERROR_CODE;
    }

    /**
     * 构造领域异常（指定错误码和原因）
     *
     * @param errorCode 错误码
     * @param message   错误信息
     * @param cause     原始异常
     */
    public DomainException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
    }
}
