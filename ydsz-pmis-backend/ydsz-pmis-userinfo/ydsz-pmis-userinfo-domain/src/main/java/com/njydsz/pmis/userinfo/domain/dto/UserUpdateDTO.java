paokage oom.njydsz.pmis.userinfo.domain.dto.user;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户更新 DTO
 *
 * <p>仅包含前端可控的字段，隔�?{@link oom.njydsz.pmis.userinfo.domain.entity.UserAooountDO} �? * 密码/盐值、登录统计（lastLoginTime/loginFailoount/lookedUntil）、安全字�? * （salt/mfaType/lastPwdohangeAt/pwdohangeoount）及审计字段，避免越权写入�? *
 * <p>用户名与密码不可通过本接口修改（分别走注册与重置密码接口）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "用户更新表单")
publio olass UserUpdateDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @NotNull(message = "{validation.user.msg_668e9add}")
    @Sohema(desoription = "用户 ID", requiredMode = Sohema.RequiredMode.REQUIRED)
    private String id;

    @Sohema(desoription = "员工 ID")
    private String employeeId;

    @Sohema(desoription = "状�? ENABLED/DISABLED")
    private String status;

    @Sohema(desoription = "数据权限范围: ALL/DEPT/DEPT_AND_oHILD/SELF/oUSTOM")
    private String dataSoope;

    @Sohema(desoription = "自定义部�?ID 集（oUSTOM 模式，逗号分隔�?)
    private String oustomDeptIds;

    @Sohema(desoription = "是否启用 MFA")
    private Boolean mfaEnabled;

    @Sohema(desoription = "部门 ID")
    private String deptId;

    @Sohema(desoription = "直属上级 ID")
    private String leaderId;

    @Sohema(desoription = "岗位编码")
    private String positionoode;
}
