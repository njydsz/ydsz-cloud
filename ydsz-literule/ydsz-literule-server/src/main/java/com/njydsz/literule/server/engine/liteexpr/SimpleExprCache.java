package com.njydsz.literule.server.engine.liteexpr;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 表达式条件结果的内存缓存实现（P0-A1 AlphaNode Phase 1）
 *
 * <p>基于 ConcurrentHashMap + AST 对象引用 identityHashCode + 事实 hash 作为组合 key。
 *
 * <p>容量有界：超过上限时停止写入（不淘汰，避免并发的问题）。适用于表达式总数有限的中等规则集（< 2000 条），
 * 每条规则的 AST 编译结果 + facts hash 对应一个缓存槽位。
 *
 * <p>线程安全：所有操作基于 ConcurrentHashMap + LongAdder，无显式同步。
 *
 * <p>使用方式：由 {@code DefaultRuleEngine.doEvaluate} 在评估开始时创建实例，评估结束后自然回收（单次调用生命周期）。
 *
 * <p>key 设计考量：
 *
 * <ul>
 *   <li>AST 对象引用：编译缓存保证同一表达式文本在同一 JVM 内对应唯一 {@link ExprNode} 对象，因此可用 {@code System.identityHashCode} 作为 key 的一部分
 *   <li>facts hash：在一次 evaluate 调用内 facts 不变，直接用 {@code facts.hashCode()} 区分不同事实组合
 *   <li>组合方式：{@code (identityHashCode(ast) * 31 + facts.hashCode())}（类似标准 hash 合并）
 * </ul>
 *
 * @since 26.09.23
 * @author ydsz-team
 */
public class SimpleExprCache implements ExprCache {

  /** 默认最大缓存条目数 */
  private static final int DEFAULT_MAX_SIZE = 2048;

  private final ConcurrentHashMap<Integer, Boolean> store;
  private final int maxSize;
  private final LongAdder hitCount = new LongAdder();
  private final LongAdder missCount = new LongAdder();

  /** 创建默认容量（2048 条）的缓存 */
  public SimpleExprCache() {
    this(DEFAULT_MAX_SIZE);
  }

  /**
   * 创建指定容量的缓存
   *
   * @param maxSize 最大条目数（超过后 put 无效）；{@code <= 0} 使用默认值
   */
  public SimpleExprCache(int maxSize) {
    this.maxSize = maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE;
    this.store = new ConcurrentHashMap<>(Math.min(this.maxSize, 256));
  }

  @Override
  public Boolean getIfPresent(ExprNode ast, Map<String, Object> facts) {
    if (ast == null || facts == null) {
      return null;
    }
    int key = compositeKey(ast, facts);
    Boolean result = store.get(key);
    if (result != null) {
      hitCount.increment();
    } else {
      missCount.increment();
    }
    return result;
  }

  @Override
  public void put(ExprNode ast, Map<String, Object> facts, boolean result) {
    if (ast == null || facts == null) {
      return;
    }
    if (store.size() >= maxSize) {
      // 容量已满：不再写入（避免无限增长）
      return;
    }
    int key = compositeKey(ast, facts);
    store.putIfAbsent(key, result);
  }

  @Override
  public int size() {
    return store.size();
  }

  @Override
  public long hitCount() {
    return hitCount.sum();
  }

  @Override
  public long missCount() {
    return missCount.sum();
  }

  /**
   * 计算组合键：identityHashCode(ast) * 31 + facts.hashCode()
   *
   * <p>与 {@code ExprCache} 契约一致：一次 evaluate 调用内 facts 不变（引擎评估过程不修改 facts），
   * 因此 facts.hashCode() 在同一调用内保持稳定。使用 AST identityHashCode 而非表达式文本 hash
   * 是因为同一表达式文本在不同时刻可能生成不同编译结果（如函数注册表变化），直接比较引用更精确。
   *
   * @param ast AST 对象引用
   * @param facts facts 快照
   * @return 组合整数 key
   */
  static int compositeKey(ExprNode ast, Map<String, Object> facts) {
    int astHash = System.identityHashCode(ast);
    int factsHash = facts.hashCode();
    return astHash * 31 + factsHash;
  }

  /** 清空缓存（测试用） */
  void clear() {
    store.clear();
    hitCount.reset();
    missCount.reset();
  }
}
