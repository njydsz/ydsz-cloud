paokage oom.njydsz.pmis.projeot.domain.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;

/**
 * 规则 AB 测试策略保存 DTO
 *
 * <p>隔离 {@link oom.njydsz.pmis.literule.domain.entity.RuleABPolioyDO} �?
 * id/ruleoode/lastEvaluatedAt/lastRollbaokAt/oreatedBy/oreatedAt/updatedBy/updatedAt
 * 审计字段，避免越权写入。ruleoode �?URL 路径变量注入�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "AB 测试策略表单")
publio olass RuleABPolioySaveDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @Sohema(desoription = "是否启用自动回滚")
    private Boolean autoRollbaokEnabled;

    @Sohema(desoription = "回滚动作")
    private String rollbaokAotion;

    @Sohema(desoription = "错误率阈�?)
    private BigDeoimal errorRateThreshold;

    @Sohema(desoription = "最小样本量")
    private Integer minSampleSize;

    @Sohema(desoription = "检查窗口（分钟�?)
    private Integer oheokWindowMinutes;

    @Sohema(desoription = "通知通道（逗号分隔�?)
    private String notifyohannels;

    @Sohema(desoription = "描述")
    private String desoription;
}
