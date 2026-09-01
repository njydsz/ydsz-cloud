package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;

import com.njydsz.common.json.YdszJson;

/**
 * 催办通道配置值对象。
 *
 * <p>借鉴 Flowlong 的多路催办通道能力，允许在 SLA 配置 / 流程定义 / 节点级别配置催办通道。
 * 支持 INAPP（站内信）/ EMAIL（邮件）/ WEBHOOK（机器人）/ SMS（短信）等多通道。
 *
 * <p><b>ext JSON 配置：</b>
 *
 * <ul>
 *   <li>{@code urgeChannels}：催办通道列表（JSON 数组，默认 ["INAPP"]）
 *   <li>{@code urgeIntervalMinutes}：催办间隔分钟数（默认 30）
 *   <li>{@code urgeMaxCount}：最大催办次数（默认 3）
 *   <li>{@code urgeEnabled}：是否启用催办通知（默认 true）
 * </ul>
 *
 * <p><b>架构合规说明（1.0.0 DDD 分层规范）：</b>值对象置于 {@code domain/vo/} 包下，
 * 以 {@code Config} 结尾，不可变对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@ToString
public class UrgeChannelConfigVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 默认催办通道 */
  private static final List<String> DEFAULT_URGE_CHANNELS = List.of("INAPP");

  /** 默认催办间隔（分钟） */
  public static final int DEFAULT_URGE_INTERVAL_MINUTES = 30;

  /** 默认最大催办次数 */
  public static final int DEFAULT_URGE_MAX_COUNT = 3;

  /** 催办通道列表 */
  private final List<String> urgeChannels;

  /** 催办间隔分钟数 */
  private final int urgeIntervalMinutes;

  /** 最大催办次数 */
  private final int urgeMaxCount;

  /** 是否启用催办通知 */
  private final boolean urgeEnabled;

  /**
   * 支持的催办通道常量。
   */
  public static final class Channel {
    /** 站内信 */
    public static final String INAPP = "INAPP";
    /** 邮件 */
    public static final String EMAIL = "EMAIL";
    /** Webhook 机器人 */
    public static final String WEBHOOK = "WEBHOOK";
    /** 短信 */
    public static final String SMS = "SMS";
    /** 企业微信 */
    public static final String WECOM = "WECOM";
    /** 钉钉 */
    public static final String DINGTALK = "DINGTALK";
    /** 飞书 */
    public static final String FEISHU = "FEISHU";

    private Channel() {
      throw new UnsupportedOperationException("常量类不可实例化");
    }
  }

  private UrgeChannelConfig(List<String> urgeChannels, int urgeIntervalMinutes, int urgeMaxCount,
      boolean urgeEnabled) {
    this.urgeChannels = urgeChannels != null && !urgeChannels.isEmpty() ? urgeChannels
        : DEFAULT_URGE_CHANNELS;
    this.urgeIntervalMinutes =
        urgeIntervalMinutes > 0 ? urgeIntervalMinutes : DEFAULT_URGE_INTERVAL_MINUTES;
    this.urgeMaxCount = urgeMaxCount > 0 ? urgeMaxCount : DEFAULT_URGE_MAX_COUNT;
    this.urgeEnabled = urgeEnabled;
  }

  /**
   * 从 ext JSON Map 解析催办通道配置。
   *
   * @param extMap 节点 ext JSON 解析后的 Map，不可为 null
   * @return 催办通道配置值对象（不可变）
   */
  public static UrgeChannelConfig fromExt(Map<String, Object> extMap) {
    if (extMap == null || extMap.isEmpty()) {
      return new UrgeChannelConfig(DEFAULT_URGE_CHANNELS, DEFAULT_URGE_INTERVAL_MINUTES,
          DEFAULT_URGE_MAX_COUNT, true);
    }
    List<String> channels = parseChannels(extMap.get("urgeChannels"));
    int interval = parseIntSafe(extMap.get("urgeIntervalMinutes"), DEFAULT_URGE_INTERVAL_MINUTES);
    int maxCount = parseIntSafe(extMap.get("urgeMaxCount"), DEFAULT_URGE_MAX_COUNT);
    boolean enabled = parseBooleanSafe(extMap.get("urgeEnabled"), true);
    return new UrgeChannelConfig(channels, interval, maxCount, enabled);
  }

  /**
   * 从 ext JSON 字符串解析催办通道配置。
   *
   * @param extJson ext JSON 字符串，可为 null 或空
   * @return 催办通道配置值对象（不可变）
   */
  public static UrgeChannelConfig fromExtJson(String extJson) {
    if (extJson == null || extJson.isBlank()) {
      return new UrgeChannelConfig(DEFAULT_URGE_CHANNELS, DEFAULT_URGE_INTERVAL_MINUTES,
          DEFAULT_URGE_MAX_COUNT, true);
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(extJson);
      return fromExt(map);
    } catch (Exception e) {
      return new UrgeChannelConfig(DEFAULT_URGE_CHANNELS, DEFAULT_URGE_INTERVAL_MINUTES,
          DEFAULT_URGE_MAX_COUNT, true);
    }
  }

  /**
   * 获取有效的催办通道列表（过滤掉无效通道）。
   *
   * @return 有效通道列表
   */
  public List<String> getValidChannels() {
    List<String> valid = List.of(Channel.INAPP, Channel.EMAIL, Channel.WEBHOOK, Channel.SMS,
        Channel.WECOM, Channel.DINGTALK, Channel.FEISHU);
    List<String> result = new ArrayList<>();
    for (String ch : urgeChannels) {
      if (valid.contains(ch)) {
        result.add(ch);
      }
    }
    return result.isEmpty() ? DEFAULT_URGE_CHANNELS : result;
  }

  // ==================== 内部工具方法 ====================

  @SuppressWarnings("unchecked")
  private static List<String> parseChannels(Object value) {
    if (value == null) {
      return DEFAULT_URGE_CHANNELS;
    }
    if (value instanceof List<?> list) {
      return list.stream().map(Object::toString).toList();
    }
    return DEFAULT_URGE_CHANNELS;
  }

  private static int parseIntSafe(Object value, int defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value).trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static boolean parseBooleanSafe(Object value, boolean defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }
}
