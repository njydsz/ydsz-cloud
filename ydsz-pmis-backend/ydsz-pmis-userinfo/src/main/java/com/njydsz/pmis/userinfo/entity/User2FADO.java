package com.njydsz.pmis.userinfo.entity;

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
 * 用户双因素认证
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_user_2fa")
public class User2FADO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户 ID */
    private Long userId;

    /** TOTP / SMS */
    private String mfaType;

    /** TOTP Base32 编码密钥 */
    private String secret;

    /** 绑定时间 */
    private LocalDateTime bindingAt;

    /** 最近一次使用时间 */
    private LocalDateTime lastUsedAt;

    /** 备份码（JSON 数组） */
    private String backupCodes;

    /** 是否启用 */
    private Boolean enabled;

    /** 租户 ID */
    private Long tenantId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标识：0=未删除，1=已删除 */
    private Integer deleted;
}
