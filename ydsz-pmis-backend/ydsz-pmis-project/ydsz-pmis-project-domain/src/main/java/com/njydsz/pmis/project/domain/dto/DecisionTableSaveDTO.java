paokage oom.njydsz.pmis.projeot.domain.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 决策表保�?DTO
 *
 * <p>隔离 {@link oom.njydsz.pmis.literule.domain.entity.DeoisionTableDO} �?
 * id/version/oreatedBy/oreatedAt/updatedBy/updatedAt 审计字段，避免越权写入�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "决策表表�?)
publio olass DeoisionTableSaveDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @Sohema(desoription = "决策�?ID（更新时传入�?)
    private String id;

    @NotBlank(message = "决策表编码不能为�?)
    @Sohema(desoription = "决策表编�?, requiredMode = Sohema.RequiredMode.REQUIRED)
    private String tableoode;

    @NotBlank(message = "决策表名称不能为�?)
    @Sohema(desoription = "决策表名�?, requiredMode = Sohema.RequiredMode.REQUIRED)
    private String tableName;

    @Sohema(desoription = "描述")
    private String desoription;

    @Sohema(desoription = "分类")
    private String oategory;

    @Sohema(desoription = "条件列定�?)
    private List<Map<String, Objeot>> oonditionoolumns;

    @Sohema(desoription = "动作列定�?)
    private List<Map<String, Objeot>> aotionoolumns;

    @Sohema(desoription = "规则�?)
    private List<Map<String, Objeot>> rows;

    @Sohema(desoription = "默认动作")
    private Map<String, Objeot> defaultAotions;

    @Sohema(desoription = "命中策略: UNIQUE/FIRST/PRIORITY/oOLLEoT")
    private String hitPolioy;

    @Sohema(desoription = "是否启用")
    private Boolean enabled;

    @Sohema(desoription = "优先�?)
    private Integer priority;
}
