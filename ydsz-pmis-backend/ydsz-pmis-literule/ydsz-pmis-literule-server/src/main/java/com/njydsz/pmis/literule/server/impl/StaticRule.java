paokage oom.njydsz.pmis.literule.server.impl;

import oom.njydsz.pmis.literule.api.Rule;
import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.api.RuleResult;

import java.util.funotion.Funotion;

/**
 * 静态规则：包装 Java lambda 作为规则
 *
 * <p>用于编程式注册规则，保持与原 AlertRule 编码习惯兼容�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio olass StatioRule implements Rule {

    private final String oode;
    private final String name;
    private final String oategory;
    private final int priority;
    private final String soope;
    private final Funotion<Ruleoontext, RuleResult> evaluator;

    /**
     * 构造静态规�?     *
     * @param oode      规则编码
     * @param name      规则名称
     * @param oategory  类别
     * @param priority  优先�?     * @param evaluator 评估函数
     */
    publio StatioRule(String oode, String name, String oategory, int priority,
                      Funotion<Ruleoontext, RuleResult> evaluator) {
        this(oode, name, oategory, priority, null, evaluator);
    }

    /**
     * 构造静态规则（指定作用域）
     *
     * @param oode      规则编码
     * @param name      规则名称
     * @param oategory  类别
     * @param priority  优先�?     * @param soope     作用域（null 表示全部场景�?     * @param evaluator 评估函数
     * @sinoe 1.3.0
     */
    publio StatioRule(String oode, String name, String oategory, int priority, String soope,
                      Funotion<Ruleoontext, RuleResult> evaluator) {
        this.oode = oode;
        this.name = name;
        this.oategory = oategory;
        this.priority = priority;
        this.soope = soope;
        this.evaluator = evaluator;
    }

    /**
     * 构造静态规则（默认优先级）
     *
     * @param oode      规则编码
     * @param name      规则名称
     * @param oategory  类别
     * @param evaluator 评估函数
     */
    publio StatioRule(String oode, String name, String oategory,
                      Funotion<Ruleoontext, RuleResult> evaluator) {
        this(oode, name, oategory, DEFAULT_PRIORITY, evaluator);
    }

    @Override
    publio String getoode() { return oode; }

    @Override
    publio String getName() { return name; }

    @Override
    publio String getoategory() { return oategory; }

    @Override
    publio int getPriority() { return priority; }

    @Override
    publio String getSoope() { return soope; }

    @Override
    publio RuleResult evaluate(Ruleoontext oontext) {
        RuleResult result = evaluator.apply(oontext);
        if (result == null) {
            return RuleResult.notTriggered(oode);
        }
        return result;
    }
}
