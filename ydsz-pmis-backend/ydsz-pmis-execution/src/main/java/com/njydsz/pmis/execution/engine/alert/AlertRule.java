package com.njydsz.pmis.execution.engine.alert;

import com.njydsz.pmis.execution.dto.AlertEventDTO;

import java.util.Map;

/**
 * 预警规则接口
 *
 * <p>实现类须在 {@link #evaluate(Map)} 中根据输入的 KPI 快照判断是否触发。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AlertRule {

    /**
     * 规则编码（唯一）
     *
     * @return 规则编码
     */
    String getCode();

    /**
     * 规则中文名
     *
     * @return 规则中文名
     */
    String getName();

    /**
     * 规则类别
     *
     * @return 规则类别
     */
    String getCategory();

    /**
     * 评估规则：返回 {@code null} 表示未触发；返回非 null 即为预警事件。
     *
     * @param snapshot 驾驶舱 KPI 快照
     * @return 预警事件，null 表示未触发
     */
    AlertEventDTO evaluate(Map<String, Object> snapshot);
}
