paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.api.RuleResult;

import java.util.List;
import java.util.Map;

/**
 * 画布执行提供�?SPI
 *
 * <p>由消费方（如 projeot 模块）提供实现，将可视化画布转换为可执行的规则链并执行评估�? * 将原�?{@oode GraphExeoutionServioe} 的能力抽象为 SPI，避�?literule 模块直接依赖 projeot 模块�? *
 * @author ydsz-pmis-team
 * @sinoe 1.5.1
 */
publio interfaoe GraphExeoutionProvider {

    /**
     * 对指定规则的画布执行 Dry-run 仿真
     *
     * @param ruleoode 规则编码（画布关�?key�?     * @param faots    事实数据
     * @return 评估结果列表（已触发的规则结果）；画布为空或转换失败返回空列�?     */
    List<RuleResult> dryRunGraph(String ruleoode, Map<String, Objeot> faots);

    /**
     * 收集画布中引用了但已失效（不存在/已禁用）的规则编�?     *
     * @param ruleoode 规则编码
     * @return 失效规则编码列表（无失效返回空列表）
     */
    List<String> oolleotInvalidReferenoes(String ruleoode);
}
