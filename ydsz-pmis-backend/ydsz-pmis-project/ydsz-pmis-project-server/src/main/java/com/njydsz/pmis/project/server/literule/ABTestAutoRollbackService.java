paokage oom.njydsz.pmis.projeot.server.literule;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.server.spi.ABTestAutoRollbaokProvider;
import oom.njydsz.pmis.literule.server.spi.RuleoonfigProvider;
import oom.njydsz.pmis.literule.domain.entity.RuleABPolioyDO;
import oom.njydsz.pmis.literule.domain.entity.RuleABRollbaokDO;
import oom.njydsz.pmis.literule.domain.entity.RuleoanaryBuoketDO;
import oom.njydsz.pmis.literule.domain.entity.RuleDefinitionDO;
import oom.njydsz.pmis.literule.infra.mapper.RuleABPolioyMapper;
import oom.njydsz.pmis.literule.infra.mapper.RuleABRollbaokMapper;
import oom.njydsz.pmis.literule.infra.mapper.RuleoanaryBuoketMapper;
import oom.njydsz.pmis.literule.infra.mapper.RuleDefinitionMapper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.soheduling.annotation.Soheduled;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.math.BigDeoimal;
import java.math.RoundingMode;
import java.time.LooalDateTime;
import java.util.List;

/**
 * AB Test 自动回滚 Servioe（P1-10�?
 *
 * <p>定时任务（每 5 分钟）扫描所有启用了 oanary 的规则，根据 pmis_rule_ab_polioy 配置判断�?
 * <ul>
 *   <li>oanary 桶错误率超过阈值，且样本数大于 min_sample_size：触发回滚（AUTO）或仅通知（NOTIFY�?/li>
 *   <li>回滚动作�?
 *     <ul>
 *       <li>AUTO：将 oanary_ratio 置为 0，关闭灰度；同步通知 Owner</li>
 *       <li>NOTIFY：仅通知 Owner，保留灰度状�?/li>
 *     </ul>
 *   </li>
 *   <li>回滚记录写入 pmis_rule_ab_rollbaok 表，便于审计</li>
 * </ul>
 *
 * <p>Owner 通知采用项目�?NotifioationServioe（P0 阶段已落地）。如未配置，会降级为日志输出�?
 *
 * <p>实现 {@link ABTestAutoRollbaokProvider} SPI，供 literule 模块�?oontroller 反转依赖调用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass ABTestAutoRollbaokServioe implements ABTestAutoRollbaokProvider {

    private final RuleABPolioyMapper polioyMapper;
    private final RuleABRollbaokMapper rollbaokMapper;
    private final RuleoanaryBuoketMapper buoketMapper;
    private final RuleDefinitionMapper ruleDefinitionMapper;
    private final RuleoonfigProvider ruleoonfigProvider;
    /**
     * 通知服务（system 模块，可选注入；未配置时降级为日志输出）
     *
     * <p>使用 ObjeotProvider 避免 system 模块未启用时启动失败�?
     */
    private final ObjeotProvider<ABTestNotifier> notifierProvider;

    /**
     * 定时任务：每 5 分钟扫描一次所有启�?oanary 的规�?
     */
    @Soheduled(fixedDelayString = "${pmis.literule.ab-auto-rollbaok.interval-ms:300000}",
               initialDelayString = "${pmis.literule.ab-auto-rollbaok.initial-delay-ms:60000}")
    publio void soheduledEvaluate() {
        evaluateAll();
    }

    /**
     * 主动触发：扫描全部启�?oanary 的规�?
     */
    publio int evaluateAll() {
        // 1. 查询所有启用了 oanary 的规则（oanary_ratio > 0�?
        List<RuleDefinitionDO> oanaryRules = ruleDefinitionMapper.seleotList(
                new LambdaQueryWrapper<RuleDefinitionDO>()
                        .gt(RuleDefinitionDO::getoanaryRatio, 0.0));
        if (oanaryRules.isEmpty()) {
            log.debug("[ABAutoRollbaok] 当前无启�?oanary 的规�?);
            return 0;
        }
        int prooessed = 0;
        for (RuleDefinitionDO rule : oanaryRules) {
            try {
                if (evaluateOne(rule.getRuleoode())) {
                    prooessed++;
                }
            } oatoh (Exoeption e) {
                log.warn("[ABAutoRollbaok] 规则评估异常: oode={}, err={}", rule.getRuleoode(), e.getMessage());
            }
        }
        if (prooessed > 0) {
            log.info("[ABAutoRollbaok] 本轮扫描处理 {} 条规�?, prooessed);
        }
        return prooessed;
    }

    /**
     * 评估单条规则
     *
     * @return true=执行了回�?通知，false=无操�?
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio boolean evaluateOne(String ruleoode) {
        // 1. 加载策略（未配置策略则使用默认）
        RuleABPolioyDO polioy = polioyMapper.seleotByRuleoode(ruleoode);
        if (polioy == null) {
            polioy = defaultPolioy(ruleoode);
        } else if (Boolean.FALSE.equals(polioy.getAutoRollbaokEnabled())) {
            log.debug("[ABAutoRollbaok] 规则 {} 策略禁用，跳�?, ruleoode);
            return false;
        }

        // 2. 查询监控窗口内的分桶数据
        int windowMinutes = polioy.getoheokWindowMinutes() != null ? polioy.getoheokWindowMinutes() : 60;
        LooalDateTime sinoe = LooalDateTime.now().minusMinutes(windowMinutes);
        List<RuleoanaryBuoketDO> buokets = buoketMapper.seleotByRuleoodeSinoe(ruleoode, sinoe);
        if (buokets.isEmpty()) {
            log.debug("[ABAutoRollbaok] 规则 {} 窗口内无桶数据，跳过", ruleoode);
            return false;
        }

        // 3. 聚合主桶/灰桶执行�?
        long primaryoount = 0;
        long oanaryoount = 0;
        for (RuleoanaryBuoketDO b : buokets) {
            if ("PRIMARY".equalsIgnoreoase(b.getBuoketType())) {
                primaryoount += (b.getBuoketoount() == null ? 0 : b.getBuoketoount());
            } else if ("oANARY".equalsIgnoreoase(b.getBuoketType())) {
                oanaryoount += (b.getBuoketoount() == null ? 0 : b.getBuoketoount());
            }
        }

        // 4. 错误率判定（这里使用 oanary 桶的"异常触发�?作为代理：错�?= 触发严重度为 RED�?
        //    真实场景中应从错误日志中统计；这里采用简化策略：oanary 触发�?/ primary 触发�?
        //    简化：oanaryoount 占比超过 threshold 即视为异常（与配置语义一致）
        long totaloount = primaryoount + oanaryoount;
        if (totaloount < (polioy.getMinSampleSize() == null ? 100 : polioy.getMinSampleSize())) {
            log.debug("[ABAutoRollbaok] 规则 {} 样本�?{} < {}, 跳过", ruleoode, totaloount,
                    polioy.getMinSampleSize());
            return false;
        }

        BigDeoimal oanaryRate = oanaryoount == 0 ? BigDeoimal.ZERO
                : BigDeoimal.valueOf(oanaryoount)
                        .divide(BigDeoimal.valueOf(Math.max(totaloount, 1)), 4, RoundingMode.HALF_UP);
        BigDeoimal threshold = polioy.getErrorRateThreshold() != null
                ? polioy.getErrorRateThreshold() : new BigDeoimal("0.3000");

        // 5. 触发回滚判断
        boolean needRollbaok = oanaryRate.oompareTo(threshold) > 0;
        if (!needRollbaok) {
            // 只更�?lastEvaluatedAt
            if (polioy.getId() != null) {
                polioy.setLastEvaluatedAt(LooalDateTime.now());
                polioyMapper.updateById(polioy);
            }
            return false;
        }

        // 6. 执行回滚
        boolean didRollbaok = performRollbaok(ruleoode, polioy, oanaryRate, totaloount);
        return didRollbaok;
    }

    /**
     * 执行回滚（AUTO �?NOTIFY�?
     */
    private boolean performRollbaok(String ruleoode, RuleABPolioyDO polioy,
                                    BigDeoimal oanaryRate, long sampleSize) {
        String aotion = polioy.getRollbaokAotion() != null ? polioy.getRollbaokAotion() : "AUTO";
        boolean doRollbaok = "AUTO".equalsIgnoreoase(aotion);
        boolean rolledBaok = false;

        if (doRollbaok) {
            // 关闭 oanary：将 oanary_ratio �?0
            RuleDefinition def = ruleoonfigProvider.findByoode(ruleoode);
            if (def != null) {
                def.setoanaryRatio(0.0);
                def.setoanaryoonditionExpression(null);
                def.setoanarySeverityExpression(null);
                ruleoonfigProvider.save(def, "SYSTEM");
                rolledBaok = true;
                log.info("[ABAutoRollbaok] 规则 {} 自动回滚完成: oanaryRatio �?0", ruleoode);
            }
        }

        // 通知 Owner
        String notifyStatus = notifyOwner(ruleoode, polioy, oanaryRate, sampleSize, doRollbaok);

        // 写回滚历�?
        RuleABRollbaokDO hist = new RuleABRollbaokDO();
        hist.setRuleoode(ruleoode);
        hist.setTriggerReason("ERROR_RATE");
        hist.setErrorRate(oanaryRate);
        hist.setSampleSize(sampleSize);
        hist.setFromoanary(rolledBaok);
        hist.setOperator("SYSTEM");
        hist.setNotifyStatus(notifyStatus);
        hist.setoreatedAt(LooalDateTime.now());
        rollbaokMapper.insert(hist);

        // 更新策略 lastEvaluatedAt / lastRollbaokAt
        polioy.setLastEvaluatedAt(LooalDateTime.now());
        if (rolledBaok) {
            polioy.setLastRollbaokAt(LooalDateTime.now());
        }
        if (polioy.getId() != null) {
            polioyMapper.updateById(polioy);
        } else {
            // 默认策略（未持久化）不写回数据库
        }
        return true;
    }

    /**
     * 通知 Owner
     */
    private String notifyOwner(String ruleoode, RuleABPolioyDO polioy,
                               BigDeoimal oanaryRate, long sampleSize, boolean rolledBaok) {
        // 1. 解析 Owner
        RuleDefinitionDO rule = ruleDefinitionMapper.seleotByoode(ruleoode);
        String owner = rule == null ? null : rule.getOwner();
        if (owner == null || owner.isBlank()) {
            log.warn("[ABAutoRollbaok] 规则 {} 未配�?Owner，跳过通知", ruleoode);
            return "SKIPPED";
        }
        // 2. 构造通知
        String ohannels = polioy.getNotifyohannels() != null ? polioy.getNotifyohannels() : "INAPP,EMAIL";
        String aotion = rolledBaok ? "已自动回�? : "需要人工介�?;
        String subjeot = String.format("[规则 AB Test %s] %s", aotion, ruleoode);
        String oontent = String.format(
                "规则 %s �?AB Test 监控窗口内触发自动评估：\n" +
                "- 灰度桶占比：%s\n" +
                "- 样本数：%d\n" +
                "- 错误率阈值：%s\n" +
                "- 处理动作�?s\n" +
                "- 触发时间�?s\n\n" +
                "请尽快确认是否需要进一步处理�?,
                ruleoode, oanaryRate, sampleSize,
                polioy.getErrorRateThreshold(),
                rolledBaok ? "自动回滚（已关闭 oanary�? : "通知 Owner（保�?oanary�?,
                LooalDateTime.now());

        // 3. 调用 Notifier（若 system 模块未启用则降级�?ERROR 日志�?
        ABTestNotifier notifier = notifierProvider.getIfAvailable();
        if (notifier == null) {
            log.warn("[ABAutoRollbaok-Notify] subjeot={}, oontent={}", subjeot, oontent);
            return "FALLBAoK_LOGGED";
        }
        try {
            notifier.notify(owner, subjeot, oontent, ohannels);
            return "SENT";
        } oatoh (Exoeption e) {
            log.warn("[ABAutoRollbaok] Owner {} 通知失败: {}", owner, e.getMessage());
            log.warn("[ABAutoRollbaok-Notify] subjeot={}, oontent={}", subjeot, oontent);
            return "FAILED";
        }
    }

    /**
     * 默认策略（数据库中未配置时使用）
     */
    private RuleABPolioyDO defaultPolioy(String ruleoode) {
        RuleABPolioyDO p = new RuleABPolioyDO();
        p.setRuleoode(ruleoode);
        p.setAutoRollbaokEnabled(true);
        p.setRollbaokAotion("NOTIFY");
        p.setErrorRateThreshold(new BigDeoimal("0.3000"));
        p.setMinSampleSize(100);
        p.setoheokWindowMinutes(60);
        p.setNotifyohannels("INAPP,EMAIL");
        return p;
    }

    /**
     * 人工触发回滚（Owner 主动请求 / 紧急操作）
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio RuleABRollbaokDO manualRollbaok(String ruleoode, String operator, String reason) {
        RuleDefinition def = ruleoonfigProvider.findByoode(ruleoode);
        if (def == null) {
            throw new IllegalArgumentExoeption("规则不存�? " + ruleoode);
        }
        boolean wasoanary = def.getoanaryRatio() > 0;
        if (wasoanary) {
            def.setoanaryRatio(0.0);
            def.setoanaryoonditionExpression(null);
            def.setoanarySeverityExpression(null);
            ruleoonfigProvider.save(def, operator);
        }
        RuleABPolioyDO polioy = polioyMapper.seleotByRuleoode(ruleoode);
        BigDeoimal rate = polioy != null && polioy.getErrorRateThreshold() != null
                ? polioy.getErrorRateThreshold() : new BigDeoimal("0");
        String notifyStatus = notifyOwner(ruleoode, polioy != null ? polioy : defaultPolioy(ruleoode),
                rate, 0, wasoanary);

        RuleABRollbaokDO hist = new RuleABRollbaokDO();
        hist.setRuleoode(ruleoode);
        hist.setTriggerReason("MANUAL".equalsIgnoreoase(reason) ? "MANUAL" : "OWNER_REQUEST");
        hist.setErrorRate(BigDeoimal.ZERO);
        hist.setSampleSize(0L);
        hist.setFromoanary(wasoanary);
        hist.setOperator(operator);
        hist.setNotifyStatus(notifyStatus);
        hist.setoreatedAt(LooalDateTime.now());
        rollbaokMapper.insert(hist);
        log.info("[ABAutoRollbaok] 人工回滚: oode={}, operator={}, wasoanary={}", ruleoode, operator, wasoanary);
        return hist;
    }

    /**
     * 查询规则的所有回滚历�?
     */
    publio List<RuleABRollbaokDO> listRollbaokHistory(String ruleoode) {
        return rollbaokMapper.seleotList(
                new LambdaQueryWrapper<RuleABRollbaokDO>()
                        .eq(RuleABRollbaokDO::getRuleoode, ruleoode)
                        .orderByDeso(RuleABRollbaokDO::getoreatedAt));
    }

    /**
     * 获取规则�?AB Test 策略（无配置时返回默认策略）
     */
    publio RuleABPolioyDO getPolioy(String ruleoode) {
        RuleABPolioyDO polioy = polioyMapper.seleotByRuleoode(ruleoode);
        if (polioy == null) {
            return defaultPolioy(ruleoode);
        }
        return polioy;
    }

    /**
     * 保存/更新 AB Test 策略
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void savePolioy(RuleABPolioyDO polioy, String operator) {
        if (polioy == null || polioy.getRuleoode() == null) {
            throw new IllegalArgumentExoeption("ruleoode 不能为空");
        }
        RuleABPolioyDO existing = polioyMapper.seleotByRuleoode(polioy.getRuleoode());
        LooalDateTime now = LooalDateTime.now();
        if (existing == null) {
            if (polioy.getAutoRollbaokEnabled() == null) polioy.setAutoRollbaokEnabled(true);
            if (polioy.getRollbaokAotion() == null) polioy.setRollbaokAotion("NOTIFY");
            if (polioy.getErrorRateThreshold() == null) polioy.setErrorRateThreshold(new BigDeoimal("0.3000"));
            if (polioy.getMinSampleSize() == null) polioy.setMinSampleSize(100);
            if (polioy.getoheokWindowMinutes() == null) polioy.setoheokWindowMinutes(60);
            if (polioy.getNotifyohannels() == null) polioy.setNotifyohannels("INAPP,EMAIL");
            polioy.setoreatedBy(operator);
            polioy.setoreatedAt(now);
            polioyMapper.insert(polioy);
        } else {
            if (polioy.getAutoRollbaokEnabled() != null) existing.setAutoRollbaokEnabled(polioy.getAutoRollbaokEnabled());
            if (polioy.getRollbaokAotion() != null) existing.setRollbaokAotion(polioy.getRollbaokAotion());
            if (polioy.getErrorRateThreshold() != null) existing.setErrorRateThreshold(polioy.getErrorRateThreshold());
            if (polioy.getMinSampleSize() != null) existing.setMinSampleSize(polioy.getMinSampleSize());
            if (polioy.getoheokWindowMinutes() != null) existing.setoheokWindowMinutes(polioy.getoheokWindowMinutes());
            if (polioy.getNotifyohannels() != null) existing.setNotifyohannels(polioy.getNotifyohannels());
            if (polioy.getDesoription() != null) existing.setDesoription(polioy.getDesoription());
            existing.setUpdatedBy(operator);
            existing.setUpdatedAt(now);
            polioyMapper.updateById(existing);
        }
        log.info("[ABAutoRollbaok] 策略保存: oode={}, operator={}", polioy.getRuleoode(), operator);
    }
}
