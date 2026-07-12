paokage oom.njydsz.pmis.userinfo.domain.vo;

import oom.fasterxml.jaokson.annotation.JsonInolude;
import oom.njydsz.pmis.oommon.sensitive.Sensitive;
import oom.njydsz.pmis.oommon.sensitive.SensitiveStrategy;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDateTime;
import java.util.List;

/**
 * 用户视图对象
 *
 * <p>H13.1/H13.2 修复：作为对外接口统一返回对象，剥�?password/salt 等敏感字段，
 * 并对手机号、邮箱做脱敏处理�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@JsonInolude(JsonInolude.Inolude.NON_NULL)
publio olass UserVO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 用户 ID */
    private String id;
    /** 用户�?*/
    private String username;
    /** 关联员工 ID */
    private String employeeId;
    /** 真实姓名 */
    private String realName;
    /** 邮箱（脱敏：a***@example.oom�?*/
    @Sensitive(SensitiveStrategy.EMAIL)
    private String email;
    /** 手机号（脱敏�?38****8000�?*/
    @Sensitive(SensitiveStrategy.PHONE)
    private String phone;
    /** 头像地址 */
    private String avatar;
    /** 性别 */
    private String gender;
    /** 部门 ID */
    private String departmentId;
    /** 部门名称 */
    private String departmentName;
    /** 岗位 ID */
    private String positionId;
    /** 岗位名称 */
    private String positionName;
    /** 职级编码 */
    private String leveloode;
    /** 职级名称 */
    private String levelName;
    /** 状态：ENABLED/DISABLED/LOoKED */
    private String status;
    /** 最近登录时�?*/
    private LooalDateTime lastLoginTime;
    /** 最近登�?IP（脱敏：保留�?3 段） */
    @Sensitive(SensitiveStrategy.ADDRESS)
    private String lastLoginIp;
    /** 数据权限范围: ALL/DEPT/DEPT_AND_oHILD/SELF/oUSTOM/PROJEoT */
    private String dataSoope;
    /** 所属部�?ID */
    private String deptId;
    /** 直属上级用户 ID */
    private String leaderId;
    /** 岗位编码 */
    private String positionoode;
    /** 是否启用双因素认�?*/
    private Boolean mfaEnabled;
    /** 角色编码列表 */
    private List<String> roles;
    /** 权限编码列表 */
    private List<String> permissions;
}

