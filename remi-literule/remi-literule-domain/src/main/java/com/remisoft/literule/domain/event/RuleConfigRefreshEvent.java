package com.remisoft.literule.domain.event;

/**
 * 规则配置刷新事件
 *
 * <p>当规则配置发生变更（新增/修改/删除/启停）时发布此事件，
 * 引擎监听后重新加载规则定义并热刷新注册表。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class RuleConfigRefreshEvent {

    /** 变更的规则编码（null 表示全量刷新） */
    private final String ruleCode;

    /** 变更类型 */
    private final ChangeType changeType;

    /** 操作人 */
    private final String operator;

    /**
     * 变更类型枚举
     */
    public enum ChangeType {
        CREATE, UPDATE, DELETE, TOGGLE, FULL_RELOAD
    }

    /**
     * 构造全量刷新事件
     *
     * @param operator 操作人
     * @return 事件实例
     */
    public static RuleConfigRefreshEvent fullReload(String operator) {
        return new RuleConfigRefreshEvent(null, ChangeType.FULL_RELOAD, operator);
    }

    /**
     * 构造单条规则变更事件
     *
     * @param ruleCode   规则编码
     * @param changeType 变更类型
     * @param operator   操作人
     * @return 事件实例
     */
    public static RuleConfigRefreshEvent of(String ruleCode, ChangeType changeType, String operator) {
        return new RuleConfigRefreshEvent(ruleCode, changeType, operator);
    }

    public RuleConfigRefreshEvent(String ruleCode, ChangeType changeType, String operator) {
        this.ruleCode = ruleCode;
        this.changeType = changeType;
        this.operator = operator;
    }

    public String getRuleCode() { return ruleCode; }
    public ChangeType getChangeType() { return changeType; }
    public String getOperator() { return operator; }
}
