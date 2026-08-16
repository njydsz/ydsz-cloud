package com.njydsz.common.netty.exception;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.SysException;

/**
 * Netty 模块统一异常。
 *
 * <p>封装 Netty Server/Client/SSL/Transport 各层的运行时错误，
 * 错误码固定为 {@link CoreExceptionCode#NETWORK_ERROR}（B01055），
 * 表示基础设施层网络故障。
 *
 * <p>场景区分通过 message 描述实现，避免异常类膨胀。
 * 如需区分 SSL 服务端/客户端上下文，可使用 {@link #ofSsl(String, String)} 工厂方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class NettyException extends SysException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造 Netty 统一异常。
     *
     * @param message 错误消息
     */
    public NettyException(String message) {
        super(CoreExceptionCode.NETWORK_ERROR);
        setMessage(message);
    }

    /**
     * 构造 Netty 统一异常。
     *
     * @param message 错误消息
     * @param cause   根因
     */
    public NettyException(String message, Throwable cause) {
        super(CoreExceptionCode.NETWORK_ERROR, cause);
        setMessage(message);
    }

    /**
     * 创建 SSL 上下文相关异常。
     *
     * @param contextType 上下文类型（server/client）
     * @param message     错误消息
     * @return NettyException 实例
     */
    public static NettyException ofSsl(String contextType, String message) {
        return new NettyException("SSL[" + contextType + "] " + message);
    }

    /**
     * 创建 SSL 上下文相关异常（带根因）。
     *
     * @param contextType 上下文类型（server/client）
     * @param message     错误消息
     * @param cause       根因
     * @return NettyException 实例
     */
    public static NettyException ofSsl(String contextType, String message, Throwable cause) {
        return new NettyException("SSL[" + contextType + "] " + message, cause);
    }
}
