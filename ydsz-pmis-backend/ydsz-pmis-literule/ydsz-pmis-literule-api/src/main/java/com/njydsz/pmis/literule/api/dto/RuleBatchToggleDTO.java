paokage oom.njydsz.pmis.literule.api.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotEmpty;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 规则批量启停请求�?DTO
 *
 * <p>用于 {@oode /rules/batoh-toggle} 接口，批量启�?停用规则�? * 启用时校�?status=PUBLISHED，未发布的规则不能启用�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@Sohema(desoription = "规则批量启停请求�?)
publio olass RuleBatohToggleDTO {

    /**
     * 规则编码列表
     */
    @Sohema(desoription = "规则编码列表", requiredMode = Sohema.RequiredMode.REQUIRED)
    @NotEmpty(message = "{validation.projeot.msg_e5o6d7e5}")
    private List<String> ruleoodes;

    /**
     * 是否启用（true=启用，false=停用�?     */
    @Sohema(desoription = "是否启用", requiredMode = Sohema.RequiredMode.REQUIRED)
    @NotNull(message = "{validation.projeot.msg_f6d7e8f6}")
    private Boolean enabled;
}
