package com.njydsz.common.jdbc.exception;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCode;

/**
 * JDBC 模块业务异常基类
 *
 * <p>所有 JDBC 模块抛出的异常均应继承本类，以获得：
 * <ul>
 *   <li>统一的错误码体系（{@link JdbcExceptionCode}）</li>
 *   <li>语义化的错误消息（通过 i18n 消息键）</li>
 *   <li>正确的 HTTP 状态码映射（503 Service Unavailable 等）</li>
 *   <li>异常分类标记（DATABASE 类别，便于监控告警）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 简单抛出
 * throw new JdbcException(JdbcExceptionCode.DATASOURCE_UNAVAILABLE);
 *
 * // 带原始异常
 * throw new JdbcException(JdbcExceptionCode.SQL_PARSE_FAILED, cause);
 *
 * // 带动态参数
 * throw new JdbcException(JdbcExceptionCode.SLAVE_LATENCY_EXCEEDED, cause)
 *     .data("slave", slaveName)
 *     .data("latency", "5s");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.8.0
 * @see JdbcExceptionCode
 * @see BusinessException
 */
public class JdbcException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * 使用 JDBC 异常码构造异常
     *
     * @param exceptionCode JDBC 异常码
     */
    public JdbcException(JdbcExceptionCode exceptionCode) {
        super(exceptionCode);
    }

    /**
     * 使用 JDBC 异常码和原始异常构造异常
     *
     * @param exceptionCode JDBC 异常码
     * @param cause         原始异常
     */
    public JdbcException(JdbcExceptionCode exceptionCode, Throwable cause) {
        super(exceptionCode, cause);
    }

    /**
     * 使用 JDBC 异常码和自定义消息构造异常
     *
     * <p>保留用于需要补充动态上下文（如具体表名、SQL 片段）的场景。
     *
     * @param exceptionCode JDBC 异常码
     * @param message       自定义消息
     */
    public JdbcException(JdbcExceptionCode exceptionCode, String message) {
        super(exceptionCode);
        initMessage(message);
    }

    /**
     * 使用通用异常码和自定义消息构造异常
     *
     * <p>保留用于需要携带通用异常码（如安全模块异常码）并补充自定义消息的场景。
     *
     * @param exceptionCode 通用异常码
     * @param message       自定义消息
     */
    protected JdbcException(ExceptionCode exceptionCode, String message) {
        super(exceptionCode);
        initMessage(message);
    }

    /**
     * 使用通用异常码和原始异常构造异常
     *
     * @param exceptionCode 通用异常码
     * @param cause         原始异常
     */
    protected JdbcException(ExceptionCode exceptionCode, Throwable cause) {
        super(exceptionCode, cause);
    }
}
