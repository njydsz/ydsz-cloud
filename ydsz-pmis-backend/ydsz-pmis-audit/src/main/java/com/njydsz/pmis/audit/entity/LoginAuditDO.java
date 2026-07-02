package com.njydsz.pmis.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录审计实体
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_login_audit")
public class LoginAuditDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名 */
    private String username;
    /** 用户 ID */
    private Long userId;
    /** 登录时间 */
    private LocalDateTime loginAt;
    /** 登录 IP */
    private String loginIp;
    /** User-Agent */
    private String userAgent;
    /** SUCCESS / FAIL_PASSWORD / FAIL_LOCKED / ... */
    private String status;
    /** 失败原因 */
    private String failReason;
    /** 是否使用 MFA */
    private Boolean mfaUsed;
    /** MFA 是否成功 */
    private Boolean mfaSuccess;
    /** 链路追踪 ID */
    private String traceId;
    /** 租户 ID */
    private Long tenantId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
