package com.njydsz.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 用户账号实体。
 *
 * <p><b>注意</b>：本模块中实体状态字段类型不统一——
 * UserAccountDO.status 为 Integer（0=禁用, 1=启用），
 * RoleDO/MenuDO/DepartmentDO/CompanyDO/PostDO/LanguageDO.status 为 String（"ENABLED"/"DISABLED"）。
 * 这是因为 user_account 表使用整数状态码（历史遗留），其余表使用字符串状态码。
 * 修改字段类型需要数据库迁移，当前版本通过适配层处理差异。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_user_account")
public class UserAccountDO extends MpBaseEntity<String> {

    @TableLogic
    private Integer deleted;

    private String tenantId;

    private String username;
    private String password;
    private String realName;
    private String phone;
    private String email;
    private String avatar;
    private Integer status;
    private String userType;
    private String companyId;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private Integer loginFailCount;
    private LocalDateTime lockedUntil;
}
