package com.njydsz.common.queue.serializer;

/**
 * 序列化异常
 *
 * <p>当消息序列化或反序列化失败时抛出此异常。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SerializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public SerializationException(Throwable cause) {
        super(cause);
    }
}
