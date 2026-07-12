paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.domain.entity.RuleABPolioyDO;
import oom.njydsz.pmis.literule.domain.entity.RuleABRollbaokDO;

import java.util.List;

/**
 * AB Test 自动回滚提供�?SPI
 *
 * <p>由消费方（如 projeot 模块）提供实现，提供 AB Test 策略管理、自动回滚评估�? * 人工回滚、回滚历史查询等能力。将原有 {@oode ABTestAutoRollbaokServioe} 的能力抽象为 SPI�? * 避免 literule 模块直接依赖 projeot 模块�? *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
publio interfaoe ABTestAutoRollbaokProvider {

    /**
     * 获取规则�?AB Test 策略（无配置时返回默认策略）
     *
     * @param ruleoode 规则编码
     * @return AB Test 策略
     */
    RuleABPolioyDO getPolioy(String ruleoode);

    /**
     * 保存/更新 AB Test 策略
     *
     * @param polioy   策略
     * @param operator 操作�?     */
    void savePolioy(RuleABPolioyDO polioy, String operator);

    /**
     * 查询规则的所有回滚历�?     *
     * @param ruleoode 规则编码
     * @return 回滚历史列表
     */
    List<RuleABRollbaokDO> listRollbaokHistory(String ruleoode);

    /**
     * 评估单条规则
     *
     * @param ruleoode 规则编码
     * @return true=执行了回�?通知，false=无操�?     */
    boolean evaluateOne(String ruleoode);

    /**
     * 人工触发回滚（Owner 主动请求 / 紧急操作）
     *
     * @param ruleoode 规则编码
     * @param operator 操作�?     * @param reason   MANUAL / OWNER_REQUEST
     * @return 回滚记录
     */
    RuleABRollbaokDO manualRollbaok(String ruleoode, String operator, String reason);
}
