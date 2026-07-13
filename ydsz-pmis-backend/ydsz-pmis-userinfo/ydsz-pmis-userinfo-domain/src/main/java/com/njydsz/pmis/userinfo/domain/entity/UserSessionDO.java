package com.njydsz.pmis.userinfo.domain.entity.user;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

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

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户 ID */
    private String userId;

    /** 会话 ID（UUID） */
    private String sessionId;

    /** JWT jti 标识 */
    private String tokenJti;

    /** 登录时间 */
    private LocalDateTime loginAt;

    /** 最近活跃时间 */
    private LocalDateTime lastActiveAt;

    /** 会话过期时间 */
    private LocalDateTime expireAt;

    /** 客户端 IP */
    private String clientIp;

    /** User-Agent 头 */
    private String userAgent;

    /** 设备类型：PC/APP/H5 */
    private String deviceType;

    /** ACTIVE / KICKED / EXPIRED / LOGOUT */
    private String status;

    /** 登出时间 */
    private LocalDateTime logoutAt;

    /** 登出原因 */
    private String logoutReason;

    /** 链路追踪 ID */
    private String traceId;

    /** 租户 ID */
    private String tenantId;

    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标识：0=未删除，1=已删除 */
    private Integer deleted;
}
