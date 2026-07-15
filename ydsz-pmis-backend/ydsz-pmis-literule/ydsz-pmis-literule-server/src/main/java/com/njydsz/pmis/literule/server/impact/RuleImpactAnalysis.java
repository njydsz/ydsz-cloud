package com.njydsz.pmis.literule.server.impact;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则变更影响分析结果
 *
 * <p>当一条规则被修改/删除/启停时，分析其对其他规则、下游消费者的影响。
 *
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleImpactAnalysis implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 被分析的规则编码 */
    private String ruleCode;

    /** 变更类型 */
    private ChangeType changeType;

    /** 受影响的规则列表 */
    private List<AffectedRule> affectedRules;

    /** 受影响的下游消费者（如告警通道、报表） */
    private List<AffectedConsumer> affectedConsumers;

    /** 共享变量列表（可能受影响的变量） */
    private Set<String> sharedVariables;

    /** 风险等级 */
    private RiskLevel riskLevel;

    /** 风险描述 */
    private String riskDescription;

    /** 建议操作 */
    private List<String> recommendations;

    /**
     * 受影响的规则
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AffectedRule implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 规则编码 */
        private String ruleCode;
        /** 规则名称 */
        private String ruleName;
        /** 影响类型 */
        private String impactType;
        /** 影响描述 */
        private String description;
        /** 是否同互斥组 */
        private boolean sameMutexGroup;
        /** 是否有共享变量 */
        private boolean sharedVariables;
    }

    /**
     * 受影响的下游消费者
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AffectedConsumer implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 消费者名称 */
        private String consumerName;
        /** 消费者类型（ALERT/REPORT/WEBHOOK/DASHBOARD） */
        private String consumerType;
        /** 影响描述 */
        private String description;
    }

    public enum ChangeType {
        CREATE, UPDATE, DELETE, ENABLE, DISABLE, PRIORITY_CHANGE, SEVERITY_CHANGE
    }

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
