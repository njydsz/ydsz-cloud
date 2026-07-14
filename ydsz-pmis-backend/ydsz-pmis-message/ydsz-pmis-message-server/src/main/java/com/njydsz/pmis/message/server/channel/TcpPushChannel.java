package com.njydsz.pmis.message.server.channel.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.netty.codec.LengthFieldFrameDecoder;
import com.njydsz.pmis.common.netty.config.NettyProperties;
import com.njydsz.pmis.common.netty.server.AbstractNettyServer;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.message.server.channel.MessageChannel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * TCP 推送通道（基于 common-netty）。
 *
 * <p>P0-5: 引入 common-netty 的 {@link AbstractNettyServer} 作为 TCP 长连接推送通道。
 * 适用于移动端、IoT 设备等不适用 WebSocket 的场景。
 *
 * <p>协议格式：Length(4B) + Payload(JSON)
 * <ul>
 *   <li>粘包/半包：通过 {@link LengthFieldFrameDecoder} 解决</li>
 *   <li>消息编码：JSON（UTF-8）</li>
 *   <li>连接管理：通过 {@code channelIdMap} 维护 userId → Channel 映射</li>
 *   <li>推送方式：单推（按 userId 查找 Channel）+ 广播（通过 ChannelGroupManager）</li>
 * </ul>
 *
 * <p>配置项 {@code pmis.message.tcp-push.enabled=true} 启用，
 * 端口通过 {@code pmis.message.tcp-push.port} 配置（默认 9123）。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Component
@ConditionalOnClass(AbstractNettyServer.class)
@ConditionalOnProperty(prefix = "pmis.message.tcp-push", name = "enabled", havingValue = "true")
public class TcpPushChannel extends AbstractNettyServer implements MessageChannel {

    /** 通道类型 */
    private static final String CHANNEL_TYPE = "PUSH";

    /** JSON 序列化器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** userId → ChannelHandlerContext 映射（用于定向推送） */
    private final Map<String, ChannelHandlerContext> userChannelMap = new ConcurrentHashMap<>();

    /** TCP 推送服务端口 */
    private final int pushPort;

    /**
     * 构造 TCP 推送通道。
     *
     * @param properties Netty 配置
     */
    public TcpPushChannel(NettyProperties properties) {
        super(9123, properties);
        this.pushPort = 9123;
    }

    @Override
    public String channelType() {
        return CHANNEL_TYPE;
    }

    @Override
    protected void initChannelPipeline(SocketChannel ch) {
        ch.pipeline()
                // 粘包/半包解码：Length(4B) + Payload
                .addLast(new LengthFieldFrameDecoder())
                // 编码：自动添加 4B 长度前缀
                .addLast(new LengthFieldPrepender(4))
                // 业务 Handler
                .addLast(new TcpPushServerHandler(this));
    }

