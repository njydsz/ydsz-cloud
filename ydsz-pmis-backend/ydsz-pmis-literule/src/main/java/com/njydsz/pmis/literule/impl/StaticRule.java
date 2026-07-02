package com.njydsz.pmis.literule.impl;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;

import java.util.function.Function;

/**
 * 静态规则：包装 Java lambda 作为规则
 *
 * <p>用于编程式注册规则，保持与原 AlertRule 编码习惯兼容。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public class StaticRule implements Rule {

    private final String code;
    private final String name;
    private final String category;
    private final int priority;
    private final Function<RuleContext, RuleResult> evaluator;

    /**
     * 构造静态规则
     *
     * @param code      规则编码
     * @param name      规则名称
     * @param category  类别
     * @param priority  优先级
     * @param evaluator 评估函数
     */
    public StaticRule(String code, String name, String category, int priority,
                      Function<RuleContext, RuleResult> evaluator) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.priority = priority;
        this.evaluator = evaluator;
    }

    /**
     * 构造静态规则（默认优先级）
     *
     * @param code      规则编码
     * @param name      规则名称
     * @param category  类别
     * @param evaluator 评估函数
     */
    public StaticRule(String code, String name, String category,
                      Function<RuleContext, RuleResult> evaluator) {
        this(code, name, category, DEFAULT_PRIORITY, evaluator);
    }

    @Override
    public String getCode() { return code; }

    @Override
    public String getName() { return name; }

    @Override
    public String getCategory() { return category; }

    @Override
    public int getPriority() { return priority; }

    @Override
    public RuleResult evaluate(RuleContext context) {
        RuleResult result = evaluator.apply(context);
        if (result == null) {
            return RuleResult.notTriggered(code);
        }
        return result;
    }
}
