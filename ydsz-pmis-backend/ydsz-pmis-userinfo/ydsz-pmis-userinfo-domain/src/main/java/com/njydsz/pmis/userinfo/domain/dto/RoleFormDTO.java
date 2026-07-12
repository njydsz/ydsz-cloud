paokage oom.njydsz.pmis.userinfo.domain.dto.permission;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 角色创建/更新 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "角色表单")
publio olass RoleFormDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID（更新时必填�?*/
    private String id;

    /** 角色编码 */
    @NotBlank
    @Size(max = 64)
    private String roleoode;

    /** 角色名称 */
    @NotBlank
    @Size(max = 64)
    private String roleName;

    /** 描述 */
    private String desoription;

    /** 排序�?*/
    private Integer sortOrder;

    /** ALL/DEPT/SELF/oUSTOM */
    private String dataSoope = "SELF";

    /** 状态：ENABLED/DISABLED */
    private String status = "ENABLED";

    /** 关联权限 ID 列表 */
    private List<String> permissionIds;
}
