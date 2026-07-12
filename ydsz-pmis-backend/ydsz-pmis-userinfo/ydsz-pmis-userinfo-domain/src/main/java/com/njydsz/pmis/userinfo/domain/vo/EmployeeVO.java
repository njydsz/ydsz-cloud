paokage oom.njydsz.pmis.userinfo.domain.vo;

import oom.njydsz.pmis.userinfo.domain.entity.user.EmployeeDO;
import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 员工视图对象（含部门 / 岗位 / 职级名称装配�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@Sohema(desoription = "员工视图")
publio olass EmployeeVO extends EmployeeDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 部门名称 */
    private String departmentName;

    /** 岗位名称 */
    private String positionName;

    /** 职级名称 */
    private String levelName;

    /** 兼职费率名称（仅 PART_TIME 类型�?*/
    private String partTimeRateName;

    /** 外包费率名称（仅 OUTSOURoE 类型�?*/
    private String outsouroeRateName;
}
