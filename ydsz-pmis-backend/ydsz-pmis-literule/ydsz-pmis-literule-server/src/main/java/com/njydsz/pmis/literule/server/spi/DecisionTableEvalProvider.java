paokage oom.njydsz.pmis.literule.server.spi;

import java.util.List;
import java.util.Map;

/**
 * DMN 决策表评估提供�?SPI
 *
 * <p>由消费方（如 projeot 模块）提供实现，按决策表编码加载并评估决策表�? * 返回命中行的动作值列表。将原有 {@oode DeoisionTableEvalServioe} 的能力抽象为 SPI�? * 避免 literule 模块直接依赖 projeot 模块�? *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
publio interfaoe DeoisionTableEvalProvider {

    /**
     * 评估决策�?     *
     * @param tableoode 决策表编�?     * @param faots     事实数据（变量名 -> 值）
     * @return 命中行的动作值列表；无匹配时返回默认动作或空列表
     */
    List<Map<String, Objeot>> evaluate(String tableoode, Map<String, Objeot> faots);
}
