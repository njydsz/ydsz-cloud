package com.njydsz.common.socket.cluster;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 集群广播消息（Redis Pub/Sub 载荷）。
 *
 * <p>多实例部署下，推送指令封装为本对象后序列化为 JSON 发布到 Redis Channel，所有实例订阅后 各自推送到本地 WebSocket session，从而实现跨节点广播。
 *
 * <p>注意：本对象通过 {@code YdszJson} 进行 JSON 序列化后传输， 不依赖 Java 原生序列化，因此未实现 {@link java.io.Serializable}
 * 接口。
 *
 * <p>推送类型：
 *
 * <ul>
 *   <li>{@code USER}：推送到指定用户的个人频道 {@code /topic/user/{userId}/notifications}
 *   <li>{@code BROADCAST}：推送到广播频道 {@code /topic/broadcast}
 *   <li>{@code TOPIC}：推送到指定主题 {@code /topic/{topic}}
 *   <li>{@code KICK}：踢出指定用户在本节点的所有 Session（P2-8 多端策略集群同步）
 * </ul>
 *
 * <p><b>协议版本（ARCH-006）：</b> {@code protocolVersion} 字段标识消息 envelope 版本号（如 {@code "1.0"}），
 * 兼容期新旧版本节点可能同时在线；接收端发现版本不匹配时按 {@link #isCompatibleWithCurrent()} 判定降级路径
 * （当前仅判定 major 版本号，兼容同大版本下的字段新增）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketClusterMessage {

  /** 当前协议版本（ARCH-006）。 */
  public static final String CURRENT_PROTOCOL_VERSION = "1.0";

  /** 推送类型：USER / BROADCAST / TOPIC */
  private String pushType;

  /** 目标用户 ID（pushType=USER 时使用） */
  private String userId;

  /** 目标主题（pushType=TOPIC 时使用） */
  private String topic;

  /** 消息类型标签（如 NOTIFICATION / ALERT / DASHBOARD） */
  private String type;

  /** 消息内容（JSON 字符串，由推送端序列化） */
  private String payloadJson;

  /** 链路追踪 ID（P1-1） */
  private String traceId;

  /** 灰度标签（P3-3），客户端匹配 tags 后才推送 */
  private List<String> tags;

  /** 消息优先级（P1-4）：URGENT / HIGH / NORMAL / LOW */
  private String priority;

  /** 踢出原因（pushType=KICK 时使用）：MULTI_DEVICE_POLICY / USER_LOGOUT 等 */
  private String kickReason;

  /** 协议版本（ARCH-006）。 {@code null} 视为遗留 0.x 消息，兼容期内按 1.0 处理。 */
  private String protocolVersion;

  /**
   * 构造用户推送消息。
   *
   * @param userId 用户 ID
   * @param type 消息类型标签
   * @param payloadJson 消息内容 JSON
   * @return 集群推送消息
   */
  public static WebSocketClusterMessage forUser(String userId, String type, String payloadJson) {
    return new WebSocketClusterMessage(
        "USER", userId, null, type, payloadJson, null, null, null, null, CURRENT_PROTOCOL_VERSION);
  }

  /**
   * 构造广播消息。
   *
   * @param type 消息类型标签
   * @param payloadJson 消息内容 JSON
   * @return 集群推送消息
   */
  public static WebSocketClusterMessage forBroadcast(String type, String payloadJson) {
    return new WebSocketClusterMessage(
        "BROADCAST", null, null, type, payloadJson, null, null, null, null, CURRENT_PROTOCOL_VERSION);
  }

  /**
   * 构造主题推送消息。
   *
   * @param topic 主题
   * @param type 消息类型标签（可为 null）
   * @param payloadJson 消息内容 JSON
   * @return 集群推送消息
   */
  public static WebSocketClusterMessage forTopic(String topic, String type, String payloadJson) {
    return new WebSocketClusterMessage(
        "TOPIC", null, topic, type, payloadJson, null, null, null, null, CURRENT_PROTOCOL_VERSION);
  }

  /**
   * 构造踢出消息（P2-8：多端策略集群同步）。
   *
   * <p>发布到集群后，各节点收到消息时踢出指定用户在本节点的所有 Session。
   *
   * @param userId 待踢出的用户 ID
   * @return 集群踢出消息
   */
  public static WebSocketClusterMessage forKick(String userId) {
    return new WebSocketClusterMessage(
        "KICK", userId, null, null, null, null, null, null, "MULTI_DEVICE_POLICY", CURRENT_PROTOCOL_VERSION);
  }

  /**
   * 判断当前消息是否与本节点协议版本兼容。
   *
   * <p>兼容规则：
   *
   * <ul>
   *   <li>{@code protocolVersion == null} → 按遗留 0.x 处理，视为兼容</li>
   *   <li>major 版本号相同（如 1.x 与 1.y） → 兼容（字段新增在末尾，旧端 JSON 反序列化缺失字段
   *       使用默认值）</li>
   *   <li>major 版本号不同 → 不兼容，调用方应丢弃或降级处理</li>
   * </ul>
   *
   * @return true 表示兼容，可正常处理
   */
  public boolean isCompatibleWithCurrent() {
    if (protocolVersion == null || protocolVersion.isEmpty()) {
      return true;
    }
    String localMajor = CURRENT_PROTOCOL_VERSION.split("\\.")[0];
    String incomingMajor = protocolVersion.split("\\.")[0];
    return localMajor.equals(incomingMajor);
  }

  /**
   * 是否在灰度发布白名单内（根据 tags 与当前节点灰度标签匹配）。
   *
   * <p>当消息未携带 tags 时视为"全量可见"，返回 true；tags 非空时要求当前节点灰度标签集合与之有交集。
   *
   * @param currentNodeTags 当前节点配置的灰度标签集合（由运维侧或配置中心下发），可为 null
   * @return true 表示该消息在本节点应被下发
   */
  public boolean matchesNodeTags(List<String> currentNodeTags) {
    if (tags == null || tags.isEmpty()) {
      return true;
    }
    if (currentNodeTags == null || currentNodeTags.isEmpty()) {
      return false;
    }
    for (String tag : tags) {
      if (currentNodeTags.contains(tag)) {
        return true;
      }
    }
    return false;
  }
}
