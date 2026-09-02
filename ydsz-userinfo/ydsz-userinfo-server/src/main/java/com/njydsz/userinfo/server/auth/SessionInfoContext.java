package com.njydsz.userinfo.server.auth;

import com.njydsz.userinfo.domain.enums.DeviceType;

/**
 * 会话 Hash 构建参数值对象。
 *
 * <p>封装构建会话 Hash 数据所需的全部字段，避免方法参数数量超限（云顶编码规范 5.4 节）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @param userId 用户 ID
 * @param username 用户名
 * @param roleCodes 角色编码（逗号分隔）
 * @param roleNames 角色名称（逗号分隔）
 * @param tenantId 租户 ID
 * @param refreshToken 刷新令牌
 * @param deviceType 设备类型
 * @param loginIp 登录 IP（可为 null）
 * @param userAgent User-Agent（可为 null，超长自动截断）
 */
public record SessionInfoContext(
    String userId,
    String username,
    String roleCodes,
    String roleNames,
    String tenantId,
    String refreshToken,
    DeviceType deviceType,
    String loginIp,
    String userAgent) {
}
