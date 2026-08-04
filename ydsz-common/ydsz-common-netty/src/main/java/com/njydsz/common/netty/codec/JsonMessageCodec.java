package com.njydsz.common.netty.codec;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.njydsz.common.json.YdszJson;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON 消息编解码器 — 基于 {@link YdszJson} 引擎实现消息序列化/反序列化。
 *
 * <p>组合 {@link MessageEncoder} 和 {@link MessageDecoder} 接口，
 * 将业务对象与 ByteBuf 之间进行 JSON 转换。
 *
 * <p>使用方式：
 * <pre>{@code
 * // 在 Pipeline 中添加
 * ch.pipeline().addLast(new JsonMessageCodec<MyMessage>(MyMessage.class));
 * }</pre>
 *
 * <p>需配合 {@link LengthFieldFrameDecoder} 或 {@link LengthFieldCodec} 使用，
 * 确保 channelRead 收到的是完整的帧 ByteBuf。
 *
 * @param <T> 业务消息类型
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ChannelHandler.Sharable
public class JsonMessageCodec<T> extends MessageToByteEncoder<T> {

    private final Class<T> messageClass;

    /**
     * 构造 JSON 消息编解码器。
     *
     * @param messageClass 消息类型
     */
    public JsonMessageCodec(Class<T> messageClass) {
        this.messageClass = messageClass;
    }

    /**
     * 编码：将业务对象序列化为 JSON 字节流。
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, T msg, ByteBuf out) throws Exception {
        String json = YdszJson.toJson(msg);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        out.writeBytes(bytes);
    }

    /**
     * 解码：将 ByteBuf 反序列化为业务对象。
     *
     * <p>此方法为静态工具方法，供业务 Handler 在 channelRead 中调用。
     *
     * @param buf ByteBuf
     * @return 业务对象
     */
    public T decode(ByteBuf buf) {
        String json = buf.toString(StandardCharsets.UTF_8);
        return YdszJson.fromJson(json, messageClass);
    }

    /**
     * 解码：将字节数组反序列化为业务对象。
     *
     * @param bytes 字节数组
     * @return 业务对象
     */
    public T decode(byte[] bytes) {
        return YdszJson.fromJsonBytes(bytes, messageClass);
    }

    /**
     * 创建入站解码 Handler（将 ByteBuf 自动解码为业务对象）。
     *
     * @return MessageToMessageDecoder
     */
    public MessageToMessageDecoder<ByteBuf> createDecoder() {
        return new MessageToMessageDecoder<>() {
            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
                T message = JsonMessageCodec.this.decode(msg);
                out.add(message);
            }
        };
    }
}
