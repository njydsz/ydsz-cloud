package com.njydsz.literule.server.spi;

import java.util.List;

import com.njydsz.literule.domain.entity.RuleABPolicy;
import com.njydsz.literule.domain.entity.RuleABRollback;

/**
 * AB Test 自动回滚提供者 SPI
 *
 * <p>由消费方（如 project 模块）提供实现，提供 AB Test 策略管理、自动回滚评估、
 * 人工回滚、回滚历史查询等能力。将原有 {@code ABTestAutoRollbackService} 的能力抽象为 SPI，
 * 避免 literule 模块直接依赖 project 模块。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface ABTestAutoRollbackProvider {

    /**
     * 获取规则的 AB Test 策略（无配置时返回默认策略）
     *
     * @param ruleCode 规则编码
     * @return AB Test 策略
     */
    RuleABPolicy getPolicy(String ruleCode);

    /**
     * 保存/更新 AB Test 策略
     *
     * @param policy   策略
     * @param operator 操作人
     */
    void savePolicy(RuleABPolicy policy, String operator);

    /**
     * 查询规则的所有回滚历史
     *
     * @param ruleCode 规则编码
     * @return 回滚历史列表
     */
    List<RuleABRollback> listRollbackHistory(String ruleCode);

    /**
     * 评估单条规则
     *
     * @param ruleCode 规则编码
     * @return true=执行了回滚/通知，false=无操作
     */
    boolean evaluateOne(String ruleCode);

    /**
     * 人工触发回滚（Owner 主动请求 / 紧急操作）
     *
     * @param ruleCode 规则编码
     * @param operator 操作人
     * @param reason   MANUAL / OWNER_REQUEST
     * @return 回滚记录
     */
    RuleABRollback manualRollback(String ruleCode, String operator, String reason);
}
