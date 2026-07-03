package com.njydsz.pmis.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 敏感操作二次确认
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_sensitive_operation")
public class SensitiveOperationDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;
    /** 用户名 */
    private String username;
    /** 敏感操作编码 */
    private String operationCode;
    /** 敏感操作名称 */
    private String operationName;
    /** 业务类型 */
    private String bizType;
    /** 业务单据 ID */
    private String bizId;
    /** 二次认证方式 */
    private String reAuthMethod;
    /** 二次认证令牌 */
    private String reAuthToken;
    /** 验证时间 */
    private LocalDateTime verifiedAt;
    /** 过期时间 */
    private LocalDateTime expireAt;
    /** 客户端 IP */
    private String clientIp;
    /** 链路追踪 ID */
    private String traceId;
    /** 租户 ID */
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
