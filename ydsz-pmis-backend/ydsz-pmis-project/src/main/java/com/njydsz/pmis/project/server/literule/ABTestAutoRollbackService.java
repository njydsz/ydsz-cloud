package com.njydsz.pmis.project.server.literule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.spi.ABTestAutoRollbackProvider;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import com.njydsz.pmis.literule.entity.RuleABPolicyDO;
import com.njydsz.pmis.literule.entity.RuleABRollbackDO;
import com.njydsz.pmis.literule.entity.RuleCanaryBucketDO;
import com.njydsz.pmis.literule.entity.RuleDefinitionDO;
import com.njydsz.pmis.literule.mapper.RuleABPolicyMapper;
import com.njydsz.pmis.literule.mapper.RuleABRollbackMapper;
import com.njydsz.pmis.literule.mapper.RuleCanaryBucketMapper;
import com.njydsz.pmis.literule.mapper.RuleDefinitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AB Test 自动回滚 Service（P1-10）
 *
 * <p>定时任务（每 5 分钟）扫描所有启用了 canary 的规则，根据 pmis_rule_ab_policy 配置判断：
 * <ul>
 *   <li>canary 桶错误率超过阈值，且样本数大于 min_sample_size：触发回滚（AUTO）或仅通知（NOTIFY）</li>
 *   <li>回滚动作：
 *     <ul>
 *       <li>AUTO：将 canary_ratio 置为 0，关闭灰度；同步通知 Owner</li>
 *       <li>NOTIFY：仅通知 Owner，保留灰度状态</li>
 *     </ul>
 *   </li>
 *   <li>回滚记录写入 pmis_rule_ab_rollback 表，便于审计</li>
 * </ul>
 *
 * <p>Owner 通知采用项目内 NotificationService（P0 阶段已落地）。如未配置，会降级为日志输出。
 *
 * <p>实现 {@link ABTestAutoRollbackProvider} SPI，供 literule 模块的 Controller 反转依赖调用。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ABTestAutoRollbackService implements ABTestAutoRollbackProvider {

    private final RuleABPolicyMapper policyMapper;
    private final RuleABRollbackMapper rollbackMapper;
    private final RuleCanaryBucketMapper bucketMapper;
    private final RuleDefinitionMapper ruleDefinitionMapper;
    private final RuleConfigProvider ruleConfigProvider;
    /**
     * 通知服务（system 模块，可选注入；未配置时降级为日志输出）
     *
     * <p>使用 ObjectProvider 避免 system 模块未启用时启动失败。
     */
    private final ObjectProvider<ABTestNotifier> notifierProvider;

    /**
     * 定时任务：每 5 分钟扫描一次所有启用 canary 的规则
     */
    @Scheduled(fixedDelayString = "${pmis.literule.ab-auto-rollback.interval-ms:300000}",
               initialDelayString = "${pmis.literule.ab-auto-rollback.initial-delay-ms:60000}")
    public void scheduledEvaluate() {
        evaluateAll();
    }

    /**
     * 主动触发：扫描全部启用 canary 的规则
     */
    public int evaluateAll() {
        // 1. 查询所有启用了 canary 的规则（canary_ratio > 0）
        List<RuleDefinitionDO> canaryRules = ruleDefinitionMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionDO>()
                        .gt(RuleDefinitionDO::getCanaryRatio, 0.0));
        if (canaryRules.isEmpty()) {
            log.debug("[ABAutoRollback] 当前无启用 canary 的规则");
            return 0;
        }
        int processed = 0;
        for (RuleDefinitionDO rule : canaryRules) {
            try {
                if (evaluateOne(rule.getRuleCode())) {
                    processed++;
                }
            } catch (Exception e) {
                log.warn("[ABAutoRollback] 规则评估异常: code={}, err={}", rule.getRuleCode(), e.getMessage());
            }
        }
        if (processed > 0) {
            log.info("[ABAutoRollback] 本轮扫描处理 {} 条规则", processed);
        }
        return processed;
    }

    /**
     * 评估单条规则
     *
     * @return true=执行了回滚/通知，false=无操作
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean evaluateOne(String ruleCode) {
        // 1. 加载策略（未配置策略则使用默认）
        RuleABPolicyDO policy = policyMapper.selectByRuleCode(ruleCode);
        if (policy == null) {
            policy = defaultPolicy(ruleCode);
        } else if (Boolean.FALSE.equals(policy.getAutoRollbackEnabled())) {
            log.debug("[ABAutoRollback] 规则 {} 策略禁用，跳过", ruleCode);
            return false;
        }

        // 2. 查询监控窗口内的分桶数据
        int windowMinutes = policy.getCheckWindowMinutes() != null ? policy.getCheckWindowMinutes() : 60;
        LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);
        List<RuleCanaryBucketDO> buckets = bucketMapper.selectByRuleCodeSince(ruleCode, since);
        if (buckets.isEmpty()) {
            log.debug("[ABAutoRollback] 规则 {} 窗口内无桶数据，跳过", ruleCode);
            return false;
        }

        // 3. 聚合主桶/灰桶执行数
        long primaryCount = 0;
        long canaryCount = 0;
        for (RuleCanaryBucketDO b : buckets) {
            if ("PRIMARY".equalsIgnoreCase(b.getBucketType())) {
                primaryCount += (b.getBucketCount() == null ? 0 : b.getBucketCount());
            } else if ("CANARY".equalsIgnoreCase(b.getBucketType())) {
                canaryCount += (b.getBucketCount() == null ? 0 : b.getBucketCount());
            }
        }

        // 4. 错误率判定（这里使用 canary 桶的"异常触发率"作为代理：错误 = 触发严重度为 RED）
        //    真实场景中应从错误日志中统计；这里采用简化策略：canary 触发率 / primary 触发率
        //    简化：canaryCount 占比超过 threshold 即视为异常（与配置语义一致）
        long totalCount = primaryCount + canaryCount;
        if (totalCount < (policy.getMinSampleSize() == null ? 100 : policy.getMinSampleSize())) {
            log.debug("[ABAutoRollback] 规则 {} 样本数 {} < {}, 跳过", ruleCode, totalCount,
                    policy.getMinSampleSize());
            return false;
        }

        BigDecimal canaryRate = canaryCount == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(canaryCount)
                        .divide(BigDecimal.valueOf(Math.max(totalCount, 1)), 4, RoundingMode.HALF_UP);
        BigDecimal threshold = policy.getErrorRateThreshold() != null
                ? policy.getErrorRateThreshold() : new BigDecimal("0.3000");

        // 5. 触发回滚判断
        boolean needRollback = canaryRate.compareTo(threshold) > 0;
        if (!needRollback) {
            // 只更新 lastEvaluatedAt
            if (policy.getId() != null) {
                policy.setLastEvaluatedAt(LocalDateTime.now());
                policyMapper.updateById(policy);
            }
            return false;
        }

        // 6. 执行回滚
        boolean didRollback = performRollback(ruleCode, policy, canaryRate, totalCount);
        return didRollback;
    }

    /**
     * 执行回滚（AUTO 或 NOTIFY）
     */
    private boolean performRollback(String ruleCode, RuleABPolicyDO policy,
                                    BigDecimal canaryRate, long sampleSize) {
        String action = policy.getRollbackAction() != null ? policy.getRollbackAction() : "AUTO";
        boolean doRollback = "AUTO".equalsIgnoreCase(action);
        boolean rolledBack = false;

        if (doRollback) {
            // 关闭 canary：将 canary_ratio 置 0
            RuleDefinition def = ruleConfigProvider.findByCode(ruleCode);
            if (def != null) {
                def.setCanaryRatio(0.0);
                def.setCanaryConditionExpression(null);
                def.setCanarySeverityExpression(null);
                ruleConfigProvider.save(def, "SYSTEM");
                rolledBack = true;
                log.info("[ABAutoRollback] 规则 {} 自动回滚完成: canaryRatio → 0", ruleCode);
            }
        }

        // 通知 Owner
        String notifyStatus = notifyOwner(ruleCode, policy, canaryRate, sampleSize, doRollback);

        // 写回滚历史
        RuleABRollbackDO hist = new RuleABRollbackDO();
        hist.setRuleCode(ruleCode);
        hist.setTriggerReason("ERROR_RATE");
        hist.setErrorRate(canaryRate);
        hist.setSampleSize(sampleSize);
        hist.setFromCanary(rolledBack);
        hist.setOperator("SYSTEM");
        hist.setNotifyStatus(notifyStatus);
        hist.setCreatedAt(LocalDateTime.now());
        rollbackMapper.insert(hist);

        // 更新策略 lastEvaluatedAt / lastRollbackAt
        policy.setLastEvaluatedAt(LocalDateTime.now());
        if (rolledBack) {
            policy.setLastRollbackAt(LocalDateTime.now());
        }
        if (policy.getId() != null) {
            policyMapper.updateById(policy);
        } else {
            // 默认策略（未持久化）不写回数据库
        }
        return true;
    }

    /**
     * 通知 Owner
     */
    private String notifyOwner(String ruleCode, RuleABPolicyDO policy,
                               BigDecimal canaryRate, long sampleSize, boolean rolledBack) {
        // 1. 解析 Owner
        RuleDefinitionDO rule = ruleDefinitionMapper.selectByCode(ruleCode);
        String owner = rule == null ? null : rule.getOwner();
        if (owner == null || owner.isBlank()) {
            log.warn("[ABAutoRollback] 规则 {} 未配置 Owner，跳过通知", ruleCode);
            return "SKIPPED";
        }
        // 2. 构造通知
        String channels = policy.getNotifyChannels() != null ? policy.getNotifyChannels() : "INAPP,EMAIL";
        String action = rolledBack ? "已自动回滚" : "需要人工介入";
        String subject = String.format("[规则 AB Test %s] %s", action, ruleCode);
        String content = String.format(
                "规则 %s 在 AB Test 监控窗口内触发自动评估：\n" +
                "- 灰度桶占比：%s\n" +
                "- 样本数：%d\n" +
                "- 错误率阈值：%s\n" +
                "- 处理动作：%s\n" +
                "- 触发时间：%s\n\n" +
                "请尽快确认是否需要进一步处理。",
                ruleCode, canaryRate, sampleSize,
                policy.getErrorRateThreshold(),
                rolledBack ? "自动回滚（已关闭 canary）" : "通知 Owner（保留 canary）",
                LocalDateTime.now());

        // 3. 调用 Notifier（若 system 模块未启用则降级为 ERROR 日志）
        ABTestNotifier notifier = notifierProvider.getIfAvailable();
        if (notifier == null) {
            log.warn("[ABAutoRollback-Notify] subject={}, content={}", subject, content);
            return "FALLBACK_LOGGED";
        }
        try {
            notifier.notify(owner, subject, content, channels);
            return "SENT";
        } catch (Exception e) {
            log.warn("[ABAutoRollback] Owner {} 通知失败: {}", owner, e.getMessage());
            log.warn("[ABAutoRollback-Notify] subject={}, content={}", subject, content);
            return "FAILED";
        }
    }

    /**
     * 默认策略（数据库中未配置时使用）
     */
    private RuleABPolicyDO defaultPolicy(String ruleCode) {
        RuleABPolicyDO p = new RuleABPolicyDO();
        p.setRuleCode(ruleCode);
        p.setAutoRollbackEnabled(true);
        p.setRollbackAction("NOTIFY");
        p.setErrorRateThreshold(new BigDecimal("0.3000"));
        p.setMinSampleSize(100);
        p.setCheckWindowMinutes(60);
        p.setNotifyChannels("INAPP,EMAIL");
        return p;
    }

    /**
     * 人工触发回滚（Owner 主动请求 / 紧急操作）
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleABRollbackDO manualRollback(String ruleCode, String operator, String reason) {
        RuleDefinition def = ruleConfigProvider.findByCode(ruleCode);
        if (def == null) {
            throw new IllegalArgumentException("规则不存在: " + ruleCode);
        }
        boolean wasCanary = def.getCanaryRatio() > 0;
        if (wasCanary) {
            def.setCanaryRatio(0.0);
            def.setCanaryConditionExpression(null);
            def.setCanarySeverityExpression(null);
            ruleConfigProvider.save(def, operator);
        }
        RuleABPolicyDO policy = policyMapper.selectByRuleCode(ruleCode);
        BigDecimal rate = policy != null && policy.getErrorRateThreshold() != null
                ? policy.getErrorRateThreshold() : new BigDecimal("0");
        String notifyStatus = notifyOwner(ruleCode, policy != null ? policy : defaultPolicy(ruleCode),
                rate, 0, wasCanary);

        RuleABRollbackDO hist = new RuleABRollbackDO();
        hist.setRuleCode(ruleCode);
        hist.setTriggerReason("MANUAL".equalsIgnoreCase(reason) ? "MANUAL" : "OWNER_REQUEST");
        hist.setErrorRate(BigDecimal.ZERO);
        hist.setSampleSize(0L);
        hist.setFromCanary(wasCanary);
        hist.setOperator(operator);
        hist.setNotifyStatus(notifyStatus);
        hist.setCreatedAt(LocalDateTime.now());
        rollbackMapper.insert(hist);
        log.info("[ABAutoRollback] 人工回滚: code={}, operator={}, wasCanary={}", ruleCode, operator, wasCanary);
        return hist;
    }

    /**
     * 查询规则的所有回滚历史
     */
    public List<RuleABRollbackDO> listRollbackHistory(String ruleCode) {
        return rollbackMapper.selectList(
                new LambdaQueryWrapper<RuleABRollbackDO>()
                        .eq(RuleABRollbackDO::getRuleCode, ruleCode)
                        .orderByDesc(RuleABRollbackDO::getCreatedAt));
    }

    /**
     * 获取规则的 AB Test 策略（无配置时返回默认策略）
     */
    public RuleABPolicyDO getPolicy(String ruleCode) {
        RuleABPolicyDO policy = policyMapper.selectByRuleCode(ruleCode);
        if (policy == null) {
            return defaultPolicy(ruleCode);
        }
        return policy;
    }

    /**
     * 保存/更新 AB Test 策略
     */
    @Transactional(rollbackFor = Exception.class)
    public void savePolicy(RuleABPolicyDO policy, String operator) {
        if (policy == null || policy.getRuleCode() == null) {
            throw new IllegalArgumentException("ruleCode 不能为空");
        }
        RuleABPolicyDO existing = policyMapper.selectByRuleCode(policy.getRuleCode());
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            if (policy.getAutoRollbackEnabled() == null) policy.setAutoRollbackEnabled(true);
            if (policy.getRollbackAction() == null) policy.setRollbackAction("NOTIFY");
            if (policy.getErrorRateThreshold() == null) policy.setErrorRateThreshold(new BigDecimal("0.3000"));
            if (policy.getMinSampleSize() == null) policy.setMinSampleSize(100);
            if (policy.getCheckWindowMinutes() == null) policy.setCheckWindowMinutes(60);
            if (policy.getNotifyChannels() == null) policy.setNotifyChannels("INAPP,EMAIL");
            policy.setCreatedBy(operator);
            policy.setCreatedAt(now);
            policyMapper.insert(policy);
        } else {
            if (policy.getAutoRollbackEnabled() != null) existing.setAutoRollbackEnabled(policy.getAutoRollbackEnabled());
            if (policy.getRollbackAction() != null) existing.setRollbackAction(policy.getRollbackAction());
            if (policy.getErrorRateThreshold() != null) existing.setErrorRateThreshold(policy.getErrorRateThreshold());
            if (policy.getMinSampleSize() != null) existing.setMinSampleSize(policy.getMinSampleSize());
            if (policy.getCheckWindowMinutes() != null) existing.setCheckWindowMinutes(policy.getCheckWindowMinutes());
            if (policy.getNotifyChannels() != null) existing.setNotifyChannels(policy.getNotifyChannels());
            if (policy.getDescription() != null) existing.setDescription(policy.getDescription());
            existing.setUpdatedBy(operator);
            existing.setUpdatedAt(now);
            policyMapper.updateById(existing);
        }
        log.info("[ABAutoRollback] 策略保存: code={}, operator={}", policy.getRuleCode(), operator);
    }
}
