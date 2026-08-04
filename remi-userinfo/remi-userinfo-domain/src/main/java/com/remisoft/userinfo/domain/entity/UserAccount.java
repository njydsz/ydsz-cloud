package com.remisoft.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.remisoft.common.jdbc.entity.MpBaseEntity;
import com.remisoft.common.jdbc.handler.IntegerStringTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 用户账号实体
 *
 * <p>对应数据库表 {@code remi_user_account}，存储系统用户账号信息。
 * 是用户中心服务的核心实体，被各业务模块通过 Feign 远程查询。
 *
 * <p><b>安全敏感字段：</b>
 * <ul>
 *   <li>{@code password}：BCrypt 加密（cost=10），禁止明文存储与返回</li>
 *   <li>{@code phone} / {@code email}：使用 {@code SensitiveType.PHONE/EMAIL} 脱敏</li>
 *   <li>{@code loginFailCount} / {@code lockedUntil}：登录失败保护，达到阈值自动锁定</li>
 * </ul>
 *
 * <p><b>状态字段类型不统一说明：</b>user_account 表使用整数状态码（{@code 0=禁用, 1=启用}，历史遗留），
 * 而 Role/Menu/Department/Company/Post/Language.status 为 String（{@code "ENABLED"/"DISABLED"}）。
 * 为兼容 {@link MpBaseEntity#getStatus()} 的 String 返回类型，本类 status 字段声明为 String，
 * 并通过 {@link IntegerStringTypeHandler} 在持久化时与整数列双向转换。
 *
 * <p><b>审批人展开支持：</b>
 * <ul>
 *   <li>{@code deptId}：所属部门，支持 {@code dept:xxx} 审批人展开</li>
 *   <li>{@code leaderId}：直属上级用户 ID，支持 {@code leader:xxx} 展开</li>
 *   <li>{@code positionCode}：岗位编码（PM/DEV/QA/SA），支持 {@code position:xxx} 展开</li>
 * </ul>
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_username}（{@code username}），
 * 普通索引 {@code idx_phone}（{@code phone}）、{@code idx_dept_id}（{@code dept_id}）。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("remi_user_account")
public class UserAccount extends MpBaseEntity<String> {

    /** 登录用户名（全局唯一） */
    private String username;

    /** 登录密码（BCrypt 加密，禁止明文存储/返回） */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 手机号（用于短信验证/找回密码，脱敏返回） */
    private String phone;

    /** 邮箱（用于通知/找回密码，脱敏返回） */
    private String email;

    /** 头像 URL */
    private String avatar;

    /**
     * 账号状态（0=禁用, 1=启用，DB 整数列）
     *
     * <p>通过 {@link IntegerStringTypeHandler} 实现 String↔Integer 双向转换。
     * 业务代码建议使用字符串 {@code "0"}/{@code "1"}，由 TypeHandler 在持久化层转换。
     */
    @TableField(value = "status", typeHandler = IntegerStringTypeHandler.class)
    private String status;

    /** 用户类型（PLATFORM/ISV/TENANT_ADMIN/REGULAR 等） */
    private String userType;

    /** 所属公司 ID（关联 {@code remi_company.id}） */
    private String companyId;

    /** 最近登录时间 */
    private LocalDateTime lastLoginAt;

    /** 最近登录 IP */
    private String lastLoginIp;

    /** 连续登录失败次数（达到阈值触发账号锁定） */
    private Integer loginFailCount;

    /** 账号锁定截止时间（解锁后自动清零 loginFailCount） */
    private LocalDateTime lockedUntil;

    /** 所属部门 ID（关联 remi_department.id，支持 dept: 审批人展开） */
    private String deptId;

    /** 直属上级用户 ID（关联 remi_user_account.id，支持 leader: 审批人展开） */
    private String leaderId;

    /** 岗位编码（如 PM/DEV/QA/SA，支持 position: 审批人展开） */
    private String positionCode;
}
