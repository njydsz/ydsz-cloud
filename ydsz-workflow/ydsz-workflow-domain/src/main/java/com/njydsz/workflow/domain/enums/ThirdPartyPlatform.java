package com.njydsz.workflow.domain.enums;

import lombok.Getter;

/**
 * 三方审批平台枚举
 *
 * <p>P0-2: 三方审批 SDK（钉钉/飞书/企微）平台标识，与 ydsz_flow_third_party_account.platform 对应。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
public enum ThirdPartyPlatform {

  /** 钉钉 */
  DINGTALK("钉钉"),

  /** 飞书 */
  FEISHU("飞书"),

  /** 企业微信 */
  WECOM("企业微信");

  /** 平台描述 */
  private final String description;

  ThirdPartyPlatform(String description) {
    this.description = description;
  }

  /**
   * 按名称（忽略大小写）解析平台
   *
   * @param name 平台名称字符串
   * @return 平台枚举，无法匹配时返回 null
   */
  public static ThirdPartyPlatform ofName(String name) {
    if (name == null || name.isEmpty()) {
      return null;
    }
    for (ThirdPartyPlatform platform : values()) {
      if (platform.name().equalsIgnoreCase(name)) {
        return platform;
      }
    }
    return null;
  }
}
