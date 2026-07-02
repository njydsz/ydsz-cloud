package com.njydsz.pmis.literule.api;

/**
 * 规则接口
 *
 * <p>所有规则（Java 编码规则 / 表达式规则 / 数据库配置规则）均实现此接口。
 * 引擎遍历已注册规则，调用 {@link #evaluate(RuleContext)} 进行评估。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface Rule {

    /** 默认优先级（数值越小优先级越高） */
    int DEFAULT_PRIORITY = 100;

    /**
     * 规则编码（全局唯一）
     *
     * @return 规则编码
     */
    String getCode();

    /**
     * 规则名称（中文）
     *
     * @return 规则名称
     */
    String getName();

    /**
     * 规则类别（如 EVM / COST / BENCH / UTILIZATION）
     *
     * @return 规则类别
     */
    String getCategory();

    /**
     * 优先级（数值越小越先执行，默认 100）
     *
     * @return 优先级
     */
    default int getPriority() {
        return DEFAULT_PRIORITY;
    }

    /**
     * 评估规则
     *
     * @param context 规则上下文（事实数据）
     * @return 评估结果；未触发时返回 {@link RuleResult#notTriggered(String)}
     */
    RuleResult evaluate(RuleContext context);
}
