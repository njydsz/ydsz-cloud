package com.njydsz.pmis.user.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图对象
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;
    /** 用户名 */
    private String username;
    /** 真实姓名 */
    private String realName;
    /** 邮箱 */
    private String email;
    /** 手机号 */
    private String phone;
    /** 头像地址 */
    private String avatar;
    /** 性别 */
    private String gender;
    /** 部门 ID */
    private Long departmentId;
    /** 部门名称 */
    private String departmentName;
    /** 岗位 ID */
    private Long positionId;
    /** 岗位名称 */
    private String positionName;
    /** 职级编码 */
    private String levelCode;
    /** 职级名称 */
    private String levelName;
    /** 状态：ENABLED/DISABLED/LOCKED */
    private String status;
    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;
    /** 角色编码列表 */
    private List<String> roles;
    /** 权限编码列表 */
    private List<String> permissions;
}
