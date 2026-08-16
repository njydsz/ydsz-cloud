package com.njydsz.message.domain.dto.core;

import com.njydsz.common.safe.annotation.Xss;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 用户触达画像 DTO。
 *
 * <p>P1-8: 描述用户在不同通道的触达偏好和活跃度，用于智能选择最优通道。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserReachProfileDTO {

  /** 用户 ID */
  @Xss private String userId;

  /** 通道活跃度评分（0-100，越高越活跃），key=通道类型 */
  private Map<String, Integer> channelActivityScores;

  /** 通道偏好优先级（如 ["SMS","PUSH","EMAIL"]，按优先级降序） */
  private List<String> channelPreferences;

  /** 最近活跃时间（ISO 格式字符串） */
  @Xss private String lastActiveAt;

  /** 设备类型（IOS/ANDROID/WEB/UNKNOWN） */
  @Xss private String deviceType;

  /** 时区（如 Asia/Shanghai） */
  @Xss private String timezone;

  /** 免打扰开始时间（HH:mm） */
  @Xss private String dndStart;

  /** 免打扰结束时间（HH:mm） */
  @Xss private String dndEnd;

  /** 历史消息打开率（0.0-1.0） */
  private Double openRate;

  /** 历史消息点击率（0.0-1.0） */
  private Double clickRate;
}
