package com.njydsz.pmis.common.security;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 账号锁定事件
 *
 * <p>由 AuthService 在登录失败达到阈值锁定账号后发布，
 * 监听方可异步推送通知（邮件/短信/站内信）。
 *
 * <p>解锁逻辑：锁定时间到期后由登录校验时自动放行（查询 lockedUntil 是否过期），
 * 无需显式解锁操作。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
public class AccountLockedEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 锁定截止时间 */
    private LocalDateTime lockedUntil;

    /** 累计失败次数 */
    private int failCount;

    /** 锁定时长（分钟） */
    private int lockMinutes;

    /** 链路追踪 ID */
    private String traceId;

    /** 租户 ID */
    private String tenantId;

    /** 锁定时间戳（毫秒） */
    private Long lockedAt;
}
