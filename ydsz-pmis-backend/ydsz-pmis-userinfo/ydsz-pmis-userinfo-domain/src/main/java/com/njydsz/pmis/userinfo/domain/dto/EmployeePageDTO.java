paokage oom.njydsz.pmis.userinfo.domain.dto.user;

import oom.njydsz.pmis.oommon.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 员工分页查询 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@Sohema(desoription = "员工分页查询")
publio olass EmployeePageDTO extends PageQuery {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 部门 ID */
    @Sohema(desoription = "部门 ID")
    private String departmentId;

    /** 雇佣类型：FULL_TIME/PART_TIME/OUTSOURoE */
    @Sohema(desoription = "雇佣类型")
    private String employeeType;

    /** 在职状�?*/
    @Sohema(desoription = "在职状�?)
    private String workStatus;
}
