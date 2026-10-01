package com.njydsz.nextwiki.server.channel;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.netty.codec.LengthFieldCodec;
import com.njydsz.common.netty.config.NettyProperties;
import com.njydsz.common.netty.handler.AbstractJsonTcpHandler;
import com.njydsz.common.netty.server.AbstractNettyServer;
/**
 * NextWiki TCP 推送通道（基于 common-netty，P1-2 Netty 推送能力扩展）。
 *
 * <p>为 NextWiki 协作编辑、文件锁定、实时通知等场景提供 TCP 长连接推送能力。 协议格式与 message 模块的 TcpPushChannel 保持一致：{@code Length(4B) + Payload(JSON)}。
 *
 * <p><b>协议格式：</b>
 *
 * <pre>
 * 客户端 → 服务端 AUTH：
 *   {"type":"AUTH","userId":"u001","token":"xxx"}
 *
 * 服务端 → 客户端 AUTH_ACK：
 *   {"type":"AUTH_ACK","success":true,"message":"ok"}
 *
 * 客户端 → 服务端 SUB_SPACE（订阅空间）：
 *   {"type":"SUB_SPACE","spaceId":"space-001"}
 *
 * 客户端 → 服务端 UNSUB_SPACE（取消订阅）：
 *   {"type":"UNSUB_SPACE","spaceId":"space-001"}
 *
 * 服务端 → 客户端 COLLAB_EVENT（协作事件推送）：
 *   {"type":"COLLAB_EVENT","spaceId":"space-001","fileId":"file-001","eventType":"CONTENT_CHANGE","payload":{...}}
 * </pre>
 *
 * <p><b>空间维度推送：</b>客户端通过 {@code SUB_SPACE} 订阅空间后，服务端通过 {@link #broadcastToSpace} 向该空间所有订阅者推送协作事件，适用于多人协作编辑同一文档的场景。
 *
 * <p><b>使用方式：</b>在协作编辑业务服务中注入本 Bean，调用 {@link #broadcastToSpace} 或 {@link #sendToUser} 推送事件。
 *
 * <p>配置项 {@code ydsz.nextwiki.tcp-push.enabled=true} 启用，端口通过 {@code ydsz.nextwiki.tcp-push.port} 配置（默认 9124）。
 *
 * @author ydsz-team
 * @since 26.09.06
 * @see AbstractNettyServer
 * @see LengthFieldCodec
 */
@Slf4j
@Component
@ConditionalOnClass(AbstractNettyServer.class)
@ConditionalOnProperty(prefix = "ydsz.nextwiki.tcp-push", name = "enabled", havingValue = "true")
public class NextwikiTcpPushChannel extends AbstractNettyServer {

  /** Map 初始容量 */
  private static final int MAP_CAPACITY_8 = 8;

  /** Map 初始容量（小） */
  private static final int MAP_CAPACITY_4 = 4;

  /** TCP 推送端口（默认 9124，避免与 message 模块的 9123 冲突） */
  private static final int DEFAULT_PUSH_PORT = 9124;

  /** 通道类型标识 */
  private static final String CHANNEL_TYPE = "NEXTWIKI_PUSH";

  /** 用户分组前缀 */
  private static final String USER_GROUP_PREFIX = "user:";

  /** 空间分组前缀 */
  private static final String SPACE_GROUP_PREFIX = "space:";

  /** TCP 推送服务端口 */
  private final int pushPort;

  /**
   * 构造 NextWiki TCP 推送通道。
   *
   * @param properties Netty 配置属性
   */
  public NextwikiTcpPushChannel(NettyProperties properties) {
    super(DEFAULT_PUSH_PORT, properties);
    this.pushPort = DEFAULT_PUSH_PORT;
  }

  /**
   * 返回通道类型标识。
   *
   * @return 通道类型
   */
  public String channelType() {
    return CHANNEL_TYPE;
  }

