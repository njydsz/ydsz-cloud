package com.njydsz.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.common.jdbc.handler.IntegerStringTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 用户账号实体。
 *
 * <p><b>注意</b>：本模块中实体状态字段类型不统一——
 * user_account 表使用整数状态码（0=禁用, 1=启用，历史遗留），
 * RoleDO/MenuDO/DepartmentDO/CompanyDO/PostDO/LanguageDO.status 为 String（"ENABLED"/"DISABLED"）。
 * 为兼容 {@link MpBaseEntity#getStatus()} 的 String 返回类型，本类 status 字段声明为 String，
 * 并通过 {@link IntegerStringTypeHandler} 在持久化时与整数列双向转换。
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

    private String tenantId;

    private String username;
    private String password;
    private String realName;
    private String phone;
    private String email;
    private String avatar;

    @TableField(value = "status", typeHandler = IntegerStringTypeHandler.class)
    private String status;

    private String userType;
    private String companyId;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private Integer loginFailCount;
    private LocalDateTime lockedUntil;

    /** 所属部门 ID（关联 ydsz_department.id，支持 dept: 审批人展开） */
    private String deptId;

    /** 直属上级用户 ID（关联 ydsz_user_account.id，支持 leader: 审批人展开） */
    private String leaderId;

    /** 岗位编码（如 PM/DEV/QA/SA，支持 position: 审批人展开） */
    private String positionCode;
}
