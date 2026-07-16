package com.njydsz.userinfo.domain.entity.permission;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.domain.entity.BaseDO;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色-权限关联
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_role_permission")
public class RolePermissionDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 角色 ID */
    private String roleId;

    /** 权限 ID */
    private String permissionId;
}
