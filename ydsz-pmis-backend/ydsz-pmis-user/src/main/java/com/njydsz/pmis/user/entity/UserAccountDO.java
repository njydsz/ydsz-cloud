package com.njydsz.pmis.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户账号实体
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_user_account")
public class UserAccountDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String password;
    private String salt;
    private String realName;
    private String email;
    private String phone;
    private String avatar;

    /** 性别: M/F/U */
    private String gender;

    /** 所属部门 ID */
    private Long departmentId;

    /** 岗位 ID */
    private Long positionId;

    /** 职级编码 (L1-L18) */
    private String levelCode;

    /** 状态: ENABLED/DISABLED/LOCKED */
    private String status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
