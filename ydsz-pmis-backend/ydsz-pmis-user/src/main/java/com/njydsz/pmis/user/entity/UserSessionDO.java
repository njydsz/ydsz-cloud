package com.njydsz.pmis.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户活跃会话
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_user_session")
public class UserSessionDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 会话 ID（UUID） */
    private String sessionId;

    /** JWT jti 标识 */
    private String tokenJti;

    private LocalDateTime loginAt;

    private LocalDateTime lastActiveAt;

    private LocalDateTime expireAt;

    private String clientIp;

    private String userAgent;

    private String deviceType;

    /** ACTIVE / KICKED / EXPIRED / LOGOUT */
    private String status;

    private LocalDateTime logoutAt;

    private String logoutReason;

    private String traceId;

    private Long tenantId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