  /**
   * 初始化通道管线。
   *
   * <p>使用 {@link LengthFieldCodec} 一站式添加 Length(4B) + Payload 编解码器， 并添加协作事件业务 Handler。
   *
   * @param ch SocketChannel
   */
  @Override
  protected void initChannelPipeline(SocketChannel ch) {
    LengthFieldCodec.addToPipeline(ch.pipeline());
    ch.pipeline().addLast(new NextwikiPushServerHandler(this));
  }

  /**
   * 向指定空间的所有订阅者广播协作事件。
   *
   * <p>典型调用场景：用户 A 修改文档内容 → 后端处理 → 调用本方法广播给空间内其他在线的协作者。
   *
   * @param spaceId 空间 ID（不能为空）
   * @param fileId 文件 ID
   * @param eventType 事件类型（如 CONTENT_CHANGE / CURSOR_MOVE / FILE_LOCK / FILE_UNLOCK）
   * @param payload 事件负载（业务自定义 JSON 对象，如文档变更内容、光标位置等）
   * @return 推送成功的订阅者数量，空间无订阅者时返回 0
   */
  public int broadcastToSpace(
      String spaceId, String fileId, String eventType, Object payload) {
    if (spaceId == null || spaceId.isBlank()) {
      return 0;
    }
    String groupKey = SPACE_GROUP_PREFIX + spaceId;
    if (channelGroupManager.groupSize(groupKey) == 0) {
      log.debug("[NextWiki-PUSH] 空间无订阅者: spaceId={}", spaceId);
      return 0;
    }
    try {
      Map<String, Object> eventData = new HashMap<>(MAP_CAPACITY_8);
      eventData.put("type", "COLLAB_EVENT");
      eventData.put("spaceId", spaceId);
      eventData.put("fileId", fileId);
      eventData.put("eventType", eventType);
      eventData.put("payload", payload);
      eventData.put("timestamp", System.currentTimeMillis());
      String json = YdszJson.toJson(eventData);
      ByteBuf buf = Unpooled.copiedBuffer(json, StandardCharsets.UTF_8);
      channelGroupManager.broadcastToGroup(groupKey, buf);
      int count = channelGroupManager.groupSize(groupKey);
      log.info(
          "[NextWiki-PUSH] 空间广播: spaceId={} fileId={} event={} receivers={}",
          spaceId, fileId, eventType, count);
      return count;
    } catch (Exception e) {
      log.error("[NextWiki-PUSH] 广播异常: spaceId={} err={}", spaceId, e.getMessage(), e);
      return 0;
    }
  }

  /**
   * 向指定用户定向推送通知。
   *
   * <p>典型调用场景：用户被 @提及、文件被锁定通知等。
   *
   * @param userId 目标用户 ID
   * @param eventType 事件类型
   * @param payload 事件负载
   * @return true 推送成功，false 用户不在线
   */
  public boolean sendToUser(String userId, String eventType, Object payload) {
    if (userId == null || userId.isBlank()) {
      return false;
    }
    String groupKey = USER_GROUP_PREFIX + userId;
    if (channelGroupManager.groupSize(groupKey) == 0) {
      return false;
    }
    try {
      Map<String, Object> eventData = new HashMap<>(MAP_CAPACITY_4);
      eventData.put("type", "USER_EVENT");
      eventData.put("eventType", eventType);
      eventData.put("payload", payload);
      eventData.put("timestamp", System.currentTimeMillis());
      String json = YdszJson.toJson(eventData);
      ByteBuf buf = Unpooled.copiedBuffer(json, StandardCharsets.UTF_8);
      channelGroupManager.broadcastToGroup(groupKey, buf);
      return true;
    } catch (Exception e) {
      log.error("[NextWiki-PUSH] 用户推送异常: userId={} err={}", userId, e.getMessage(), e);
      return false;
    }
  }

  /**
   * 注册用户连接（AUTH 成功后调用）。
   *
   * @param userId 用户 ID
   * @param channel Netty Channel
   */
  void registerUser(String userId, Channel channel) {
    channelGroupManager.addToGroup(USER_GROUP_PREFIX + userId, channel);
    log.debug("[NextWiki-PUSH] 用户注册: userId={} online={}", userId, channelGroupManager.globalSize());
  }

