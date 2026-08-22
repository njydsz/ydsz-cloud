package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

/**
 * 安全事件 VO。
 *
 * <p>记录异常登录、账号锁定等安全相关事件。
 *
 * <p>使用 {@link com.njydsz.common.json.YdszJson} 进行 JSON 序列化，字段名即为 JSON key。
 *
 * @param eventType 事件类型编码
 * @param username 用户名
 * @param ip 来源 IP
 * @param timestamp 事件发生时间
 * @param description 事件描述
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public record SecurityEventVO(
    String eventType,
    String username,
    String ip,
    LocalDateTime timestamp,
    String description) {
}
