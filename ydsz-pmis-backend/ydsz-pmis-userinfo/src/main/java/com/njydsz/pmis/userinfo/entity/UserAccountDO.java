package com.njydsz.pmis.userinfo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.njydsz.pmis.common.entity.BaseDO;
import com.njydsz.pmis.common.sensitive.Sensitive;
import com.njydsz.pmis.common.sensitive.SensitiveStrategy;
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

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户名（登录账号） */
    private String username;

    /** 密码密文（加盐哈希） */
    @JsonIgnore
    private String password;

    /** 密码盐值 */
    @JsonIgnore
    private String salt;

    /** 关联员工 ID */
    private String employeeId;

    /** 状态: ENABLED/DISABLED/LOCKED */
    private String status;

    /** 最近一次登录时间 */
    private LocalDateTime lastLoginTime;

    /** 最近一次登录 IP（脱敏：保留前 3 段） */
    @Sensitive(SensitiveStrategy.ADDRESS)
    private String lastLoginIp;

    /** 连续登录失败次数 */
    private Integer loginFailCount;

    /** 账号锁定截止时间（锁定时非空） */
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

    /** P2-2: 所属部门 ID（关联 pmis_department.id，用于审批人 dept: 展开） */
    private String deptId;

    /** P2-2: 直属上级用户 ID（关联 pmis_user_account.id，用于审批人 leader: 展开） */
    private String leaderId;

    /** P2-2: 岗位编码（如 PM/DEV/QA/SA，用于审批人 position: 展开） */
    private String positionCode;
}