  /**
   * 订阅空间（SUB_SPACE 消息触发）。
   *
   * @param spaceId 空间 ID
   * @param channel Netty Channel
   */
  void subscribeSpace(String spaceId, Channel channel) {
    channelGroupManager.addToGroup(SPACE_GROUP_PREFIX + spaceId, channel);
    log.debug("[NextWiki-PUSH] 空间订阅: spaceId={}", spaceId);
  }

  /**
   * 取消订阅空间（UNSUB_SPACE 消息触发）。
   *
   * @param spaceId 空间 ID
   * @param channel Netty Channel
   */
  void unsubscribeSpace(String spaceId, Channel channel) {
    channelGroupManager.removeFromGroup(SPACE_GROUP_PREFIX + spaceId, channel);
    log.debug("[NextWiki-PUSH] 空间取消订阅: spaceId={}", spaceId);
  }

  /**
   * 获取在线用户数。
   *
   * @return 在线用户数（全局活跃 Channel 数）
   */
  public int getOnlineCount() {
    return channelGroupManager.globalSize();
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
   * NextWiki TCP 推送服务端 Handler（基于 {@link AbstractJsonTcpHandler} 基类）。
   *
   * <p>封装 ByteBuf → UTF-8 → JSON 解析、空闲超时关闭、异常关闭等通用逻辑。 子类实现认证注册、空间订阅/取消订阅等业务消息路由。
   */
  static class NextwikiPushServerHandler extends AbstractJsonTcpHandler {

    private final NextwikiTcpPushChannel server;
    private String userId;

    NextwikiPushServerHandler(NextwikiTcpPushChannel server) {
      this.server = server;
    }

    @Override
    protected String getLogPrefix() {
      return "[NextWiki-PUSH]";
    }

    @Override
    protected void onJsonMessage(ChannelHandlerContext ctx, Map<String, Object> message) {
      String type = (String) message.get("type");
      if (AbstractJsonTcpHandler.TYPE_AUTH.equals(type)) {
        handleAuth(ctx, message);
      } else if ("SUB_SPACE".equals(type)) {
        handleSubSpace(ctx, message);
      } else if ("UNSUB_SPACE".equals(type)) {
        handleUnsubSpace(ctx, message);
      } else {
        log.debug("[NextWiki-PUSH] 未知消息类型: type={}", type);
      }
    }

    @Override
    protected void onAuthenticated(ChannelHandlerContext ctx, String userId) {
      this.userId = userId;
      server.registerUser(userId, ctx.channel());
      // 激活 Session 管理：更新 bizId 使 sessionRepository 可查询到此会话
      server.getSessionRepository().find(s -> s.getChannel().equals(ctx.channel()))
          .forEach(s -> server.getSessionRepository().updateBizId(s.getSessionId(), userId));
    }

    /**
     * 处理 SUB_SPACE 空间订阅消息。
     *
     * <p>仅当用户已认证（userId 非空）后才允许订阅。
     *
     * @param ctx  Channel 上下文
     * @param data 消息数据
     */
    private void handleSubSpace(ChannelHandlerContext ctx, Map<String, Object> data) {
      if (userId == null) {
        log.warn("[NextWiki-PUSH] 未认证用户尝试订阅空间");
        return;
      }
      String spaceId = (String) data.get("spaceId");
      if (spaceId != null && !spaceId.isBlank()) {
        server.subscribeSpace(spaceId, ctx.channel());
      }
    }

    /**
     * 处理 UNSUB_SPACE 空间取消订阅消息。
     *
     * @param ctx  Channel 上下文
     * @param data 消息数据
     */
    private void handleUnsubSpace(ChannelHandlerContext ctx, Map<String, Object> data) {
      String spaceId = (String) data.get("spaceId");
      if (spaceId != null && !spaceId.isBlank()) {
        server.unsubscribeSpace(spaceId, ctx.channel());
      }
    }
  }
}
