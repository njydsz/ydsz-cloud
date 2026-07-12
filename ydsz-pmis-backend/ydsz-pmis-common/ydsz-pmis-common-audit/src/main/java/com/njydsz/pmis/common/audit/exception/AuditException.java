package com.njydsz.pmis.common.audit.exception;

/**
 * 审计模块统一异常
 * <p>
 * 审计日志的产生、记录、查询、存储等任意环节失败时抛出。
 * 携带 {@code component}（审计组件名称）和 {@code errorCode}（错误码）便于问题定位。
 * </p>
 *
 * <p>该异常通常发生在以下场景：</p>
 * <ul>
 *   <li>数据库写入/查询失败（连接异常、SQL 语法错等）</li>
 *   <li>异步落盘队列异常</li>
 *   <li>审计切面 SpEL 解析失败</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
public class AuditException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 审计组件名称（如 {@code AuditAspect}、{@code JdbcAuditStorage} 等）
     */
    private final String component;

    /**
     * 错误码（业务可识别的稳定错误码，优先于 message 国际化）
     */
    private final String errorCode;

    /**
     * 使用错误消息构造审计异常
     *
     * @param message 错误消息
     */
    public AuditException(String message) {
        super(message);
        this.component = null;
        this.errorCode = null;
    }

    /**
     * 使用错误消息和组件名称构造审计异常
     *
     * @param message   错误消息
     * @param component 审计组件名称
     */
    public AuditException(String message, String component) {
        super(message);
        this.component = component;
        this.errorCode = null;
    }

    /**
     * 使用错误消息、组件名称和错误码构造审计异常
     *
     * @param message   错误消息
     * @param component 审计组件名称
     * @param errorCode 错误码
     */
    public AuditException(String message, String component, String errorCode) {
        super(message);
        this.component = component;
        this.errorCode = errorCode;
    }

    /**
     * 使用错误消息和原因构造审计异常
     *
     * @param message 错误消息
     * @param cause   导致异常的原因
     */
    public AuditException(String message, Throwable cause) {
        super(message, cause);
        this.component = null;
        this.errorCode = null;
    }

    /**
     * 使用错误消息、组件名称和原因构造审计异常
     *
     * @param message   错误消息
     * @param component 审计组件名称
     * @param cause     导致异常的原因
     */
    public AuditException(String message, String component, Throwable cause) {
        super(message, cause);
        this.component = component;
        this.errorCode = null;
    }

    /**
     * 使用错误消息、组件名称、错误码和原因构造审计异常
     *
     * @param message   错误消息
     * @param component 审计组件名称
     * @param errorCode 错误码
     * @param cause     导致异常的原因
     */
    public AuditException(String message, String component, String errorCode, Throwable cause) {
        super(message, cause);
        this.component = component;
        this.errorCode = errorCode;
    }

    /**
     * 获取审计组件名称
     *
     * @return 组件名称
     */
    public String getComponent() {
        return component;
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
