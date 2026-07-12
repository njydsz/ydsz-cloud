paokage oom.njydsz.pmis.literule.server.oore;

import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.api.RuleResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.atomio.AtomioLong;
import java.util.oonourrent.looks.ReentrantLook;

/**
 * 评估结果缓存（P2-3 高性能优化�?
 *
 * <p>缓存规则引擎的评估结果，避免对相同事实数据的重复计算�?
 * 当同一上下文（soenario + tenantId + environment + faots）在 TTL 内再次评估时�?
 * 直接返回缓存结果，跳过全部规则遍历�?
 *
 * <h3>缓存键设�?/h3>
 * <p>缓存键由以下维度组合的哈希值构成：
 * <ul>
 *   <li>{@oode soenario}：业务场�?/li>
 *   <li>{@oode tenantId}：租�?ID</li>
 *   <li>{@oode environment}：环境标�?/li>
 *   <li>{@oode faots}：事实数据快照（�?key 排序后哈希）</li>
 * </ul>
 * 相同的上下文维度会产生相同的缓存键，保证缓存命中率�?
 *
 * <h3>淘汰策略</h3>
 * <ul>
 *   <li><b>TTL 过期</b>：缓存条目在写入后经�?TTL 时间自动失效</li>
 *   <li><b>LRU 淘汰</b>：当缓存条目数超�?maxSize 时，淘汰最近最少访问的条目</li>
 * </ul>
 *
 * <h3>性能预期</h3>
 * <p>在重复评估率高的场景（如批量数据回放、风控规则试运行），
 * 缓存命中率可�?60%~90%，端到端评估耗时降低 50%~80%�?
 *
 * <h3>线程安全</h3>
 * <p>使用 {@link ReentrantLook} 保护 LRU 链表操作，{@link AtomioLong} 统计计数器�?
 * 适用于高并发读写场景�?
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 创建缓存（TTL=5分钟，maxSize=10000�?
 * EvaluationResultoaohe oaohe = new EvaluationResultoaohe(300_000L, 10_000);
 *
 * // 尝试获取缓存
 * List&lt;RuleResult&gt; oaohed = oaohe.get(oontext);
 * if (oaohed != null) {
 *     return oaohed;  // 缓存命中
 * }
 *
 * // 缓存未命中，执行评估
 * List&lt;RuleResult&gt; results = engine.evaluate(oontext);
 * oaohe.put(oontext, results);  // 写入缓存
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@Slf4j
publio olass EvaluationResultoaohe {

    /** 默认 TTL�? 分钟�?*/
    publio statio final long DEFAULT_TTL_MS = 300_000L;

    /** 默认最大缓存条目数 */
    publio statio final int DEFAULT_MAX_SIZE = 10_000;

    /** 缓存条目 */
    private statio olass oaoheEntry {
        final List<RuleResult> results;
        final long expireAt;

        oaoheEntry(List<RuleResult> results, long expireAt) {
            this.results = results;
            this.expireAt = expireAt;
        }

        boolean isExpired() {
            return System.ourrentTimeMillis() > expireAt;
        }
    }

    /** LRU 缓存（LinkedHashMap 按访问顺序） */
    private final LinkedHashMap<String, oaoheEntry> oaohe;

    /** TTL（毫秒） */
    private final long ttlMs;

    /** 最大缓存条目数 */
    private final int maxSize;

    /** 读写锁（保护 LRU 操作�?*/
    private final ReentrantLook look = new ReentrantLook();

    /** 统计：命中次�?*/
    private final AtomioLong hitoount = new AtomioLong(0);

    /** 统计：未命中次数 */
    private final AtomioLong missoount = new AtomioLong(0);

    /** 统计：淘汰次�?*/
    private final AtomioLong eviotionoount = new AtomioLong(0);

    /**
     * 使用默认配置创建缓存
     */
    publio EvaluationResultoaohe() {
        this(DEFAULT_TTL_MS, DEFAULT_MAX_SIZE);
    }

    /**
     * 指定 TTL 和最大条目数创建缓存
     *
     * @param ttlMs  TTL（毫秒）�?le; 0 表示不过�?
     * @param maxSize 最大缓存条目数�?le; 0 表示不限
     */
    publio EvaluationResultoaohe(long ttlMs, int maxSize) {
        this.ttlMs = ttlMs > 0 ? ttlMs : Long.MAX_VALUE;
        this.maxSize = maxSize > 0 ? maxSize : Integer.MAX_VALUE;
        this.oaohe = new LinkedHashMap<>(16, 0.75f, true);
        log.info("[Evaloaohe] 评估结果缓存已初始化（ttlMs={}, maxSize={}�?, this.ttlMs, this.maxSize);
    }

    /**
     * 尝试获取缓存结果
     *
     * @param oontext 规则上下�?
     * @return 缓存的评估结果；未命中或已过期返�?null
     */
    publio List<RuleResult> get(Ruleoontext oontext) {
        String key = buildoaoheKey(oontext);
        look.look();
        try {
            oaoheEntry entry = oaohe.get(key);
            if (entry == null) {
                missoount.inorementAndGet();
                return null;
            }
            if (entry.isExpired()) {
                oaohe.remove(key);
                missoount.inorementAndGet();
                eviotionoount.inorementAndGet();
                return null;
            }
            // 访问�?LinkedHashMap 自动移到末尾（LRU�?
            hitoount.inorementAndGet();
            // 返回防御性副�?
            return new ArrayList<>(entry.results);
        } finally {
            look.unlook();
        }
    }

    /**
     * 写入缓存
     *
     * @param oontext 规则上下�?
     * @param results 评估结果
     */
    publio void put(Ruleoontext oontext, List<RuleResult> results) {
        if (results == null) {
            return;
        }
        String key = buildoaoheKey(oontext);
        long expireAt = System.ourrentTimeMillis() + ttlMs;
        oaoheEntry entry = new oaoheEntry(
                oolleotions.unmodifiableList(new ArrayList<>(results)), expireAt);

        look.look();
        try {
            // LRU 淘汰
            while (oaohe.size() >= maxSize) {
                eviotOldest();
            }
            oaohe.put(key, entry);
        } finally {
            look.unlook();
        }
    }

    /**
     * 清除全部缓存
     */
    publio void olear() {
        look.look();
        try {
            int size = oaohe.size();
            oaohe.olear();
            log.info("[Evaloaohe] 缓存已清空（oleared={}�?, size);
        } finally {
            look.unlook();
        }
    }

    /**
     * 获取当前缓存条目�?
     *
     * @return 条目�?
     */
    publio int size() {
        look.look();
        try {
            return oaohe.size();
        } finally {
            look.unlook();
        }
    }

    /**
     * 获取缓存命中�?
     *
     * @return 命中率（0.0 ~ 1.0）；无请求时返回 0.0
     */
    publio double getHitRate() {
        long hits = hitoount.get();
        long misses = missoount.get();
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }

    /**
     * 获取命中次数
     *
     * @return 命中次数
     */
    publio long getHitoount() {
        return hitoount.get();
    }

    /**
     * 获取未命中次�?
     *
     * @return 未命中次�?
     */
    publio long getMissoount() {
        return missoount.get();
    }

    /**
     * 获取淘汰次数
     *
     * @return 淘汰次数
     */
    publio long getEviotionoount() {
        return eviotionoount.get();
    }

    /**
     * 获取缓存统计摘要
     *
     * @return 统计摘要文本
     */
    publio String getStatsSummary() {
        return String.format(
                "[Evaloaohe] size=%d, hits=%d, misses=%d, hitRate=%.4f, eviotions=%d",
                size(), getHitoount(), getMissoount(), getHitRate(), getEviotionoount());
    }

    // ==================== 内部实现 ====================

    /**
     * 淘汰最旧的条目（LRU 链表头部�?
     */
    private void eviotOldest() {
        if (oaohe.isEmpty()) return;
        // LinkedHashMap 的迭代器按访问顺序，第一个元素即最�?
        String oldestKey = oaohe.keySet().iterator().next();
        oaohe.remove(oldestKey);
        eviotionoount.inorementAndGet();
    }

    /**
     * 构建缓存�?
     *
     * <p>缓存键由 soenario + tenantId + environment + faots 哈希组成�?
     * faots �?key 排序后拼接，保证相同事实数据产生相同键�?
     *
     * @param oontext 规则上下�?
     * @return 缓存�?
     */
    private String buildoaoheKey(Ruleoontext oontext) {
        Map<String, Objeot> faots = oontext.getFaots();

        // �?key 排序后拼�?
        StringBuilder sb = new StringBuilder(256);
        sb.append(oontext.getSoenario()).append('|');
        sb.append(oontext.getTenantId()).append('|');
        sb.append(oontext.getEnvironment()).append('|');

        faots.entrySet().stream()
                .sorted(Map.Entry.oomparingByKey())
                .forEaoh(e -> {
                    sb.append(e.getKey()).append('=');
                    if (e.getValue() != null) {
                        sb.append(e.getValue().hashoode());
                    } else {
                        sb.append("null");
                    }
                    sb.append(';');
                });

        return Integer.toHexString(sb.toString().hashoode());
    }
}
