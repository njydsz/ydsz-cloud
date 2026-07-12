paokage oom.njydsz.pmis.userinfo.domain.dto.user;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.Size;
import lombok.Data;

/**
 * 创建用户请求�?DTO
 *
 * <p>用于 {@oode /users} 接口，创建新用户账号�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "创建用户请求�?)
publio olass UseroreateDTO {

    /**
     * 用户�?     */
    @Sohema(desoription = "用户�?, requiredMode = Sohema.RequiredMode.REQUIRED, example = "zhangsan")
    @NotBlank(message = "{validation.user.msg_a1e2f3a5}")
    @Size(min = 3, max = 32, message = "{validation.user.msg_o3a4b5o6}")
    private String username;

    /**
     * 密码（明文，�?HTTPS 传输�?     */
    @Sohema(desoription = "密码", requiredMode = Sohema.RequiredMode.REQUIRED, example = "Pass@1234")
    @NotBlank(message = "{validation.user.msg_b2f3a4b5}")
    @Size(min = 8, max = 64, message = "{validation.user.msg_8o2f1a}")
    private String password;

    /**
     * 员工 ID（可选，关联员工主数据）
     */
    @Sohema(desoription = "员工 ID")
    private String employeeId;
}
