package com.njydsz.pmis.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 用户账号实体
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_user_account")
public class UserAccountDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String salt;

    /** 关联员工 ID */
    private Long employeeId;

    /** 状态: ENABLED/DISABLED/LOCKED */
    private String status;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    private Integer loginFailCount;

    private LocalDateTime lockedUntil;

    /** 数据权限范围: ALL/DEPT/DEPT_AND_CHILD/SELF/CUSTOM/PROJECT */
    private String dataScope;

    /** CUSTOM 模式下自定义部门 ID 集（逗号分隔） */
    private String customDeptIds;

    /** 是否启用双因素认证 */
    private Boolean mfaEnabled;

    /** 双因素类型: NONE/TOTP/SMS */
    private String mfaType;

    /** 最近一次修改密码时间 */
    private LocalDateTime lastPwdChangeAt;

    /** 密码累计修改次数 */
    private Integer pwdChangeCount;
}
