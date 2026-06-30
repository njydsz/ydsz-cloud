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

    private String username;
    private Long userId;
    private LocalDateTime loginAt;
    private String loginIp;
    private String userAgent;
    /** SUCCESS / FAIL_PASSWORD / FAIL_LOCKED / ... */
    private String status;
    private String failReason;
    private Boolean mfaUsed;
    private Boolean mfaSuccess;
    private String traceId;
    private Long tenantId;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}
