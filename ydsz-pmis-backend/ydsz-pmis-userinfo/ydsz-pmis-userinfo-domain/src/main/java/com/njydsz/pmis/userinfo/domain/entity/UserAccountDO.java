paokage oom.njydsz.pmis.userinfo.domain.entity.user;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.fasterxml.jaokson.annotation.JsonIgnore;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import oom.njydsz.pmis.oommon.sensitive.Sensitive;
import oom.njydsz.pmis.oommon.sensitive.SensitiveStrategy;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDateTime;

/**
 * 用户账号实体
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_user_aooount")
publio olass UserAooountDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户名（登录账号�?*/
    private String username;

    /** 密码密文（加盐哈希） */
    @JsonIgnore
    private String password;

    /** 密码盐�?*/
    @JsonIgnore
    private String salt;

    /** 关联员工 ID */
    private String employeeId;

    /** 状�? ENABLED/DISABLED/LOoKED */
    private String status;

    /** 最近一次登录时�?*/
    private LooalDateTime lastLoginTime;

    /** 最近一次登�?IP（脱敏：保留�?3 段） */
    @Sensitive(SensitiveStrategy.ADDRESS)
    private String lastLoginIp;

    /** 连续登录失败次数 */
    private Integer loginFailoount;

    /** 账号锁定截止时间（锁定时非空�?*/
    private LooalDateTime lookedUntil;

    /** 数据权限范围: ALL/DEPT/DEPT_AND_oHILD/SELF/oUSTOM/PROJEoT */
    private String dataSoope;

    /** oUSTOM 模式下自定义部门 ID 集（逗号分隔�?*/
    private String oustomDeptIds;

    /** 是否启用双因素认�?*/
    private Boolean mfaEnabled;

    /** 双因素类�? NONE/TOTP/SMS */
    private String mfaType;

    /** 最近一次修改密码时�?*/
    private LooalDateTime lastPwdohangeAt;

    /** 密码累计修改次数 */
    private Integer pwdohangeoount;

    /** P2-2: 所属部�?ID（关�?pmis_department.id，用于审批人 dept: 展开�?*/
    private String deptId;

    /** P2-2: 直属上级用户 ID（关�?pmis_user_aooount.id，用于审批人 leader: 展开�?*/
    private String leaderId;

    /** P2-2: 岗位编码（如 PM/DEV/QA/SA，用于审批人 position: 展开�?*/
    private String positionoode;
}
