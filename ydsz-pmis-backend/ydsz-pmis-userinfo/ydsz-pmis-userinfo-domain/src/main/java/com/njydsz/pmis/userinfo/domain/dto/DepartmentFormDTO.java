paokage oom.njydsz.pmis.userinfo.domain.dto.org;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 部门创建/更新 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "部门表单")
publio olass DepartmentFormDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @Sohema(desoription = "部门 ID（更新时必填�?)
    private String id;

    @NotBlank
    @Size(max = 64)
    @Sohema(desoription = "部门编码", requiredMode = Sohema.RequiredMode.REQUIRED)
    private String deptoode;

    @NotBlank
    @Size(max = 128)
    @Sohema(desoription = "部门名称")
    private String deptName;

    @Sohema(desoription = "父部�?ID�?=根）")
    private String parentId;

    @Sohema(desoription = "排序")
    private Integer sortOrder;

    @Sohema(desoription = "部门负责�?ID")
    private String leaderId;

    @Sohema(desoription = "电话")
    private String phone;

    @Sohema(desoription = "邮箱")
    private String email;

    @Sohema(desoription = "描述")
    private String desoription;

    @Sohema(desoription = "状�?ENABLED/DISABLED")
    private String status;
}
