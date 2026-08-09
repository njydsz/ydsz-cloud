package com.njydsz.literule.server.cep;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CEP（Complex Event Processing）模式匹配命中结果。
 *
 * <p>当事件流满足 CEP 模式定义的条件时，引擎生成此对象封装命中详情，
 * 包括匹配上的事件序列、命中时间、指标值及上下文元数据。
 *
 * <p>典型使用场景：风控规则中的"短时间内多次失败登录"、运维告警中的"5 分钟内错误率超阈值"等。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CEPHit implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 命中的模式 ID（对应 CEP 模式定义的唯一标识） */
    private String patternId;

    /** 命中的规则编码（关联 {@code ydsz_rule_definition.code}） */
    private String ruleCode;

    /** 匹配上的事件列表（序列模式按顺序，窗口模式按时间） */
    private List<CEPEvent> matchedEvents;

    /** 命中的开始时间（第一个匹配事件的时间戳） */
    private Instant hitAt;

    /** 命中的指标值（窗口计数 / 聚合值，如错误次数、平均延迟） */
    private double metric;

    /** 关联的元数据（如 tenantId、partitionKey，用于下游处理路由） */
    private Map<String, Object> context;
}
