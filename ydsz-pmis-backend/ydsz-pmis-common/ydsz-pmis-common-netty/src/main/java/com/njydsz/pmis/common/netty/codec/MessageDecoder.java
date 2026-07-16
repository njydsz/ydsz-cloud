package com.njydsz.pmis.common.netty.codec;

/**
 * 消息解码器接口 — 将 Netty 接收的字节流解码为业务对象。
 *
 * @param <T> 业务消息类型
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public interface MessageDecoder<T> {

    /**
     * 将字节数组解码为业务消息对象。
     *
     * @param bytes 字节数组
     * @return 解码后的消息对象
     * @throws Exception 解码异常
     */
    T decode(byte[] bytes) throws Exception;
}
