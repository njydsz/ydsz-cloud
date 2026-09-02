package com.njydsz.userinfo.server.auth;

/**
 * 登录风险评估参数值对象。
 *
 * <p>封装基础与地理围栏风险评估所需的全部输入，避免方法参数数量超限（云顶编码规范 5.4 节）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @param username 用户名
 * @param loginIp 登录 IP
 * @param userAgent 用户代理
 * @param recentFailCount 最近失败次数
 * @param isNewDevice 是否新设备
 * @param lastLoginIp 上次登录 IP（可为 null，仅地理围栏使用）
 */
public record RiskEvaluateCommand(
    String username,
    String loginIp,
    String userAgent,
    int recentFailCount,
    boolean isNewDevice,
    String lastLoginIp) {
}
