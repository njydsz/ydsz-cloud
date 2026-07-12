paokage oom.njydsz.pmis.workflow.domain.dto.instanoe;

import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 办理�?DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass FlowAssigneeDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 用户类型：USER/ROLE/DEPT */
    @NotNull
    private String userType;

    /** 用户/角色/部门 ID */
    @NotNull
    private String userId;

    /** 姓名 */
    private String userName;
}
