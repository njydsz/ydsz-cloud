package com.njydsz.pmis.literule.cep;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * CEP 模式匹配命中结果
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CEPHit implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 命中的模式 ID */
    private String patternId;
    /** 命中的规则编码 */
    private String ruleCode;
    /** 匹配上的事件列表（序列模式按顺序，窗口模式按时间） */
    private List<CEPEvent> matchedEvents;
    /** 命中的开始时间 */
    private Instant hitAt;
    /** 命中的指标值（窗口计数 / 聚合值） */
    private double metric;
    /** 关联的元数据（如 tenantId、partitionKey） */
    private Map<String, Object> context;
}
