package com.njydsz.literule.server.distributed;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.vo.RuleContext;
import com.njydsz.literule.domain.RuleEngine;
import com.njydsz.literule.domain.vo.RuleEngineStats;
import com.njydsz.literule.domain.vo.RuleResult;

/**
 * 分片感知的规则引擎装饰器（P2-16 分布式执行）
 *
 * <p>包装已有的 {@link RuleEngine}，通过<b>注册期过滤 + 拓扑变化时重平衡</b>实现分布式分片执行：
 * 每个节点只把属于自身的规则注册到被装饰引擎，{@code evaluate} / {@code dryRun} 天然只评估本地规则，
 * 不改变 delegate 的评估语义，也无需在评估路径上逐条过滤。
 *
 * <h3>分片策略</h3>
 *
 * <ul>
 *   <li>以规则编码（{@code rule.getCode()}）作为分片键
 *   <li>使用一致性 hash 将规则映射到集群节点（{@link ConsistentHashSharder}）
 *   <li>仅 isMine(ruleCode) == true 的规则注册到 delegate，其余规则不注册（不评估）
 *   <li>节点列表为空、集群规模=1 或尚未 {@link #refreshNodes()} 时，全部本地执行（向后兼容）
 *   <li>集群拓扑变化（节点上下线）时由 {@link #refreshNodes()} 触发重平衡：
 *       逐条规则按新归属同步 注册/注销，在途请求不受影响（delegate 内部 CopyOnWrite）
 * </ul>
 *
 * <h3>使用方式</h3>
 *
 * <pre>
 * RuleEngine delegate = new DefaultRuleEngine();
 * NodeRegistry registry = ...; // 注入实际的 NodeRegistry 实现
 * ConsistentHashSharder sharder = new ConsistentHashSharder();
 * ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry, sharder);
 * engine.refreshNodes(); // 刷新节点列表，完成首轮分片
 * engine.register(rule); // 注册时即按分片归属过滤
 * List&lt;RuleResult&gt; results = engine.evaluate(context);
 * </pre>
 *
 * <h3>废弃说明</h3>
 *
 * <p>自 1.0.0 起提供但<b>从未在业务代码中实际调用</b>（{@link #refreshNodes()} 未被触发，
 * {@code shardingEnabled} 始终为 false）。规则引擎通常需要全量加载规则以保障优先级编排语义，
 * 分片执行会破坏此语义，与业务场景不匹配。
 *
 * <p>计划在未来版本中移除。如需分布式规则执行，建议通过上游路由（如网关层按租户分流）实现，
 * 而非在引擎层做规则分片。
 *
 * @since 1.0.0
 * @author ydsz-team
 * @deprecated 自 1.4.0 起废弃，计划未来版本移除。原因：业务无多节点规则分摊诉求，且分片执行破坏规则优先级编排语义
 */
@Deprecated
public class ShardAwareRuleEngine implements RuleEngine {

  private static final Logger log = LoggerFactory.getLogger(ShardAwareRuleEngine.class);

  /** 被装饰的规则引擎 */
  private final RuleEngine delegate;

  /** 节点注册表 */
  private final NodeRegistry nodeRegistry;

  /** 一致性 hash 分片器 */
  private final ConsistentHashSharder sharder;

  /** 是否启用分片（false 时全部本地执行） */
  private volatile boolean shardingEnabled = false;

  /** 上一次刷新的节点签名 */
  private volatile String lastSignature = "";

  /** 本节点已注册的全部规则（全量视图，用于拓扑变化时重平衡） */
  private final Map<String, Rule> allRules = new ConcurrentHashMap<>();

  public ShardAwareRuleEngine(RuleEngine delegate, NodeRegistry nodeRegistry) {
    this(delegate, nodeRegistry, new ConsistentHashSharder());
  }

  public ShardAwareRuleEngine(
      RuleEngine delegate, NodeRegistry nodeRegistry, ConsistentHashSharder sharder) {
    this.delegate = delegate;
    this.nodeRegistry = nodeRegistry;
    this.sharder = sharder;
  }

  /**
   * 刷新节点列表并重建 hash 环（P0-1：接入真实分片执行路径）
   *
   * <p>节点拓扑变化时对已注册规则做重平衡：按新归属逐条同步 注册/注销。
   * 首轮调用前 {@code shardingEnabled=false}（全部本地执行），避免节点数据未就绪时误丢规则。
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
      log.info("[ShardEngine] 集群规模={}，分片已启用，当前节点={}", count, nodeRegistry.getSelfNodeId());
    }
    rebalance();
  }

  /** 按当前分片归属重平衡 delegate 中的规则（本节点只保留 isMine 的规则） */
  private void rebalance() {
    for (Rule rule : allRules.values()) {
      syncLocal(rule);
    }
    log.info("[ShardEngine] 重平衡完成，本地生效规则数={}", delegate.getRules().size());
  }

  /** 将单条规则按当前归属同步到 delegate（本地生效则注册，否则注销） */
  private void syncLocal(Rule rule) {
    boolean mine = !shardingEnabled || isMine(rule.getCode());
    if (mine) {
      delegate.register(rule);
    } else {
      delegate.unregister(rule.getCode());
    }
  }

  @Override
  public void register(Rule rule) {
    if (rule == null || rule.getCode() == null) {
      throw new IllegalArgumentException("规则与规则编码不能为空");
    }
    allRules.put(rule.getCode(), rule);
    syncLocal(rule);
  }

  @Override
  public void unregister(String ruleCode) {
    allRules.remove(ruleCode);
    delegate.unregister(ruleCode);
  }

  @Override
  public List<RuleResult> evaluate(RuleContext context) {
    // delegate 仅持有本节点规则，评估即分片执行
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
    // 与 evaluate() 路径一致：仅仿真本节点持有的规则
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
