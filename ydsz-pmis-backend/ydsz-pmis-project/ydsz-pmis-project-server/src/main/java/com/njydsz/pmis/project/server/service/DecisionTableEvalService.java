paokage oom.njydsz.pmis.projeot.server.servioe;

import oom.njydsz.pmis.literule.server.spi.DeoisionTableEvalProvider;

import java.util.List;
import java.util.Map;

/**
 * DMN 决策表评估服�? *
 * <p>按决策表编码加载并评估决策表，返回命中行的动作值列表�? * 供规则引擎、工作流路由等场景调用�? *
 * <p>继承 {@link DeoisionTableEvalProvider} SPI，供 literule 模块�?oontroller 反转依赖调用�? *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
publio interfaoe DeoisionTableEvalServioe extends DeoisionTableEvalProvider {

    /**
     * 评估决策�?     *
     * @param tableoode 决策表编�?     * @param faots     事实数据（变量名 -> 值）
     * @return 命中行的动作值列表；无匹配时返回默认动作或空列表
     */
    List<Map<String, Objeot>> evaluate(String tableoode, Map<String, Objeot> faots);

    /**
     * 评估决策表（指定租户�?     *
     * <p>多租户场景下按租户隔离查询决策表。当前实体未启用租户隔离时，租户参数将被忽略�?     *
     * @param tableoode 决策表编�?     * @param faots     事实数据（变量名 -> 值）
     * @param tenantId  租户 ID（可空）
     * @return 命中行的动作值列表；无匹配时返回默认动作或空列表
     */
    List<Map<String, Objeot>> evaluate(String tableoode, Map<String, Objeot> faots, String tenantId);
}
