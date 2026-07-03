package com.njydsz.pmis.literule.distributed;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分片感知的规则引擎装饰器（P2-16 分布式执行）
 *
 * <p>包装已有的 {@link RuleEngine}，在 {@code evaluate} / {@code dryRun} 时
 * 只执行属于当前节点的规则，实现分布式分片执行。
 *
 * <h3>分片策略</h3>
 * <ul>
 *   <li>以规则编码（{@code rule.getCode()}）作为分片键</li>
 *   <li>使用一致性 hash 将规则映射到集群节点</li>
 *   <li>只执行 isMine(ruleCode) == true 的规则</li>
 *   <li>节点列表为空或集群规模=1 时，全部本地执行（向后兼容）</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>
 * RuleEngine delegate = new DefaultRuleEngine();
 * NodeRegistry registry = new InMemoryNodeRegistry("node-1");
 * ConsistentHashSharder sharder = new ConsistentHashSharder();
 * ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry, sharder);
 * engine.start(); // 启动定时刷新节点列表
 * List&lt;RuleResult&gt; results = engine.evaluate(context);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public class ShardAwareRuleEngine implements RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(ShardAwareRuleEngine.class);

    /** 被装饰的规则引擎 */
    private final RuleEngine delegate;

    /** 节点注册表 */
    private final NodeRegistry nodeRegistry;

    /** 一致性 hash 分片器 */
    private final ConsistentHashSharder sharder;

    /** 节点列表刷新间隔（毫秒） */
    private final long refreshIntervalMs;

    /** 是否启用分片（false 时全部本地执行） */
    private volatile boolean shardingEnabled = true;

    /** 上一次刷新的节点签名 */
    private volatile String lastSignature = "";

    public ShardAwareRuleEngine(RuleEngine delegate, NodeRegistry nodeRegistry) {
        this(delegate, nodeRegistry, new ConsistentHashSharder(), 10_000L);
    }

    public ShardAwareRuleEngine(RuleEngine delegate, NodeRegistry nodeRegistry,
                                ConsistentHashSharder sharder, long refreshIntervalMs) {
        this.delegate = delegate;
        this.nodeRegistry = nodeRegistry;
        this.sharder = sharder;
        this.refreshIntervalMs = refreshIntervalMs;
    }

    /**
     * 刷新节点列表并重建 hash 环
     */
    public synchronized void refreshNodes() {
        List<ClusterNode> alive = nodeRegistry.getAliveNodes();
        String sig = buildSignature(alive);
        if (sig.equals(lastSignature)) {
            return;
        }
        lastSignature = sig;
        sharder.updateNodes(alive);
        int count = sharder.getNodeCount();
        if (count <= 1) {
            // 单节点或无节点：全部本地执行
            shardingEnabled = false;
            log.info("[ShardEngine] 集群规模 ≤1，分片关闭，全部本地执行 (nodes={})", count);
        } else {
            shardingEnabled = true;
            log.info("[ShardEngine] 集群规模={}，分片已启用，当前节点={}",
                    count, nodeRegistry.getSelfNodeId());
        }
    }

    @Override
    public void register(Rule rule) {
        delegate.register(rule);
    }

    @Override
    public void unregister(String ruleCode) {
        delegate.unregister(ruleCode);
    }

    @Override
    public List<RuleResult> evaluate(RuleContext context) {
        if (!shardingEnabled) {
            return delegate.evaluate(context);
        }
        List<Rule> mine = filterMineRules();
        return evaluateSubset(mine, context, false);
    }

    @Override
    public RuleResult topResult(RuleContext context) {
        List<RuleResult> results = evaluate(context);
        if (results == null || results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    @Override
    public List<RuleResult> dryRun(RuleContext context) {
        if (!shardingEnabled) {
            return delegate.dryRun(context);
        }
        List<Rule> mine = filterMineRules();
        return evaluateSubset(mine, context, true);
    }

    @Override
    public List<Rule> getRules() {
        return delegate.getRules();
    }

    @Override
    public RuleEngineStats getStats() {
        return delegate.getStats();
    }

    /**
     * 过滤出属于当前节点的规则
     */
    private List<Rule> filterMineRules() {
        String selfId = nodeRegistry.getSelfNodeId();
        return delegate.getRules().stream()
                .filter(r -> {
                    if (r == null || r.getCode() == null) return true;
                    return sharder.isMine(r.getCode(), selfId);
                })
                .collect(Collectors.toList());
    }

    /**
     * 对子集规则执行评估
     *
     * <p>由于 {@link RuleEngine#evaluate} 是对全部已注册规则执行的，
     * 这里通过临时注册/注销实现子集执行不现实，因此直接调用规则的 evaluate 方法。
     */
    private List<RuleResult> evaluateSubset(List<Rule> rules, RuleContext context, boolean dryRun) {
        if (rules == null || rules.isEmpty()) {
            return new ArrayList<>();
        }
        List<RuleResult> results = new ArrayList<>(rules.size());
        for (Rule rule : rules) {
            try {
                RuleResult result = dryRun ? rule.evaluate(context) : rule.evaluate(context);
                if (result != null && (result.isTriggered() || dryRun)) {
                    results.add(result);
                }
            } catch (Exception e) {
                log.warn("[ShardEngine] 规则 {} 执行异常: {}", rule.getCode(), e.getMessage());
            }
        }
        results.sort((a, b) -> {
            int sa = a.getSeverity() == null ? 0 : a.getSeverity().getWeight();
            int sb = b.getSeverity() == null ? 0 : b.getSeverity().getWeight();
            return Integer.compare(sb, sa);
        });
        return results;
    }

    /**
     * 判断指定规则编码是否属于当前节点
     */
    public boolean isMine(String ruleCode) {
        if (!shardingEnabled) {
            return true;
        }
        return sharder.isMine(ruleCode, nodeRegistry.getSelfNodeId());
    }

    /**
     * 获取当前集群规模
     */
    public int getClusterSize() {
        return sharder.getNodeCount();
    }

    /**
     * 是否启用分片
     */
    public boolean isShardingEnabled() {
        return shardingEnabled;
    }

    private String buildSignature(List<ClusterNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ClusterNode n : nodes) {
            sb.append(n.getNodeId()).append(',');
        }
        return sb.toString();
    }
}
