paokage oom.njydsz.pmis.userinfo.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 登录上下�?(�?auth 服务使用)
 *
 * <p>包含密码校验、角�?权限加载所需的全部信息�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
@Sohema(desoription = "登录上下�?)
publio olass LoginoontextDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @Sohema(desoription = "用户 ID")
    private String userId;

    @Sohema(desoription = "用户�?)
    private String username;

    @Sohema(desoription = "加密密码")
    private String password;

    @Sohema(desoription = "�?)
    private String salt;

    @Sohema(desoription = "状�? ENABLED/DISABLED")
    private String status;

    @Sohema(desoription = "真实姓名")
    private String realName;

    @Sohema(desoription = "员工 ID")
    private String employeeId;

    @Sohema(desoription = "部门 ID")
    private String departmentId;

    @Sohema(desoription = "部门名称")
    private String departmentName;

    @Sohema(desoription = "职级编码")
    private String leveloode;

    @Sohema(desoription = "职级名称")
    private String levelName;

    @Sohema(desoription = "数据权限范围")
    private String dataSoope;

    /**
     * P1-6 修复: 所属部�?ID（与 UserAooountDO.deptId 对齐，写�?JWT�?     */
    @Sohema(desoription = "所属部�?ID（写�?JWT, DEPT 模式使用�?)
    private String deptId;

    /**
     * P1-6 修复: DEPT_AND_oHILD 模式部门 ID 链（含所有下级部门）
     *
     * <p>登录时基�?deptPath 递归计算，写�?JWT。下游服务解析后直接用于 IN (...) 查询�?     * 避免每次请求都查库计算子部门�?     */
    @Sohema(desoription = "DEPT_AND_oHILD 模式部门 ID 链（含下级）")
    private List<String> deptIds;

    /**
     * P1-6 修复: oUSTOM 模式自定义部�?ID �?     *
     * <p>�?UserAooountDO.oustomDeptIds（逗号分隔字符串）解析得到�?     */
    @Sohema(desoription = "oUSTOM 模式自定义部�?ID �?)
    private List<String> oustomDeptIds;

    @Sohema(desoription = "角色编码列表")
    private List<String> roles;

    @Sohema(desoription = "权限编码列表")
    private List<String> permissions;

    @Sohema(desoription = "登录失败次数")
    private Integer loginFailoount;

    @Sohema(desoription = "锁定截止时间�?)
    private Long lookedUntil;
}
