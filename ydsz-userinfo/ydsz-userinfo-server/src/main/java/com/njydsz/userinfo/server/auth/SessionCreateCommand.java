package com.njydsz.userinfo.server.auth;

import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;

/**
 * 会话创建参数值对象。
 *
 * <p>封装创建用户会话所需的全部参数，避免方法参数数量超限（云顶编码规范 5.4 节）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @param accessToken 访问令牌
 * @param refreshToken 刷新令牌
 * @param user 用户账号
 * @param roleCodes 角色编码（逗号分隔）
 * @param roleNames 角色名称（逗号分隔）
 * @param deviceType 设备类型（用于分端会话限制）
 * @param loginIp 登录 IP（可为 null）
 * @param userAgent User-Agent 头（可为 null，超长自动截断）
 */
public record SessionCreateCommand(
    String accessToken,
    String refreshToken,
    UserAccountCredentialVO user,
    String roleCodes,
    String roleNames,
    DeviceType deviceType,
    String loginIp,
    String userAgent) {

  /** 是否携带设备详情。 */
  public boolean hasDeviceDetail() {
    return loginIp != null || userAgent != null;
  }
}
