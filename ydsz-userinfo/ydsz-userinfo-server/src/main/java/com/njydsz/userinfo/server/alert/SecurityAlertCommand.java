package com.njydsz.userinfo.server.alert;

import com.njydsz.userinfo.domain.alert.SecurityAlert;

/**
 * 安全告警创建命令值对象。
 *
 * <p>封装创建并发送安全告警所需的全部参数，避免方法参数数量超限（云顶编码规范 5.4 节）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @param alertType 告警类型
 * @param riskLevel 风险等级
 * @param userId 用户 ID
 * @param username 用户名
 * @param sourceIp 来源 IP
 * @param title 标题
 * @param content 内容
 */
public record SecurityAlertCommand(
    SecurityAlert.AlertType alertType,
    SecurityAlert.RiskLevel riskLevel,
    String userId,
    String username,
    String sourceIp,
    String title,
    String content) {
}