    @Override
    public MessageResult send(MessageRequest request) {
        if (request.getReceiver() == null || request.getReceiver().isBlank()) {
            return MessageResult.fail(CHANNEL_TYPE, "推送接收人不能为空");
        }
        String traceId = "PUSH-" + SnowflakeIdGenerator.nextTraceId();
        String userId = request.getReceiver();
        ChannelHandlerContext ctx = userChannelMap.get(userId);
        if (ctx == null || !ctx.channel().isActive()) {
            log.warn("[TCP-PUSH] 用户不在线,无法推送: userId={}", userId);
            return MessageResult.fail(CHANNEL_TYPE, "用户不在线: " + userId);
        }
        try {
            // 构建推送消息 JSON
            Map<String, Object> pushData = new HashMap<>(8);
            pushData.put("type", "PUSH");
            pushData.put("messageId", request.getMessageId());
            pushData.put("subject", request.getSubject());
            pushData.put("content", request.getContent());
            pushData.put("bizType", request.getBizType());
            pushData.put("bizId", request.getBizId());
            pushData.put("traceId", traceId);
            pushData.put("timestamp", System.currentTimeMillis());
            String json = OBJECT_MAPPER.writeValueAsString(pushData);
            ByteBuf buf = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8);
            ctx.writeAndFlush(buf);
            log.info("[TCP-PUSH] 推送成功: userId={} traceId={} subject={}",
                    userId, traceId, request.getSubject());
            return MessageResult.ok(CHANNEL_TYPE, traceId);
        } catch (Exception e) {
            log.error("[TCP-PUSH] 推送异常: userId={} err={}", userId, e.getMessage());
            return MessageResult.fail(CHANNEL_TYPE, "推送异常: " + e.getMessage());
        }
    }

    /**
     * 注册用户连接。
     *
     * @param userId 用户 ID
     * @param ctx    Channel 上下文
     */
    void registerUser(String userId, ChannelHandlerContext ctx) {
        userChannelMap.put(userId, ctx);
        channelGroupManager.addToGroup("user:" + userId, ctx.channel());
        log.info("[TCP-PUSH] 用户连接注册: userId={} channelId={} online={}",
                userId, ctx.channel().id(), userChannelMap.size());
    }

    /**
     * 注销用户连接。
     *
     * @param userId 用户 ID
     */
    void unregisterUser(String userId) {
        ChannelHandlerContext ctx = userChannelMap.remove(userId);
        if (ctx != null) {
            channelGroupManager.remove(ctx.channel());
            log.info("[TCP-PUSH] 用户连接注销: userId={} online={}", userId, userChannelMap.size());
        }
    }

    /**
     * 获取在线用户数。
     *
     * @return 在线用户数
     */
    public int getOnlineCount() {
        return userChannelMap.size();
    }

    /**
     * 获取推送端口。
     *
     * @return 端口号
     */
    public int getPushPort() {
        return pushPort;
    }

    /**
     * TCP 推送服务端 Handler。
     *
     * <p>处理客户端连接/断开、心跳保活、认证消息。
     */
    @Slf4j
    static class TcpPushServerHandler extends ChannelInboundHandlerAdapter {

        private final TcpPushChannel server;
        private String userId;

        TcpPushServerHandler(TcpPushChannel server) {
            this.server = server;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            server.channelGroupManager.add(ctx.channel());
            log.debug("[TCP-PUSH] 新连接: remote={}", ctx.channel().remoteAddress());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            server.channelGroupManager.remove(ctx.channel());
            if (userId != null) {
                server.unregisterUser(userId);
            }
            log.debug("[TCP-PUSH] 连接断开: remote={}", ctx.channel().remoteAddress());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf buf)) {
                return;
            }
            String json = buf.toString(CharsetUtil.UTF_8);
            try {
                Map<String, Object> data = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
                String type = (String) data.get("type");
                if ("AUTH".equals(type)) {
                    // 认证消息：注册 userId
                    this.userId = (String) data.get("userId");
                    if (userId != null) {
                        server.registerUser(userId, ctx);
                        // 回复认证成功
                        Map<String, Object> ack = new HashMap<>(2);
                        ack.put("type", "AUTH_ACK");
                        ack.put("success", true);
                        ctx.writeAndFlush(Unpooled.copiedBuffer(
                                OBJECT_MAPPER.writeValueAsString(ack), CharsetUtil.UTF_8));
                    }
                } else if ("PING".equals(type)) {
                    // 心跳响应
                    Map<String, Object> pong = new HashMap<>(2);
                    pong.put("type", "PONG");
                    pong.put("timestamp", System.currentTimeMillis());
                    ctx.writeAndFlush(Unpooled.copiedBuffer(
                            OBJECT_MAPPER.writeValueAsString(pong), CharsetUtil.UTF_8));
                }
            } catch (Exception e) {
                log.warn("[TCP-PUSH] 消息解析失败: {}", e.getMessage());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("[TCP-PUSH] 连接异常: remote={} err={}",
                    ctx.channel().remoteAddress(), cause.getMessage());
            ctx.close();
        }
    }
}
