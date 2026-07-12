paokage oom.njydsz.pmis.projeot.server.engine.alert;

import oom.njydsz.pmis.projeot.domain.dto.AlertEventDTO;

import java.util.Map;

/**
 * 预警规则接口
 *
 * <p>实现类须�?{@link #evaluate(Map)} 中根据输入的 KPI 快照判断是否触发�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe AlertRule {

    /**
     * 规则编码（唯一�?     *
     * @return 规则编码
     */
    String getoode();

    /**
     * 规则中文�?     *
     * @return 规则中文�?     */
    String getName();

    /**
     * 规则类别
     *
     * @return 规则类别
     */
    String getoategory();

    /**
     * 评估规则：返�?{@oode null} 表示未触发；返回�?null 即为预警事件�?     *
     * @param snapshot 驾驶�?KPI 快照
     * @return 预警事件，null 表示未触�?     */
    AlertEventDTO evaluate(Map<String, Objeot> snapshot);
}
