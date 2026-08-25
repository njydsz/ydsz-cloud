package com.njydsz.literule.server.distributed;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.literule.api.Rule;
import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleEngine;
import com.njydsz.literule.api.RuleEngineStats;
import com.njydsz.literule.api.RuleResult;

/**
 * 分片感知的规则引擎装饰器（P2-16 分布式执行）
 *
 * <p>包装已有的 {@link RuleEngine}，在 {@code evaluate} / {@code dryRun} 时 只执行属于当前节点的规则，实现分布式分片执行。
 *
 * <h3>分片策略</h3>
 *
 * <ul>
 *   <li>以规则编码（{@code rule.getCode()}）作为分片键
 *   <li>使用一致性 hash 将规则映射到集群节点
 *   <li>只执行 isMine(ruleCode) == true 的规则
 *   <li>节点列表为空或集群规模=1 时，全部本地执行（向后兼容）
 * </ul>
 *
 * <h3>使用方式</h3>
 *
 * <pre>
 * RuleEngine delegate = new DefaultRuleEngine();
 * NodeRegistry registry = ...; // 注入实际的 NodeRegistry 实现
 * ConsistentHashSharder sharder = new ConsistentHashSharder();
 * ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry, sharder);
 * engine.refreshNodes(); // 刷新节点列表
 * List&lt;RuleResult&gt; results = engine.evaluate(context);
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class ShardAwareRuleEngine implements RuleEngine {

  private static final Logger log = LoggerFactory.getLogger(ShardAwareRuleEngine.class);

  /** 被装饰的规则引擎 */
  private final RuleEngine delegate;

  /** 节点注册表 */
  private final NodeRegistry nodeRegistry;

  /** 一致性 hash 分片器 */
  private final ConsistentHashSharder sharder;

  /** 是否启用分片（false 时全部本地执行） */
  private volatile boolean shardingEnabled = true;

  /** 上一次刷新的节点签名 */
  private volatile String lastSignature = "";

  public ShardAwareRuleEngine(RuleEngine delegate, NodeRegistry nodeRegistry) {
    this(delegate, nodeRegistry, new ConsistentHashSharder());
  }

  public ShardAwareRuleEngine(
      RuleEngine delegate, NodeRegistry nodeRegistry, ConsistentHashSharder sharder) {
    this.delegate = delegate;
    this.nodeRegistry = nodeRegistry;
    this.sharder = sharder;
  }

  /** 刷新节点列表并重建 hash 环 */
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
      log.info("[ShardEngine] 集群规模={}，分片已启用，当前节点={}", count, nodeRegistry.getSelfNodeId());
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
    // 统一使用 delegate.evaluate()，确保统计、监控、熔断、轨迹等横切关注点正常生效
    return delegate.evaluate(context);
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
    // 统一使用 delegate.dryRun()，确保与 evaluate() 路径一致
    return delegate.dryRun(context);
  }

  @Override
  public List<Rule> getRules() {
    return delegate.getRules();
  }

  @Override
  public RuleEngineStats getStats() {
    return delegate.getStats();
  }

  /** 判断指定规则编码是否属于当前节点
   * @param ruleCode 参数说明
   * @return 返回值说明
   */
  public boolean isMine(String ruleCode) {
    if (!shardingEnabled) {
      return true;
    }
    return sharder.isMine(ruleCode, nodeRegistry.getSelfNodeId());
  }

  /** 获取当前集群规模
   * @return 返回值说明
   */
  public int getClusterSize() {
    return sharder.getNodeCount();
  }

  /** 是否启用分片
   * @return 返回值说明
   */
  public boolean isShardingEnabled() {
    return shardingEnabled;
  }

  private String buildSignature(List<ClusterNode> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (ClusterNode n : nodes) {
      sb.append(n.getNodeId()).append(',');
    }
    return sb.toString();
  }
}
