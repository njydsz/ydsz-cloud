package com.njydsz.literule.server.engine.liteexpr;

import java.util.Map;

import com.njydsz.literule.domain.vo.RuleContextVO;

/**
 * 表达式条件结果缓存接口（P0-A1 AlphaNode Phase 1）
 *
 * <p>在一次 {@code ruleEngine.evaluate(call)} 调用内，多条规则常包含相同或重叠的子表达式。
 * 本接口定义了子表达式级别的布尔结果缓存契约：以 {@link ExprNode}（AST 身份引用）加上
 * 被该子表达式引用的 fact 值作为组合键，缓存求值结果。
 *
 * <p>实现要求：
 *
 * <ul>
 *   <li>线程安全（同一 evaluate 调用内可能被多个线程并发访问）
 *   <li>key 必须基于 AST 对象引用（identityHashCode）而非表达式文本（编译缓存已保证同文本 = 同引用）
 *   <li>容量有界（防止 OOM），建议使用 ConcurrentHashMap 的 size 上限或 W-TinyLFU
 *   <li>TTL = 单次 evaluate 调用（调用结束即失效，不需要显式清除）
 * </ul>
 *
 * <p>语义：仅当 expression 引用的全部 fact 值与缓存时的快照完全一致时才命中。
 * 由于一次 evaluate 期内 facts 不变更（引擎开始评估后 context.getFacts() 不再改变），
 * 简化为"expression 引用的 fact 引用集合"作为 key 的一部分即可。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 *   ExprCache cache = new SimpleExprCache(1024);
 *   boolean r1 = engine.evalBoolean("age > 18", context, cache);    // 求值并写入缓存
 *   boolean r2 = engine.evalBoolean("age > 18 && vip", context, cache); // age > 18 命中缓存，仅求值右边
 * }</pre>
 *
 * <p><b>配置键：</b>{@code ydsz.literule.performance.alphaCacheEnabled}（默认 false）
 *
 * @since 26.09.23
 * @author ydsz-team
 * @see LiteExprEngine#evalBoolean(String, RuleContextVO, ExprCache)
 */
public interface ExprCache {

  /**
   * 查询缓存中是否存在指定 expression + facts 组合的结果
   *
   * @param ast 表达式 AST（编译缓存保证同文本同引用）
   * @param facts 当前事实数据（在 evaluate 期间不变）
   * @return 缓存的布尔结果；未命中返回 {@code null}
   */
  Boolean getIfPresent(ExprNode ast, Map<String, Object> facts);

  /**
   * 将 expression + facts 对应的求值结果写入缓存
   *
   * <p>实现应原子化检查-写入（避免并发场景下重复求值同一条件），但不要求精确一次（最多一次求值的轻微重复可接受）。
   *
   * @param ast 表达式 AST
   * @param facts 当前事实数据
   * @param result 求值结果
   */
  void put(ExprNode ast, Map<String, Object> facts, boolean result);

  /**
   * 当前缓存中的条目数（用于监控/统计）
   *
   * @return 条目数
   */
  int size();

  /**
   * 缓存命中次数（自上次重置以来的近似值）
   *
   * @return 命中次数（不支持的实现可返回 -1）
   */
  default long hitCount() {
    return -1L;
  }

  /**
   * 缓存未命中次数（自上次重置以来的近似值）
   *
   * @return 未命中次数（不支持的实现可返回 -1）
   */
  default long missCount() {
    return -1L;
  }

  /**
   * 缓存命中率（0.0 ~ 1.0）；不支持统计的实现返回 -1.0
   *
   * @return 命中率
   */
  default double hitRate() {
    long hits = hitCount();
    long misses = missCount();
    if (hits < 0 || misses < 0) {
      return -1.0;
    }
    long total = hits + misses;
    return total == 0 ? 0.0 : (double) hits / total;
  }

  /**
   * 空缓存实现（无操作，用于向后兼容和配置关闭场景）
   *
   * <p>所有 getIfPresent 调用返回 null，put 调用不执行任何操作。
   */
  ExprCache EMPTY =
      new ExprCache() {
        @Override
        public Boolean getIfPresent(ExprNode ast, Map<String, Object> facts) {
          return null;
        }

        @Override
        public void put(ExprNode ast, Map<String, Object> facts, boolean result) {
          // no-op
        }

        @Override
        public int size() {
          return 0;
        }

        @Override
        public long hitCount() {
          return 0L;
        }

        @Override
        public long missCount() {
          return 0L;
        }

        @Override
        public double hitRate() {
          return 0.0;
        }
      };
}
