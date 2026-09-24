package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户画像配置属性。
 *
 * <p>绑定配置前缀 {@code ydzs.agent.profile}，控制用户画像领域服务的启用状态与交互阈值。
 * 当用户交互次数超过阈值时触发更积极的画像更新。默认不开启（isEnabled=false），
 * 交互阈值默认 5 次。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileProperties {
  private static final int DEFAULT_INTERACTION_THRESHOLD = 5;

  private boolean isEnabled = false;
  private int interactionThreshold = DEFAULT_INTERACTION_THRESHOLD;
}
