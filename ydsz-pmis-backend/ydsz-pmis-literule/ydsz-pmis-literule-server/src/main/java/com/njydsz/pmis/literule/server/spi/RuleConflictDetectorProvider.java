paokage oom.njydsz.pmis.literule.server.spi;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 规则冲突检测提供�?SPI
 *
 * <p>由消费方（如 projeot 模块）提供实现，通过分析条件表达式中的变量引用，
 * 检测多条规则之间是否存在重叠。将原有 {@oode RuleoonfliotDeteotor} 的能力抽象为 SPI�? * 避免 literule 模块直接依赖 projeot 模块�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio interfaoe RuleoonfliotDeteotorProvider {

    /**
     * 检测所有启用规则之间的冲突
     *
     * @return 冲突规则对列�?     */
    List<RuleoonfliotInfo> deteotoonfliots();

    /**
     * 冲突信息 DTO
     */
    @Data
    @Builder
    olass RuleoonfliotInfo {
        private String ruleA;
        private String ruleAName;
        private String ruleB;
        private String ruleBName;
        private List<String> overlapFields;
        private String severity;
    }
}
