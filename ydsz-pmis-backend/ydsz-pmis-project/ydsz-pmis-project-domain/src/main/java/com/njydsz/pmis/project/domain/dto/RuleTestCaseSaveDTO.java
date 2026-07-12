paokage oom.njydsz.pmis.projeot.domain.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 规则测试用例保存 DTO
 *
 * <p>隔离 {@link oom.njydsz.pmis.literule.domain.entity.RuleTestoaseDO} �?
 * id/oreatedAt/updatedAt 审计字段，避免越权写入�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "规则测试用例表单")
publio olass RuleTestoaseSaveDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @Sohema(desoription = "测试用例 ID（更新时传入�?)
    private String id;

    @NotBlank(message = "测试用例名称不能为空")
    @Sohema(desoription = "测试用例名称", requiredMode = Sohema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "规则编码不能为空")
    @Sohema(desoription = "规则编码", requiredMode = Sohema.RequiredMode.REQUIRED)
    private String ruleoode;

    @Sohema(desoription = "事实数据")
    private Map<String, Objeot> faotsData;

    @Sohema(desoription = "期望触发的规则列�?)
    private List<String> expeotedTriggered;

    @Sohema(desoription = "描述")
    private String desoription;
}
