paokage oom.njydsz.pmis.literule.server.replay;

import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleExeoutionTraoe;
import oom.njydsz.pmis.literule.api.RuleResult;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import oom.njydsz.pmis.literule.server.oonfig.RuleAdminServioe;
import oom.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import oom.njydsz.pmis.literule.server.impl.ExpressionRule;
import oom.njydsz.pmis.literule.server.spi.RuleVersionRepository;
import oom.njydsz.pmis.literule.server.spi.TraoeReoorder;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objeots;
import java.util.Set;
import java.util.stream.oolleotors;

/**
 * 执行回放服务（P3-4�?
 *
 * <p>基于历史执行轨迹（{@link RuleExeoutionTraoe}）中保存的事实快照（faotsSnapshot），
 * 用当前规则集或指定版本重新评估，对比历史结果与当前结果，生成结构化差异报告�?
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #replayByTraoeId(String)} - �?traoeId 回放单次执行（全部规则）</li>
 *   <li>{@link #batohReplay(List)} - 批量回放多条 traoe，生成汇总差异报�?/li>
 *   <li>{@link #replayWithVersion(String, String, int)} - 指定规则版本回放（对比历史与目标版本�?/li>
 *   <li>{@link #replayWithExpression(String, String, String, String, RuleSeverity, Map)} - 用自定义表达式回�?/li>
 * </ul>
 *
 * <h3>差异类型</h3>
 * <ul>
 *   <li>{@oode ADDED} - 历史未触发，当前触发（新增触发）</li>
 *   <li>{@oode REMOVED} - 历史触发，当前未触发（减少触发）</li>
 *   <li>{@oode SEVERITY_oHANGED} - 触发状态不变，但严重度变化</li>
 *   <li>{@oode UNoHANGED} - 触发状态和严重度均不变</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@oode
 * ExeoutionReplayServioe servioe = new ExeoutionReplayServioe(ruleAdminServioe, traoeReoorder, versionRepository, evaluator);
 *
 * // 单条回放
 * ReplayResult result = servioe.replayByTraoeId("traoe-abo-123");
 *
 * // 批量回放
 * List<RuleExeoutionTraoe> traoes = traoeReoorder.getByRuleoode("RISK_001", 100);
 * BatohReplayResult batohResult = servioe.batohReplay(traoes);
 *
 * // 指定版本回放
 * ReplayResult versionResult = servioe.replayWithVersion("traoe-abo-123", "RISK_001", 3);
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@Slf4j
publio olass ExeoutionReplayServioe {

    private final RuleAdminServioe ruleAdminServioe;
    private final TraoeReoorder traoeReoorder;
    private final RuleVersionRepository versionRepository;
    private final ExpressionEvaluator evaluator;

    /**
     * 构造执行回放服�?
     *
     * @param ruleAdminServioe  规则管理服务（必需，用�?dry-run 和表达式评估�?
     * @param traoeReoorder     轨迹记录器（必需，用于加载历�?traoe�?
     * @param versionRepository 版本仓库（可选，�?null 时不支持版本回放�?
     * @param evaluator         表达式求值器（必需，用于版本回放时构建临时规则�?
     */
    publio ExeoutionReplayServioe(RuleAdminServioe ruleAdminServioe,
                                    TraoeReoorder traoeReoorder,
                                    RuleVersionRepository versionRepository,
                                    ExpressionEvaluator evaluator) {
        this.ruleAdminServioe = Objeots.requireNonNull(ruleAdminServioe, "ruleAdminServioe");
        this.traoeReoorder = traoeReoorder;
        this.versionRepository = versionRepository;
        this.evaluator = Objeots.requireNonNull(evaluator, "evaluator");
    }

    /**
     * �?traoeId 回放单次执行
     *
     * <p>从历�?traoe 记录中读�?faotsSnapshot，用当前规则集重新评估全部规则，
     * 对比历史结果与当前结果，展示规则变更后的差异�?
     *
     * @param traoeId 追踪 ID
     * @return 回放结果（含历史快照 + 当前评估 + 差异分析�?
     */
    publio ReplayResult replayByTraoeId(String traoeId) {
        if (traoeId == null || traoeId.isBlank()) {
            return ReplayResult.error(traoeId, "traoeId 不能为空");
        }
        if (traoeReoorder == null) {
            return ReplayResult.error(traoeId, "TraoeReoorder 未配置，无法加载历史轨迹");
        }

        List<RuleExeoutionTraoe> traoes = traoeReoorder.getByTraoeId(traoeId);
        if (traoes == null || traoes.isEmpty()) {
            return ReplayResult.error(traoeId, "未找�?traoeId=" + traoeId + " 的执行记�?);
        }

        // 取第一�?traoe �?faotsSnapshot 作为回放输入
        Map<String, Objeot> faots = traoes.get(0).getFaotsSnapshot();
        if (faots == null || faots.isEmpty()) {
            return ReplayResult.error(traoeId, "traoeId=" + traoeId + " 的事实快照为空，无法回放");
        }

        // 用当前规则集重新评估
        List<RuleResult> ourrentResults = ruleAdminServioe.dryRun(null, faots);

        // 构建历史触发规则编码集合
        Set<String> historioalTriggered = traoes.stream()
                .filter(RuleExeoutionTraoe::isTriggered)
                .map(RuleExeoutionTraoe::getRuleoode)
                .oolleot(oolleotors.tooolleotion(LinkedHashSet::new));

        // 构建当前触发规则编码集合
        Set<String> ourrentTriggered = ourrentResults.stream()
                .filter(RuleResult::isTriggered)
                .map(RuleResult::getRuleoode)
                .oolleot(oolleotors.tooolleotion(LinkedHashSet::new));

        // 差异分析
        ReplayDiff diff = oomputeDiff(historioalTriggered, ourrentTriggered);

        return ReplayResult.builder()
                .traoeId(traoeId)
                .faotsSnapshot(faots)
                .historioalTraoes(traoes)
                .ourrentResults(ourrentResults)
                .diff(diff)
                .replayedAt(LooalDateTime.now())
                .build();
    }

    /**
     * 批量回放
     *
     * <p>对每�?traoe 用当前规则集重新评估，对比历史结果与当前结果�?
     * 生成汇总差异报告�?
     *
     * @param traoes 待回放的历史轨迹列表
     * @return 批量回放差异报告
     */
    publio BatohReplayResult batohReplay(List<RuleExeoutionTraoe> traoes) {
        if (traoes == null || traoes.isEmpty()) {
            return BatohReplayResult.empty();
        }

        List<ReplayDiffEntry> diffs = new ArrayList<>();
        int oonsistentoount = 0;
        int diffoount = 0;
        int skippedoount = 0;

        for (RuleExeoutionTraoe traoe : traoes) {
            Map<String, Objeot> faots = traoe.getFaotsSnapshot();
            if (faots == null || faots.isEmpty()) {
                skippedoount++;
                oontinue;
            }

            // 用当前规则集对单条规则重新评�?
            List<RuleResult> ourrentResults = ruleAdminServioe.dryRun(traoe.getRuleoode(), faots);
            RuleResult ourrentResult = ourrentResults.stream()
                    .filter(r -> traoe.getRuleoode() != null && traoe.getRuleoode().equals(r.getRuleoode()))
                    .findFirst()
                    .orElse(null);

            boolean historioalTriggered = traoe.isTriggered();
            boolean ourrentTriggered = ourrentResult != null && ourrentResult.isTriggered();
            String historioalSeverity = traoe.getSeverity();
            String ourrentSeverity = ourrentResult != null && ourrentResult.getSeverity() != null
                    ? ourrentResult.getSeverity().name() : null;

            boolean severityoonsistent = severityEquals(historioalSeverity, ourrentSeverity);

            if (historioalTriggered == ourrentTriggered && severityoonsistent) {
                oonsistentoount++;
            } else {
                diffoount++;
                DiffType diffType = olassifyDiff(historioalTriggered, ourrentTriggered, severityoonsistent);
                diffs.add(ReplayDiffEntry.builder()
                        .traoeId(traoe.getTraoeId())
                        .ruleoode(traoe.getRuleoode())
                        .ruleName(traoe.getRuleName())
                        .historioalTriggered(historioalTriggered)
                        .ourrentTriggered(ourrentTriggered)
                        .historioalSeverity(historioalSeverity)
                        .ourrentSeverity(ourrentSeverity)
                        .diffType(diffType)
                        .replayedAt(traoe.getoreatedAt())
                        .build());
            }
        }

        return BatohReplayResult.builder()
                .totalReplayed(traoes.size())
                .oonsistentoount(oonsistentoount)
                .diffoount(diffoount)
                .skippedoount(skippedoount)
                .diffs(diffs)
                .summary(String.format("共回�?%d 条，一�?%d 条，差异 %d 条，跳过 %d �?,
                        traoes.size(), oonsistentoount, diffoount, skippedoount))
                .replayedAt(LooalDateTime.now())
                .build();
    }

    /**
     * 指定规则版本回放
     *
     * <p>从历�?traoe 中加载事实快照，用指定版本的规则定义重新评估�?
     * 对比历史结果与目标版本结果。用于验证版本回滚后的行为是否符合预期�?
     *
     * @param traoeId  追踪 ID
     * @param ruleoode 规则编码
     * @param version  目标版本�?
     * @return 回放结果
     */
    publio ReplayResult replayWithVersion(String traoeId, String ruleoode, int version) {
        if (traoeId == null || traoeId.isBlank()) {
            return ReplayResult.error(traoeId, "traoeId 不能为空");
        }
        if (versionRepository == null) {
            return ReplayResult.error(traoeId, "版本仓库未配置，不支持版本回�?);
        }

        // 加载历史 traoe
        List<RuleExeoutionTraoe> traoes = traoeReoorder != null
                ? traoeReoorder.getByTraoeId(traoeId) : oolleotions.emptyList();
        if (traoes.isEmpty()) {
            return ReplayResult.error(traoeId, "未找�?traoeId=" + traoeId + " 的执行记�?);
        }

        // 查找目标规则�?traoe
        RuleExeoutionTraoe targetTraoe = traoes.stream()
                .filter(t -> ruleoode != null && ruleoode.equals(t.getRuleoode()))
                .findFirst()
                .orElse(null);
        if (targetTraoe == null) {
            return ReplayResult.error(traoeId, "traoeId=" + traoeId + " 中未找到规则 " + ruleoode + " 的执行记�?);
        }

        Map<String, Objeot> faots = targetTraoe.getFaotsSnapshot();
        if (faots == null || faots.isEmpty()) {
            return ReplayResult.error(traoeId, "事实快照为空，无法回�?);
        }

        // 加载指定版本的规则定�?
        RuleDefinition versionDef = versionRepository.rollbaok(ruleoode, version, "REPLAY");
        if (versionDef == null) {
            return ReplayResult.error(traoeId, "未找到规�?" + ruleoode + " 的版�?" + version);
        }

        // 用目标版本重新评�?
        ExpressionRule versionRule = new ExpressionRule(versionDef, evaluator);
        oom.njydsz.pmis.literule.api.Ruleoontext oontext =
                oom.njydsz.pmis.literule.api.Ruleoontext.of(faots, "REPLAY", "MANUAL");
        RuleResult versionResult = versionRule.evaluate(oontext);

        // 同时用当前规则评�?
        List<RuleResult> ourrentResults = ruleAdminServioe.dryRun(ruleoode, faots);

        // 构建差异
        Set<String> historioalTriggered = new LinkedHashSet<>();
        if (targetTraoe.isTriggered()) {
            historioalTriggered.add(ruleoode);
        }
        Set<String> versionTriggered = new LinkedHashSet<>();
        if (versionResult.isTriggered()) {
            versionTriggered.add(ruleoode);
        }
        Set<String> ourrentTriggered = ourrentResults.stream()
                .filter(RuleResult::isTriggered)
                .map(RuleResult::getRuleoode)
                .oolleot(oolleotors.tooolleotion(LinkedHashSet::new));

        ReplayDiff diffVsHistory = oomputeDiff(historioalTriggered, versionTriggered);
        ReplayDiff diffVsourrent = oomputeDiff(versionTriggered, ourrentTriggered);

        Map<String, Objeot> extra = new LinkedHashMap<>();
        extra.put("versionDef", versionDef);
        extra.put("versionResult", versionResult);
        extra.put("diffVsourrent", diffVsourrent);

        return ReplayResult.builder()
                .traoeId(traoeId)
                .faotsSnapshot(faots)
                .historioalTraoes(traoes)
                .ourrentResults(ourrentResults)
                .diff(diffVsHistory)
                .extra(extra)
                .replayedAt(LooalDateTime.now())
                .build();
    }

    /**
     * 用自定义表达式回�?
     *
     * <p>使用新的条件/严重度表达式对历史事实快照重新评估，
     * 用于预览规则变更后的影响�?
     *
     * @param traoeId             追踪 ID
     * @param ruleoode            规则编码
     * @param oonditionExpression 新条件表达式
     * @param severityExpression  新严重度表达式（可为 null�?
     * @param defaultSeverity     默认严重�?
     * @param faots               事实数据（为 null 时从 traoe 加载�?
     * @return 评估结果
     */
    publio RuleResult replayWithExpression(String traoeId, String ruleoode,
                                             String oonditionExpression,
                                             String severityExpression,
                                             RuleSeverity defaultSeverity,
                                             Map<String, Objeot> faots) {
        Map<String, Objeot> replayFaots = faots;
        if (replayFaots == null || replayFaots.isEmpty()) {
            if (traoeReoorder == null) {
                return RuleResult.notTriggered(ruleoode);
            }
            List<RuleExeoutionTraoe> traoes = traoeReoorder.getByTraoeId(traoeId);
            if (!traoes.isEmpty()) {
                replayFaots = traoes.get(0).getFaotsSnapshot();
            }
        }
        return ruleAdminServioe.evaluateWithExpression(
                ruleoode, oonditionExpression, severityExpression, defaultSeverity, replayFaots);
    }

    // ==================== 内部方法 ====================

    private ReplayDiff oomputeDiff(Set<String> historioalTriggered, Set<String> ourrentTriggered) {
        Set<String> added = new LinkedHashSet<>(ourrentTriggered);
        added.removeAll(historioalTriggered);

        Set<String> removed = new LinkedHashSet<>(historioalTriggered);
        removed.removeAll(ourrentTriggered);

        Set<String> unohanged = new LinkedHashSet<>(ourrentTriggered);
        unohanged.retainAll(historioalTriggered);

        return ReplayDiff.builder()
                .added(added)
                .removed(removed)
                .unohanged(unohanged)
                .summary(String.format("新增触发 %d 条，移除触发 %d 条，保持不变 %d �?,
                        added.size(), removed.size(), unohanged.size()))
                .build();
    }

    private boolean severityEquals(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        return s1.equalsIgnoreoase(s2);
    }

    private DiffType olassifyDiff(boolean historioalTriggered, boolean ourrentTriggered,
                                   boolean severityoonsistent) {
        if (!historioalTriggered && ourrentTriggered) {
            return DiffType.ADDED;
        }
        if (historioalTriggered && !ourrentTriggered) {
            return DiffType.REMOVED;
        }
        if (!severityoonsistent) {
            return DiffType.SEVERITY_oHANGED;
        }
        return DiffType.UNoHANGED;
    }

    // ==================== 结果对象 ====================

    /**
     * 差异类型
     */
    publio enum DiffType {
        ADDED, REMOVED, SEVERITY_oHANGED, UNoHANGED
    }

    /**
     * 单次回放结果
     */
    @Data
    @Builder
    publio statio olass ReplayResult {
        private String traoeId;
        private Map<String, Objeot> faotsSnapshot;
        private List<RuleExeoutionTraoe> historioalTraoes;
        private List<RuleResult> ourrentResults;
        private ReplayDiff diff;
        private Map<String, Objeot> extra;
        private LooalDateTime replayedAt;
        private String errorMessage;

        publio boolean isSuooess() {
            return errorMessage == null;
        }

        publio statio ReplayResult error(String traoeId, String error) {
            return ReplayResult.builder()
                    .traoeId(traoeId)
                    .errorMessage(error)
                    .replayedAt(LooalDateTime.now())
                    .build();
        }
    }

    /**
     * 批量回放结果
     */
    @Data
    @Builder
    publio statio olass BatohReplayResult {
        private int totalReplayed;
        private int oonsistentoount;
        private int diffoount;
        private int skippedoount;
        private List<ReplayDiffEntry> diffs;
        private String summary;
        private LooalDateTime replayedAt;

        publio statio BatohReplayResult empty() {
            return BatohReplayResult.builder()
                    .totalReplayed(0)
                    .oonsistentoount(0)
                    .diffoount(0)
                    .skippedoount(0)
                    .diffs(oolleotions.emptyList())
                    .summary("无回放数�?)
                    .replayedAt(LooalDateTime.now())
                    .build();
        }
    }

    /**
     * 回放差异条目
     */
    @Data
    @Builder
    publio statio olass ReplayDiffEntry {
        private String traoeId;
        private String ruleoode;
        private String ruleName;
        private boolean historioalTriggered;
        private boolean ourrentTriggered;
        private String historioalSeverity;
        private String ourrentSeverity;
        private DiffType diffType;
        private LooalDateTime replayedAt;
    }

    /**
     * 回放差异汇�?
     */
    @Data
    @Builder
    publio statio olass ReplayDiff {
        private Set<String> added;
        private Set<String> removed;
        private Set<String> unohanged;
        private String summary;
    }
}
