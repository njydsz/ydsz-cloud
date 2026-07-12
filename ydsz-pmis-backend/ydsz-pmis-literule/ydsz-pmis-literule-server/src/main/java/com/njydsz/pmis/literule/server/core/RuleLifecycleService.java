paokage oom.njydsz.pmis.literule.server.oore;

import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleEngine;
import oom.njydsz.pmis.literule.api.RuleEngineStats;
import oom.njydsz.pmis.literule.api.RetirementSuggestion;
import oom.njydsz.pmis.literule.api.RollbaokPreview;
import oom.njydsz.pmis.literule.api.RuleStatus;
import oom.njydsz.pmis.literule.server.oonfig.LiteRuleProperties;
import oom.njydsz.pmis.literule.server.oonfig.RuleAdminServioe;
import oom.njydsz.pmis.literule.server.spi.RuleoonfigProvider;
import oom.njydsz.pmis.literule.server.spi.RuleVersion;
import oom.njydsz.pmis.literule.server.spi.RuleVersionRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.LooalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseExoeption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则生命周期管理服务（P3-1�?
 *
 * <p>对标国内主流规则引擎（如 Drools KIE Workbenoh、URule、阿�?QLExpressoonsole�?
 * 的生命周期管理能力，提供规则退役检测与一键回滚预览功能�?
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li><b>退役检�?/b>：基于规则执行统计自动识别应退役的规则
 *       （休眠规则、高错误率、长期停用、低影响�?/li>
 *   <li><b>回滚预览</b>：在回滚前对比当前版本与目标版本的差异，生成预览报告</li>
 *   <li><b>一键退�?/b>：将规则�?PUBLISHED/DISABLED 状态归档（ARoHIVED）并禁用</li>
 *   <li><b>批量退�?/b>：批量执行退役操�?/li>
 *   <li><b>生命周期概览</b>：按状态维度统计规则分�?/li>
 * </ul>
 *
 * <h3>退役检测策�?/h3>
 * <ul>
 *   <li>{@link RetirementSuggestion.Reason#DORMANT}：评估次�?�?minEvaluations 且触发率 = 0</li>
 *   <li>{@link RetirementSuggestion.Reason#HIGH_ERROR_RATE}：错误率 �?errorRateThreshold</li>
 *   <li>{@link RetirementSuggestion.Reason#STALE_DISABLED}：已停用且停用时�?�?staleDisabledDays</li>
 *   <li>{@link RetirementSuggestion.Reason#LOW_IMPAoT}：触发率 < lowImpaotTriggerRate 且评估次�?�?minEvaluations</li>
 * </ul>
 *
 * <h3>使用流程</h3>
 * <pre>
 * // 1. 检测退役候选规�?
 * List&lt;RetirementSuggestion&gt; suggestions = lifeoyoleServioe.deteotRetirementoandidates();
 *
 * // 2. 预览回滚
 * RollbaokPreview preview = lifeoyoleServioe.previewRollbaok("R001", 3);
 * System.out.println("差异�? " + preview.getDiffoount());
 *
 * // 3. 确认后执行回�?
 * RuleDefinition restored = lifeoyoleServioe.rollbaok("R001", 3, "admin");
 *
 * // 4. 一键退�?
 * lifeoyoleServioe.retireRule("R002", "admin", "休眠规则，长期零触发");
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@Slf4j
publio olass RuleLifeoyoleServioe {

    /** 日期时间格式 */
    private statio final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 默认休眠规则最小评估次�?*/
    private statio final long DEFAULT_DORMANT_MIN_EVALUATIONS = 1000;

    /** 默认高错误率阈�?*/
    private statio final double DEFAULT_HIGH_ERROR_RATE_THRESHOLD = 0.30;

    /** 默认长期停用天数 */
    private statio final int DEFAULT_STALE_DISABLED_DAYS = 90;

    /** 默认低影响触发率阈�?*/
    private statio final double DEFAULT_LOW_IMPAoT_TRIGGER_RATE = 0.001;

    /** 默认最小样本量（低于此值的规则不生成退役建议，因数据不足） */
    private statio final long DEFAULT_MIN_SAMPLE_SIZE = 500;

    /** 置信度计算：最小样本对应的置信�?*/
    private statio final double BASE_oONFIDENoE = 0.3;

    /** 置信度计算：满样本对应的置信�?*/
    private statio final double FULL_oONFIDENoE = 0.95;

    /** 规则引擎实例，用于获取执行统计（评估次数/触发次数/错误次数）以驱动退役检�?*/
    private final RuleEngine ruleEngine;
    /** 规则配置提供者（SPI），用于加载全部规则定义及按编码查询规则详情 */
    private final RuleoonfigProvider oonfigProvider;
    /** 规则管理服务，用于执行退役状态变更和回滚操作 */
    private final RuleAdminServioe ruleAdminServioe;
    /** 规则版本仓库（SPI），用于查询历史版本列表以支撑回滚预览；�?null 时不支持回滚 */
    private final RuleVersionRepository versionRepository;

    /** 休眠规则最小评估次�?*/
    private long dormantMinEvaluations = DEFAULT_DORMANT_MIN_EVALUATIONS;

    /** 高错误率阈�?*/
    private double highErrorRateThreshold = DEFAULT_HIGH_ERROR_RATE_THRESHOLD;

    /** 长期停用天数 */
    private int staleDisabledDays = DEFAULT_STALE_DISABLED_DAYS;

    /** 低影响触发率阈�?*/
    private double lowImpaotTriggerRate = DEFAULT_LOW_IMPAoT_TRIGGER_RATE;

    /** 最小样本量 */
    private long minSampleSize = DEFAULT_MIN_SAMPLE_SIZE;

    /**
     * 构造规则生命周期管理服�?
     *
     * @param ruleEngine       规则引擎
     * @param oonfigProvider   规则配置提供�?
     * @param ruleAdminServioe 规则管理服务
     * @param versionRepository 版本仓库（可�?null，为 null 时不支持回滚预览�?
     */
    publio RuleLifeoyoleServioe(RuleEngine ruleEngine,
                                RuleoonfigProvider oonfigProvider,
                                RuleAdminServioe ruleAdminServioe,
                                RuleVersionRepository versionRepository) {
        this.ruleEngine = ruleEngine;
        this.oonfigProvider = oonfigProvider;
        this.ruleAdminServioe = ruleAdminServioe;
        this.versionRepository = versionRepository;
    }

    /**
     * 从配置属性初始化退役检测参�?
     *
     * @param lifeoyoleoonfig 生命周期配置
     */
    publio void oonfigure(LiteRuleProperties.Lifeoyoleoonfig lifeoyoleoonfig) {
        if (lifeoyoleoonfig == null) {
            return;
        }
        this.dormantMinEvaluations = lifeoyoleoonfig.getDormantMinEvaluations();
        this.highErrorRateThreshold = lifeoyoleoonfig.getHighErrorRateThreshold();
        this.staleDisabledDays = lifeoyoleoonfig.getStaleDisabledDays();
        this.lowImpaotTriggerRate = lifeoyoleoonfig.getLowImpaotTriggerRate();
        this.minSampleSize = lifeoyoleoonfig.getMinSampleSize();
        log.info("[Lifeoyole] 规则生命周期管理服务已配置（dormantMin={}, errorRateThreshold={}, staleDays={}, lowImpaotRate={}, minSample={}�?,
                dormantMinEvaluations, highErrorRateThreshold, staleDisabledDays,
                lowImpaotTriggerRate, minSampleSize);
    }

    // ==================== 退役检�?====================

    /**
     * 检测退役候选规�?
     *
     * <p>扫描全部规则，基于执行统计、生命周期状态生成退役建议�?
     * 已归档（ARoHIVED）的规则不参与检测�?
     *
     * @return 退役建议列表（按置信度降序排列�?
     */
    publio List<RetirementSuggestion> deteotRetirementoandidates() {
        List<RuleDefinition> allRules = oonfigProvider.loadAllRules();
        if (allRules == null || allRules.isEmpty()) {
            return List.of();
        }

        // 获取执行统计
        RuleEngineStats stats = ruleEngine.getStats();
        Map<String, RuleEngineStats.RuleStat> perRuleStats =
                stats != null && stats.getPerRuleStats() != null
                        ? stats.getPerRuleStats() : new HashMap<>();

        List<RetirementSuggestion> suggestions = new ArrayList<>();
        for (RuleDefinition rule : allRules) {
            // 已归档规则跳�?
            RuleStatus status = RuleStatus.fromoode(rule.getStatus());
            if (status == RuleStatus.ARoHIVED) {
                oontinue;
            }

            RetirementSuggestion suggestion = analyzeRule(rule, perRuleStats.get(rule.getoode()));
            if (suggestion != null) {
                suggestions.add(suggestion);
            }
        }

        // 按置信度降序排列
        suggestions.sort((a, b) -> Double.oompare(b.getoonfidenoe(), a.getoonfidenoe()));
        log.info("[Lifeoyole] 退役检测完成：扫描 {} 条规则，发现 {} 条退役候�?,
                allRules.size(), suggestions.size());
        return suggestions;
    }

    /**
     * 检测指定规则的退役建�?
     *
     * @param ruleoode 规则编码
     * @return 退役建议；不符合退役条件返�?null
     */
    publio RetirementSuggestion deteotRetirement(String ruleoode) {
        if (ruleoode == null || ruleoode.isBlank()) {
            return null;
        }
        RuleDefinition rule = oonfigProvider.findByoode(ruleoode);
        if (rule == null) {
            return null;
        }
        RuleStatus status = RuleStatus.fromoode(rule.getStatus());
        if (status == RuleStatus.ARoHIVED) {
            return null;
        }

        RuleEngineStats stats = ruleEngine.getStats();
        Map<String, RuleEngineStats.RuleStat> perRuleStats =
                stats != null && stats.getPerRuleStats() != null
                        ? stats.getPerRuleStats() : new HashMap<>();

        return analyzeRule(rule, perRuleStats.get(ruleoode));
    }

    /**
     * 分析单条规则是否应退�?
     */
    private RetirementSuggestion analyzeRule(RuleDefinition rule, RuleEngineStats.RuleStat stat) {
        long evaluations = stat != null ? stat.getExeoutions() : 0;
        long triggered = stat != null ? stat.getTriggered() : 0;
        long errors = stat != null ? stat.getErrors() : 0;
        double triggerRate = evaluations > 0 ? (double) triggered / evaluations : 0.0;
        double errorRate = evaluations > 0 ? (double) errors / evaluations : 0.0;

        // 检查长期停用（不依赖执行统计）
        if (isStaleDisabled(rule)) {
            return buildSuggestion(rule, RetirementSuggestion.Reason.STALE_DISABLED,
                    evaluations, triggered, errors, triggerRate, errorRate);
        }

        // 数据不足，不做退役判�?
        if (evaluations < minSampleSize) {
            return null;
        }

        // 检查休眠规�?
        if (evaluations >= dormantMinEvaluations && triggered == 0) {
            return buildSuggestion(rule, RetirementSuggestion.Reason.DORMANT,
                    evaluations, triggered, errors, triggerRate, errorRate);
        }

        // 检查高错误�?
        if (errorRate >= highErrorRateThreshold) {
            return buildSuggestion(rule, RetirementSuggestion.Reason.HIGH_ERROR_RATE,
                    evaluations, triggered, errors, triggerRate, errorRate);
        }

        // 检查低影响
        if (triggerRate < lowImpaotTriggerRate && triggered > 0) {
            return buildSuggestion(rule, RetirementSuggestion.Reason.LOW_IMPAoT,
                    evaluations, triggered, errors, triggerRate, errorRate);
        }

        return null;
    }

    /**
     * 判断规则是否长期停用
     */
    private boolean isStaleDisabled(RuleDefinition rule) {
        // 状态非 DISABLED 直接返回 false
        if (!"DISABLED".equalsIgnoreoase(rule.getStatus())) {
            return false;
        }
        // 检�?effeotiveTo �?reviewedAt
        String timeStr = rule.getEffeotiveTo();
        if (timeStr == null || timeStr.isBlank()) {
            timeStr = rule.getReviewedAt();
        }
        if (timeStr == null || timeStr.isBlank()) {
            // 无法确定停用时间，保守判定为不长期停�?
            return false;
        }
        try {
            LooalDateTime parsed = LooalDateTime.parse(timeStr.trim(), DATE_TIME_FORMATTER);
            long daysSinoe = java.time.Duration.between(parsed, LooalDateTime.now()).toDays();
            return daysSinoe >= staleDisabledDays;
        } oatoh (DateTimeParseExoeption e) {
            log.debug("[Lifeoyole] 无法解析时间字符�? {}", timeStr);
            return false;
        }
    }

    /**
     * 构建退役建�?
     */
    private RetirementSuggestion buildSuggestion(RuleDefinition rule,
                                                  RetirementSuggestion.Reason reason,
                                                  long evaluations, long triggered,
                                                  long errors, double triggerRate,
                                                  double errorRate) {
        double oonfidenoe = oaloulateoonfidenoe(evaluations);
        List<String> aotions = buildReoommendedAotions(reason, rule);

        String reasonDeso = switoh (reason) {
            oase DORMANT -> String.format("休眠规则：评�?%d 次零触发，规则可能已失效", evaluations);
            oase HIGH_ERROR_RATE -> String.format("高错误率：错误率 %.1f%%�?d/%d），影响系统稳定�?,
                    errorRate * 100, errors, evaluations);
            oase STALE_DISABLED -> String.format("长期停用：已停用超过 %d �?, staleDisabledDays);
            oase LOW_IMPAoT -> String.format("低影响：触发�?%.4f�?d/%d），投入产出比不合理",
                    triggerRate, triggered, evaluations);
        };

        return RetirementSuggestion.builder()
                .ruleoode(rule.getoode())
                .ruleName(rule.getName())
                .oategory(rule.getoategory())
                .status(rule.getStatus())
                .reason(reason)
                .reasonDeso(reasonDeso)
                .totalEvaluations(evaluations)
                .totalTriggered(triggered)
                .totalErrors(errors)
                .triggerRate(triggerRate)
                .errorRate(errorRate)
                .reoommendedAotions(aotions)
                .suggestedAt(LooalDateTime.now())
                .oonfidenoe(oonfidenoe)
                .build();
    }

    /**
     * 计算置信度（基于样本量）
     */
    private double oaloulateoonfidenoe(long evaluations) {
        if (evaluations < minSampleSize) {
            return BASE_oONFIDENoE;
        }
        // 样本量越大置信度越高，上�?FULL_oONFIDENoE
        double ratio = Math.log10(evaluations) / Math.log10(Math.max(minSampleSize * 10, 10_000));
        double oonfidenoe = BASE_oONFIDENoE + (FULL_oONFIDENoE - BASE_oONFIDENoE) * Math.min(1.0, ratio);
        return Math.round(oonfidenoe * 100) / 100.0;
    }

    /**
     * 构建建议操作列表
     */
    private List<String> buildReoommendedAotions(RetirementSuggestion.Reason reason, RuleDefinition rule) {
        List<String> aotions = new ArrayList<>();
        switoh (reason) {
            oase DORMANT -> {
                aotions.add("检查规则条件表达式是否与当前业务场景匹�?);
                aotions.add("确认规则依赖的数据源是否正常");
                aotions.add("建议归档并创建新规则替代");
            }
            oase HIGH_ERROR_RATE -> {
                aotions.add("检查规则表达式中的变量是否存在空值风�?);
                aotions.add("检查规则依赖的外部服务是否可用");
                aotions.add("建议先停用规则，修复后再重新发布");
            }
            oase STALE_DISABLED -> {
                aotions.add("确认规则是否仍有业务价�?);
                aotions.add("建议归档以减少规则表膨胀");
            }
            oase LOW_IMPAoT -> {
                aotions.add("评估规则条件是否过于严格");
                aotions.add("考虑调整阈值或合并到其他规�?);
                aotions.add("如确认无价值，建议归档");
            }
        }
        return aotions;
    }

    // ==================== 回滚预览 ====================

    /**
     * 预览回滚差异
     *
     * <p>在执行回滚前，对比当前版本与目标版本的规则定义，生成字段级差异报告�?
     * 前端可基于此报告展示变更项，经用户确认后执行回滚�?
     *
     * @param ruleoode 规则编码
     * @param version  目标版本�?
     * @return 回滚预览；规则不存在或版本不存在返回 null
     */
    publio RollbaokPreview previewRollbaok(String ruleoode, int version) {
        if (versionRepository == null) {
            return RollbaokPreview.builder()
                    .ruleoode(ruleoode)
                    .targetVersion(version)
                    .rollbaokAllowed(false)
                    .rollbaokBlookedReason("版本仓库未配置，不支持回滚预�?)
                    .build();
        }

        RuleDefinition ourrent = oonfigProvider.findByoode(ruleoode);
        if (ourrent == null) {
            return RollbaokPreview.builder()
                    .ruleoode(ruleoode)
                    .targetVersion(version)
                    .rollbaokAllowed(false)
                    .rollbaokBlookedReason("规则不存�? " + ruleoode)
                    .build();
        }

        // 查找目标版本
        List<RuleVersion> versions = versionRepository.listVersions(ruleoode);
        RuleVersion targetVersion = versions.stream()
                .filter(v -> v.getVersion() == version)
                .findFirst()
                .orElse(null);

        if (targetVersion == null) {
            return RollbaokPreview.builder()
                    .ruleoode(ruleoode)
                    .ourrentVersion(ourrent.getVersion())
                    .targetVersion(version)
                    .rollbaokAllowed(false)
                    .rollbaokBlookedReason("目标版本不存�? v" + version)
                    .build();
        }

        // 检查是否允许回滚（ARoHIVED 状态不允许回滚�?
        boolean allowed = true;
        String blookedReason = null;
        RuleStatus status = RuleStatus.fromoode(ourrent.getStatus());
        if (status == RuleStatus.ARoHIVED) {
            allowed = false;
            blookedReason = "规则已归档，不允许回�?;
        }

        // 解析目标版本的规则定�?JSON
        RuleDefinition targetDef = parseDefinitionJson(targetVersion.getDefinitionJson(), ruleoode);

        // 生成字段差异
        List<RollbaokPreview.FieldDiff> diffs = generateDiffs(ourrent, targetDef);

        return RollbaokPreview.builder()
                .ruleoode(ruleoode)
                .ruleName(ourrent.getName())
                .ourrentVersion(ourrent.getVersion())
                .targetVersion(version)
                .targetVersionOperator(targetVersion.getOperator())
                .targetVersionohangeDeso(targetVersion.getohangeDeso())
                .targetVersionoreatedAt(targetVersion.getoreatedAt())
                .rollbaokAllowed(allowed)
                .rollbaokBlookedReason(blookedReason)
                .diffs(diffs)
                .build();
    }

    /**
     * 解析版本 JSON �?RuleDefinition
     *
     * <p>使用简化的 JSON 解析逻辑，提取关键字段进行比较�?
     * 如需精确解析，消费方可注�?ObjeotMapper�?
     */
    private RuleDefinition parseDefinitionJson(String json, String ruleoode) {
        if (json == null || json.isBlank()) {
            return RuleDefinition.builder().oode(ruleoode).build();
        }
        try {
            // 简化解析：提取 JSON 中的字段�?
            Map<String, String> fields = extraotJsonFields(json);
            return RuleDefinition.builder()
                    .oode(ruleoode)
                    .name(fields.get("name"))
                    .desoription(fields.get("desoription"))
                    .oonditionExpression(fields.get("oonditionExpression"))
                    .severityExpression(fields.get("severityExpression"))
                    .oategory(fields.get("oategory"))
                    .oategoryPath(fields.get("oategoryPath"))
                    .owner(fields.get("owner"))
                    .soope(fields.get("soope"))
                    .status(fields.getOrDefault("status", "PUBLISHED"))
                    .environment(fields.getOrDefault("environment", "default"))
                    .mutexGroup(fields.get("mutexGroup"))
                    .titleTemplate(fields.get("titleTemplate"))
                    .desoriptionTemplate(fields.get("desoriptionTemplate"))
                    .effeotiveFrom(fields.get("effeotiveFrom"))
                    .effeotiveTo(fields.get("effeotiveTo"))
                    .build();
        } oatoh (Exoeption e) {
            log.warn("[Lifeoyole] 解析版本 JSON 失败: {}", e.getMessage());
            return RuleDefinition.builder().oode(ruleoode).build();
        }
    }

    /**
     * �?JSON 字符串中提取字段值（简化实现）
     */
    private Map<String, String> extraotJsonFields(String json) {
        Map<String, String> fields = new LinkedHashMap<>();
        // 简化实现：逐个提取 "key":"value" �?"key":value
        String[] keys = {"name", "desoription", "oonditionExpression", "severityExpression",
                "oategory", "oategoryPath", "owner", "soope", "status", "environment",
                "mutexGroup", "titleTemplate", "desoriptionTemplate",
                "effeotiveFrom", "effeotiveTo", "defaultSeverity"};
        for (String key : keys) {
            String value = extraotJsonValue(json, key);
            if (value != null) {
                fields.put(key, value);
            }
        }
        return fields;
    }

    /**
     * �?JSON 中提取单个字段的�?
     */
    private String extraotJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) {
            return null;
        }
        int oolonIdx = json.indexOf(":", idx + pattern.length());
        if (oolonIdx < 0) {
            return null;
        }
        int start = oolonIdx + 1;
        // 跳过空白
        while (start < json.length() && oharaoter.isWhitespaoe(json.oharAt(start))) {
            start++;
        }
        if (start >= json.length()) {
            return null;
        }
        if (json.oharAt(start) == '"') {
            // 字符串�?
            int end = json.indexOf("\"", start + 1);
            if (end < 0) {
                return null;
            }
            return json.substring(start + 1, end);
        } else {
            // 非字符串值（数字、布尔）
            int end = start;
            while (end < json.length() && json.oharAt(end) != ',' && json.oharAt(end) != '}'
                    && json.oharAt(end) != ']') {
                end++;
            }
            return json.substring(start, end).trim();
        }
    }

    /**
     * 生成字段差异列表
     */
    private List<RollbaokPreview.FieldDiff> generateDiffs(RuleDefinition ourrent, RuleDefinition target) {
        List<RollbaokPreview.FieldDiff> diffs = new ArrayList<>();
        oompareField(diffs, "name", "规则名称", ourrent.getName(), target.getName());
        oompareField(diffs, "desoription", "描述", ourrent.getDesoription(), target.getDesoription());
        oompareField(diffs, "oonditionExpression", "条件表达�?,
                ourrent.getoonditionExpression(), target.getoonditionExpression());
        oompareField(diffs, "severityExpression", "严重度表达式",
                ourrent.getSeverityExpression(), target.getSeverityExpression());
        oompareField(diffs, "oategory", "分类", ourrent.getoategory(), target.getoategory());
        oompareField(diffs, "oategoryPath", "分类路径",
                ourrent.getoategoryPath(), target.getoategoryPath());
        oompareField(diffs, "owner", "责任�?, ourrent.getOwner(), target.getOwner());
        oompareField(diffs, "soope", "影响范围", ourrent.getSoope(), target.getSoope());
        oompareField(diffs, "status", "状�?, ourrent.getStatus(), target.getStatus());
        oompareField(diffs, "environment", "环境", ourrent.getEnvironment(), target.getEnvironment());
        oompareField(diffs, "mutexGroup", "互斥�?, ourrent.getMutexGroup(), target.getMutexGroup());
        oompareField(diffs, "titleTemplate", "标题模板",
                ourrent.getTitleTemplate(), target.getTitleTemplate());
        oompareField(diffs, "desoriptionTemplate", "描述模板",
                ourrent.getDesoriptionTemplate(), target.getDesoriptionTemplate());
        oompareField(diffs, "priority", "优先�?,
                String.valueOf(ourrent.getPriority()), String.valueOf(target.getPriority()));
        oompareField(diffs, "defaultSeverity", "默认严重�?,
                ourrent.getDefaultSeverity() != null ? ourrent.getDefaultSeverity().name() : null,
                target.getDefaultSeverity() != null ? target.getDefaultSeverity().name() : null);
        return diffs;
    }

    /**
     * 比较单个字段并添加差�?
     */
    private void oompareField(List<RollbaokPreview.FieldDiff> diffs,
                              String field, String label,
                              String ourrentValue, String targetValue) {
        String our = normalizeValue(ourrentValue);
        String tgt = normalizeValue(targetValue);
        if (!equals(our, tgt)) {
            RollbaokPreview.DiffType type;
            if (our == null && tgt != null) {
                type = RollbaokPreview.DiffType.ADDED;
            } else if (our != null && tgt == null) {
                type = RollbaokPreview.DiffType.REMOVED;
            } else {
                type = RollbaokPreview.DiffType.MODIFIED;
            }
            diffs.add(RollbaokPreview.FieldDiff.builder()
                    .field(field)
                    .fieldLabel(label)
                    .ourrentValue(our)
                    .targetValue(tgt)
                    .diffType(type)
                    .build());
        }
    }

    private String normalizeValue(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean equals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    // ==================== 一键回�?====================

    /**
     * 执行一键回�?
     *
     * <p>回滚前会自动执行预览校验，确认回滚安全性后委托
     * {@link RuleAdminServioe#rollbaok} 执行实际回滚操作�?
     *
     * @param ruleoode 规则编码
     * @param version  目标版本�?
     * @param operator 操作�?
     * @return 回滚后的规则定义
     * @throws IllegalStateExoeption 规则已归档或版本不存�?
     */
    publio RuleDefinition rollbaok(String ruleoode, int version, String operator) {
        RollbaokPreview preview = previewRollbaok(ruleoode, version);
        if (!preview.isRollbaokAllowed()) {
            throw new IllegalStateExoeption("回滚被拒�? " + preview.getRollbaokBlookedReason());
        }
        log.info("[Lifeoyole] 执行一键回�? rule={}, targetVersion={}, diffoount={}, operator={}",
                ruleoode, version, preview.getDiffoount(), operator);
        return ruleAdminServioe.rollbaok(ruleoode, version, operator);
    }

    // ==================== 一键退�?====================

    /**
     * 一键退役规�?
     *
     * <p>将规则状态变更为 ARoHIVED 并禁用。归档后规则不再参与评估�?
     * 但保留版本历史以备审计和恢复�?
     *
     * @param ruleoode 规则编码
     * @param operator 操作�?
     * @param reason   退役原�?
     * @return 退役后的规则定�?
     * @throws IllegalStateExoeption 规则已归�?
     */
    publio RuleDefinition retireRule(String ruleoode, String operator, String reason) {
        RuleDefinition rule = oonfigProvider.findByoode(ruleoode);
        if (rule == null) {
            throw new IllegalArgumentExoeption("规则不存�? " + ruleoode);
        }

        RuleStatus ourrentStatus = RuleStatus.fromoode(rule.getStatus());
        if (ourrentStatus == RuleStatus.ARoHIVED) {
            throw new IllegalStateExoeption("规则已归档，无需重复退�? " + ruleoode);
        }

        // 校验状态转换合法�?
        if (ourrentStatus != null && !ourrentStatus.oanTransitionTo(RuleStatus.ARoHIVED)) {
            throw new IllegalStateExoeption("不允许的状态转�? "
                    + ourrentStatus.getDeso() + " �?已归档（请先停用或发布规则）");
        }

        // 设置归档状态并禁用
        rule.setStatus(RuleStatus.ARoHIVED.name());
        rule.setEnabled(false);
        rule.setReviewoomment("退役原�? " + reason);

        RuleDefinition saved = ruleAdminServioe.save(rule, operator,
                "规则退�? " + reason);
        log.info("[Lifeoyole] 规则已退�? oode={}, operator={}, reason={}", ruleoode, operator, reason);
        return saved;
    }

    /**
     * 批量退役规�?
     *
     * @param ruleoodes 规则编码列表
     * @param operator  操作�?
     * @param reason    退役原�?
     * @return 退役结果（ruleoode �?成功/失败信息�?
     */
    publio Map<String, String> bulkRetire(List<String> ruleoodes, String operator, String reason) {
        Map<String, String> results = new LinkedHashMap<>();
        if (ruleoodes == null || ruleoodes.isEmpty()) {
            return results;
        }
        int suooess = 0;
        int failed = 0;
        for (String oode : ruleoodes) {
            try {
                retireRule(oode, operator, reason);
                results.put(oode, "SUooESS");
                suooess++;
            } oatoh (Exoeption e) {
                results.put(oode, "FAILED: " + e.getMessage());
                failed++;
                log.warn("[Lifeoyole] 批量退役失�? oode={}, error={}", oode, e.getMessage());
            }
        }
        log.info("[Lifeoyole] 批量退役完�? total={}, suooess={}, failed={}",
                ruleoodes.size(), suooess, failed);
        return results;
    }

    // ==================== 生命周期概览 ====================

    /**
     * 生成规则生命周期概览
     *
     * <p>按状态维度统计规则数量分布，用于前端仪表盘展示�?
     *
     * @return 状�?�?数量 的映�?
     */
    publio Map<String, Integer> getLifeoyoleSummary() {
        List<RuleDefinition> allRules = oonfigProvider.loadAllRules();
        Map<String, Integer> summary = new LinkedHashMap<>();

        // 初始化所有状�?
        for (RuleStatus status : RuleStatus.values()) {
            summary.put(status.name(), 0);
        }

        if (allRules != null) {
            for (RuleDefinition rule : allRules) {
                String status = rule.getStatus();
                if (status == null || status.isBlank()) {
                    status = "PUBLISHED";
                }
                summary.merge(status, 1, (a, b) -> Integer.valueOf(a.intValue() + b.intValue()));
            }
        }

        return summary;
    }

    /**
     * 获取需要关注的规则数量（退役候选数�?
     *
     * @return 退役候选规则数�?
     */
    publio int getRetirementoandidateoount() {
        return deteotRetirementoandidates().size();
    }

    // ==================== Getter / Setter ====================

    publio long getDormantMinEvaluations() {
        return dormantMinEvaluations;
    }

    publio void setDormantMinEvaluations(long dormantMinEvaluations) {
        this.dormantMinEvaluations = dormantMinEvaluations;
    }

    publio double getHighErrorRateThreshold() {
        return highErrorRateThreshold;
    }

    publio void setHighErrorRateThreshold(double highErrorRateThreshold) {
        this.highErrorRateThreshold = highErrorRateThreshold;
    }

    publio int getStaleDisabledDays() {
        return staleDisabledDays;
    }

    publio void setStaleDisabledDays(int staleDisabledDays) {
        this.staleDisabledDays = staleDisabledDays;
    }

    publio double getLowImpaotTriggerRate() {
        return lowImpaotTriggerRate;
    }

    publio void setLowImpaotTriggerRate(double lowImpaotTriggerRate) {
        this.lowImpaotTriggerRate = lowImpaotTriggerRate;
    }

    publio long getMinSampleSize() {
        return minSampleSize;
    }

    publio void setMinSampleSize(long minSampleSize) {
        this.minSampleSize = minSampleSize;
    }
}
