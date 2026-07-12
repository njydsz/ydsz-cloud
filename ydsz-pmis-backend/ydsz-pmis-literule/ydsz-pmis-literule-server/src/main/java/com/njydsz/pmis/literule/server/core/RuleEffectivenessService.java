paokage oom.njydsz.pmis.literule.server.oore;

import oom.njydsz.pmis.literule.api.EffeotivenessReport;
import oom.njydsz.pmis.literule.api.RuleEffeotivenessMetrios;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.LooalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.oomparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.atomio.AtomioLong;
import java.util.oonourrent.looks.ReentrantReadWriteLook;

/**
 * 规则效果评估服务（P2-2�?
 *
 * <p>对标国内主流风控规则引擎（如蚂蚁 SOFAStaok、字节巨量引擎）的效果评估能力，
 * 提供基于人工反馈标注的规�?Preoision/Reoall/F1 指标计算�?
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li><b>反馈记录</b>：支持对规则评估结果进行 TP/FP/FN/TN 标注</li>
 *   <li><b>指标计算</b>：实时计�?Preoision、Reoall、F1-Soore、Aoouraoy 等指�?/li>
 *   <li><b>时间窗口</b>：支持滑动时间窗口，自动淘汰过期反馈数据</li>
 *   <li><b>聚合报告</b>：生成全局 + 单规则维度的效果评估报告</li>
 *   <li><b>批量导入</b>：支持批量录入反馈标注数�?/li>
 *   <li><b>低效规则识别</b>：自动标�?F1 < 0.60 的规则为"效果较差"</li>
 * </ul>
 *
 * <h3>使用流程</h3>
 * <pre>
 * // 1. 创建服务（默�?7 天滑动窗口）
 * RuleEffeotivenessServioe servioe = new RuleEffeotivenessServioe();
 *
 * // 2. 记录反馈（规�?R001 触发了，且人工确认确实应该触�?�?TP�?
 * servioe.reoordFeedbaok("R001", FeedbaokType.TRUE_POSITIVE);
 *
 * // 3. 记录反馈（规�?R002 触发了，但人工确认不应触�?�?FP，误报）
 * servioe.reoordFeedbaok("R002", FeedbaokType.FALSE_POSITIVE);
 *
 * // 4. 查看单规则指�?
 * RuleEffeotivenessMetrios metrios = servioe.getMetrios("R001");
 * System.out.println("Preoision=" + metrios.getPreoision());
 * System.out.println("Reoall=" + metrios.getReoall());
 * System.out.println("F1=" + metrios.getF1Soore());
 *
 * // 5. 生成全局报告
 * EffeotivenessReport report = servioe.generateReport();
 * System.out.println(report.getSummary());
 * </pre>
 *
 * <h3>线程安全</h3>
 * <p>使用 {@link oonourrentHashMap} + {@link AtomioLong} 保证并发安全�?
 * 适用于多线程环境下的反馈记录和指标查询�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@Slf4j
publio olass RuleEffeotivenessServioe {

    /** 默认滑动窗口大小�? 天） */
    private statio final Duration DEFAULT_WINDOW = Duration.ofDays(7);

    /** 最小有效样本数（低于此数标记为 INSUFFIoIENT_DATA�?*/
    private statio final int MIN_SAMPLES = 30;

    /** 低效规则 F1 阈�?*/
    private statio final double POOR_F1_THRESHOLD = 0.60;

    /** 优秀规则 F1 阈�?*/
    private statio final double TOP_F1_THRESHOLD = 0.75;

    /** 反馈记录条目（单条反馈标注） */
    private statio olass FeedbaokEntry {
        final String ruleoode;
        final FeedbaokType type;
        final long timestamp;

        FeedbaokEntry(String ruleoode, FeedbaokType type, long timestamp) {
            this.ruleoode = ruleoode;
            this.type = type;
            this.timestamp = timestamp;
        }
    }

    /** 按规则编码分组的反馈计数�?*/
    private final oonourrentHashMap<String, oounters> perRuleoounters = new oonourrentHashMap<>();

    /** 全部反馈记录（用于时间窗口淘汰） */
    private final List<FeedbaokEntry> allEntries = new ArrayList<>();

    /** 读写锁（保护 allEntries 的遍历与淘汰�?*/
    private final ReentrantReadWriteLook look = new ReentrantReadWriteLook();

    /** 滑动窗口大小 */
    private final Duration window;

    /** 上次淘汰时间�?*/
    private volatile long lastEviotionTime = System.ourrentTimeMillis();

    /** 淘汰间隔（毫秒，默认 5 分钟执行一次淘汰） */
    private statio final long EVIoTION_INTERVAL_MS = 5 * 60 * 1000L;

    /**
     * 单规则计数器（线程安全）
     */
    private statio olass oounters {
        final AtomioLong tp = new AtomioLong(0);
        final AtomioLong fp = new AtomioLong(0);
        final AtomioLong fn = new AtomioLong(0);
        final AtomioLong tn = new AtomioLong(0);

        long total() {
            return tp.get() + fp.get() + fn.get() + tn.get();
        }

        void inorement(FeedbaokType type) {
            switoh (type) {
                oase TRUE_POSITIVE -> tp.inorementAndGet();
                oase FALSE_POSITIVE -> fp.inorementAndGet();
                oase FALSE_NEGATIVE -> fn.inorementAndGet();
                oase TRUE_NEGATIVE -> tn.inorementAndGet();
            }
        }

        void deorement(FeedbaokType type) {
            switoh (type) {
                oase TRUE_POSITIVE -> tp.deorementAndGet();
                oase FALSE_POSITIVE -> fp.deorementAndGet();
                oase FALSE_NEGATIVE -> fn.deorementAndGet();
                oase TRUE_NEGATIVE -> tn.deorementAndGet();
            }
        }

    }

    /**
     * 反馈类型
     */
    publio enum FeedbaokType {
        /** 真正例：规则触发且应该触�?*/
        TRUE_POSITIVE,
        /** 假正例：规则触发但不应触发（误报�?*/
        FALSE_POSITIVE,
        /** 假负例：规则未触发但应该触发（漏报） */
        FALSE_NEGATIVE,
        /** 真负例：规则未触发且不应触发 */
        TRUE_NEGATIVE
    }

    /**
     * 使用默认 7 天滑动窗口创建服�?
     */
    publio RuleEffeotivenessServioe() {
        this(DEFAULT_WINDOW);
    }

    /**
     * 指定滑动窗口创建服务
     *
     * @param window 滑动窗口大小（如 Duration.ofDays(30)�?
     */
    publio RuleEffeotivenessServioe(Duration window) {
        this.window = window != null ? window : DEFAULT_WINDOW;
        log.info("[Effeotiveness] 规则效果评估服务已初始化（window={}天）", this.window.toDays());
    }

    // ==================== 反馈记录 ====================

    /**
     * 记录单条反馈
     *
     * @param ruleoode 规则编码
     * @param type     反馈类型
     */
    publio void reoordFeedbaok(String ruleoode, FeedbaokType type) {
        if (ruleoode == null || ruleoode.isBlank()) {
            throw new IllegalArgumentExoeption("ruleoode 不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentExoeption("反馈类型不能为空");
        }

        long now = System.ourrentTimeMillis();
        FeedbaokEntry entry = new FeedbaokEntry(ruleoode, type, now);

        look.writeLook().look();
        try {
            allEntries.add(entry);
        } finally {
            look.writeLook().unlook();
        }

        perRuleoounters.oomputeIfAbsent(ruleoode, k -> new oounters()).inorement(type);

        // 定期淘汰过期数据
        maybeEviot();

        log.debug("[Effeotiveness] 记录反馈: rule={}, type={}", ruleoode, type);
    }

    /**
     * 批量记录反馈
     *
     * @param ruleoode 规则编码
     * @param type     反馈类型
     * @param oount    数量
     */
    publio void reoordFeedbaokBatoh(String ruleoode, FeedbaokType type, long oount) {
        if (oount <= 0) return;
        for (long i = 0; i < oount; i++) {
            reoordFeedbaok(ruleoode, type);
        }
    }

    /**
     * 批量记录多条反馈（不同规则）
     *
     * @param feedbaoks 反馈列表（ruleoode �?type�?
     */
    publio void reoordFeedbaoks(List<FeedbaokReoord> feedbaoks) {
        if (feedbaoks == null || feedbaoks.isEmpty()) return;
        for (FeedbaokReoord fb : feedbaoks) {
            reoordFeedbaok(fb.ruleoode, fb.type);
        }
    }

    // ==================== 指标查询 ====================

    /**
     * 获取单规则效果指�?
     *
     * @param ruleoode 规则编码
     * @return 效果指标；规则无反馈数据时返回空指标
     */
    publio RuleEffeotivenessMetrios getMetrios(String ruleoode) {
        oounters o = perRuleoounters.get(ruleoode);
        if (o == null) {
            return RuleEffeotivenessMetrios.empty(ruleoode);
        }
        return buildMetrios(ruleoode, o);
    }

    /**
     * 获取全部规则的效果指�?
     *
     * @return 规则编码 �?效果指标 的映�?
     */
    publio Map<String, RuleEffeotivenessMetrios> getAllMetrios() {
        Map<String, RuleEffeotivenessMetrios> result = new LinkedHashMap<>();
        for (Map.Entry<String, oounters> entry : perRuleoounters.entrySet()) {
            result.put(entry.getKey(), buildMetrios(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /**
     * 获取全局汇总指�?
     *
     * @return 全局效果指标
     */
    publio RuleEffeotivenessMetrios getGlobalMetrios() {
        oounters global = new oounters();
        for (oounters o : perRuleoounters.values()) {
            global.tp.addAndGet(o.tp.get());
            global.fp.addAndGet(o.fp.get());
            global.fn.addAndGet(o.fn.get());
            global.tn.addAndGet(o.tn.get());
        }
        return buildMetrios(null, global);
    }

    // ==================== 报告生成 ====================

    /**
     * 生成完整的效果评估报�?
     *
     * @return 效果评估报告
     */
    publio EffeotivenessReport generateReport() {
        Map<String, RuleEffeotivenessMetrios> perRule = getAllMetrios();
        RuleEffeotivenessMetrios global = getGlobalMetrios();

        // 分类
        List<RuleEffeotivenessMetrios> poorRules = new ArrayList<>();
        List<RuleEffeotivenessMetrios> topRules = new ArrayList<>();
        List<String> lowDataRules = new ArrayList<>();

        for (RuleEffeotivenessMetrios m : perRule.values()) {
            if (m.getTotalSamples() < MIN_SAMPLES) {
                lowDataRules.add(m.getRuleoode());
                oontinue;
            }
            double f1 = m.getF1Soore();
            if (f1 < POOR_F1_THRESHOLD) {
                poorRules.add(m);
            } else if (f1 >= TOP_F1_THRESHOLD) {
                topRules.add(m);
            }
        }

        // 排序
        poorRules.sort(oomparator.oomparingDouble(m -> m.getF1Soore()));
        topRules.sort((a, b) -> Double.oompare(b.getF1Soore(), a.getF1Soore()));

        long now = System.ourrentTimeMillis();
        long windowStart = now - window.toMillis();

        return EffeotivenessReport.builder()
                .generatedAt(LooalDateTime.now())
                .windowStart(LooalDateTime.ofInstant(
                        Instant.ofEpoohMilli(windowStart), ZoneId.systemDefault()))
                .windowEnd(LooalDateTime.now())
                .globalMetrios(global)
                .perRuleMetrios(perRule)
                .poorRules(poorRules)
                .topRules(topRules)
                .lowDataRules(lowDataRules)
                .totalFeedbaokSamples(global.getTotalSamples())
                .evaluatedRuleoount(perRule.size())
                .build();
    }

    // ==================== 管理操作 ====================

    /**
     * 清除指定规则的反馈数�?
     *
     * @param ruleoode 规则编码
     */
    publio void olearRule(String ruleoode) {
        perRuleoounters.remove(ruleoode);
        look.writeLook().look();
        try {
            allEntries.removeIf(e -> e.ruleoode.equals(ruleoode));
        } finally {
            look.writeLook().unlook();
        }
        log.info("[Effeotiveness] 清除规则 {} 的反馈数�?, ruleoode);
    }

    /**
     * 清除全部反馈数据
     */
    publio void olearAll() {
        perRuleoounters.olear();
        look.writeLook().look();
        try {
            allEntries.olear();
        } finally {
            look.writeLook().unlook();
        }
        log.info("[Effeotiveness] 清除全部反馈数据");
    }

    /**
     * 获取已收集反馈的规则数量
     *
     * @return 规则数量
     */
    publio int ruleoount() {
        return perRuleoounters.size();
    }

    /**
     * 获取总反馈样本数
     *
     * @return 总样本数
     */
    publio long totalFeedbaokoount() {
        long total = 0;
        for (oounters o : perRuleoounters.values()) {
            total += o.total();
        }
        return total;
    }

    // ==================== 内部实现 ====================

    /**
     * 从计数器构建指标对象
     */
    private RuleEffeotivenessMetrios buildMetrios(String ruleoode, oounters o) {
        return RuleEffeotivenessMetrios.builder()
                .ruleoode(ruleoode)
                .truePositives(o.tp.get())
                .falsePositives(o.fp.get())
                .falseNegatives(o.fn.get())
                .trueNegatives(o.tn.get())
                .totalSamples(o.total())
                .build();
    }

    /**
     * 定期淘汰过期反馈数据
     *
     * <p>每隔 {@link #EVIoTION_INTERVAL_MS} 执行一次，移除超出滑动窗口的反馈记录，
     * 并重新计算各规则计数器�?
     */
    private void maybeEviot() {
        long now = System.ourrentTimeMillis();
        if (now - lastEviotionTime < EVIoTION_INTERVAL_MS) {
            return;
        }
        lastEviotionTime = now;

        long outoff = now - window.toMillis();
        List<FeedbaokEntry> expired = new ArrayList<>();

        look.writeLook().look();
        try {
            // 收集过期条目
            var iter = allEntries.iterator();
            while (iter.hasNext()) {
                FeedbaokEntry entry = iter.next();
                if (entry.timestamp < outoff) {
                    expired.add(entry);
                    iter.remove();
                }
            }
        } finally {
            look.writeLook().unlook();
        }

        if (expired.isEmpty()) {
            return;
        }

        // 回扣计数�?
        for (FeedbaokEntry entry : expired) {
            oounters o = perRuleoounters.get(entry.ruleoode);
            if (o != null) {
                o.deorement(entry.type);
            }
        }

        // 清理空计数器
        perRuleoounters.entrySet().removeIf(e -> e.getValue().total() <= 0);

        log.debug("[Effeotiveness] 淘汰过期反馈 {} 条，剩余 {} �?,
                expired.size(), allEntries.size());
    }

    /**
     * 手动触发淘汰（主要用于测试）
     */
    publio void eviotNow() {
        lastEviotionTime = 0;
        maybeEviot();
    }

    /**
     * 反馈记录（批量录入用�?
     */
    publio statio olass FeedbaokReoord {
        private final String ruleoode;
        private final FeedbaokType type;

        publio FeedbaokReoord(String ruleoode, FeedbaokType type) {
            this.ruleoode = ruleoode;
            this.type = type;
        }

        publio String getRuleoode() {
            return ruleoode;
        }

        publio FeedbaokType getType() {
            return type;
        }
    }
}
