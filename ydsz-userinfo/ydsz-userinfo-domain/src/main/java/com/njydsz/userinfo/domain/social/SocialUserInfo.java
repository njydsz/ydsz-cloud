package com.njydsz.userinfo.domain.social;

import java.io.Serializable;

/**
 * 社交用户信息值对象。
 *
 * <p>封装从平台获取的用户基本信息，不可变。由 {@link SocialAuthProvider#getUserInfo} 返回，
 * 用于社交登录时的用户匹配与账号创建。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @param openId 平台用户唯一标识
 * @param unionId 平台统一应用标识（可为 null）
 * @param nickname 用户昵称（平台侧显示名，可为 null）
 * @param avatar 用户头像 URL（可为 null）
 * @param email 用户邮箱（可为 null，部分平台不返回）
 * @param platform 平台标识（如 WECHAT/DINGTALK/GITHUB）
 */
public record SocialUserInfo(
    String openId,
    String unionId,
    String nickname,
    String avatar,
    String email,
    String platform)
    implements Serializable {

  private static final long serialVersionUID = 1L;
}
