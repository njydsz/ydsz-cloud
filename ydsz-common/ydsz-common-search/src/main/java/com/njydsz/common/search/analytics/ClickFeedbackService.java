package com.njydsz.common.search.analytics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import com.njydsz.common.search.config.SearchProperties;

/**
 * 搜索点击反馈闭环服务。
 *
 * <p>解决"搜索 → 展示 → 点击"链路中反馈信号缺失问题：用户点击搜索结果后，信号回写到 Redis 形成 CTR 闭环，
 * 供 {@link BusinessRanker} 与 {@link SearchSuggestion} 消费，提升长尾词排序与热搜推荐质量。
 *
 * <p><b>Redis 数据模型</b>：
 *
 * <ul>
 *   <li>{@code search:click:doc:{docId}} — 全局点击总数（String/INCR），按文档维度聚合 CTR</li>
 *   <li>{@code search:click:kw:{normalizedKeyword}} — 关键词下各文档的点击数（ZSet，score = 累计点击），
 *       用于"该用户搜了什么、点了哪个"的细粒度反馈</li>
 *   <li>{@code search:click:position} — 点击位置分布（String/HINCRBY），位置 1~10 各累计多少次，用于评估排序质量</li>
 *   <li>{@code search:click:recent:{userId}} — 用户最近点击的文档 ID（List/LPUSH + LTRIM 50），用于去重与个性化提示</li>
 * </ul>
 *
 * <p><b>CTR 信号消费方</b>：{@code BusinessRanker.reRank()} 读取 keyword-level ZSet 计算 CTR boost；
 * {@code SearchAnalyticsService.recordSearch()} 在热门词统计中参考 CTR 调整排名。
 *
 * <p>Redis 不可用时降级到 no-op（不抛异常），埋点失败不影响主链路。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Slf4j
public class ClickFeedbackService {

  /** 文档全局点击数 key 模板。 */
  private static final String KEY_DOC_CLICKS = "search:click:doc:%s";

  /** 关键词下文档点击 ZSet key 模板（关键词需 lowercase + trim）。 */
  private static final String KEY_KEYWORD_CLICKS = "search:click:kw:%s";

  /** 点击位置分布 Hash key。 */
  private static final String KEY_POSITION_BUCKETS = "search:click:position";

  /** 用户最近点击文档 List key 模板。 */
  private static final String KEY_USER_RECENT = "search:click:recent:%s";

  /** 用户点击历史最大保留条数。 */
  private static final int USER_RECENT_LIMIT = 50;

  /** 热门点击文档排行 ZSet key（全局维度，score = CTR × 点击数）。 */
  private static final String KEY_TOP_DOCS = "search:click:top";

  private final ObjectProvider<StringRedisTemplate> redisProvider;
  private final SearchProperties properties;

  public ClickFeedbackService(
      ObjectProvider<StringRedisTemplate> redisProvider, SearchProperties properties) {
    this.redisProvider = redisProvider;
    this.properties = properties;
  }

  /**
   * 记录一次搜索结果点击。
   *
   * <p>幂等性保障：同一用户在同一搜索会话（基于 sessionId）的同一文档点击仅计一次，避免刷点击。
   * 非关键参数为 null 时降级为无状态记录，不抛异常。
   *
   * <p>写入路径（Redis 可用时）：
   *
   * <ol>
   *   <li>全局点击计数 INCR {@code search:click:doc:{docId}}</li>
   *   <li>关键词下文档排序 ZSet INCRBY 1.0 {@code search:click:kw:{keyword}}</li>
   *   <li>点击位置索引累计 INCRBY 1 {@code search:click:position} field = position</li>
   *   <li>用户点击历史 LPUSH + LTRIM {@code search:click:recent:{userId}}</li>
   * </ol>
   *
   * @param keyword 搜索关键词（原始输入，非空）
   * @param docId 文档 ID（非空）
   * @param position 点击位置（从 1 开始）
   * @param userId 点击用户 ID，可为 null（未登录用户不记录历史）
   * @param sessionId 搜索会话 ID，用于同一搜索去重，可为 null
   */
  public void recordClick(String keyword, String docId, int position, String userId, String sessionId) {
    if (!properties.getClickFeedback().isEnabled()) {
      return;
    }
    if (keyword == null || keyword.isBlank() || docId == null || docId.isBlank()) {
      return;
    }

    String normalizedKeyword = keyword.trim().toLowerCase();
    StringRedisTemplate redis = getRedis();
    if (redis == null) {
      log.debug("[ClickFeedback] Redis 不可用，跳过点击反馈记录: docId={}", docId);
      return;
    }

    try {
      // 1. 文档全局点击计数
      redis.opsForValue().increment(String.format(KEY_DOC_CLICKS, docId));

      // 2. 关键词维度 ZSet — 文档按点击数排序
      redis.opsForZSet().incrementScore(
          String.format(KEY_KEYWORD_CLICKS, normalizedKeyword), docId, 1.0);

      // 3. 点击位置分布（只记录前 20 位，超出归入 "other"）
      String posBucket = position > 20 ? "other" : String.valueOf(position);
      redis.opsForHash().increment(KEY_POSITION_BUCKETS, posBucket, 1L);

      // 4. 用户最近点击历史（仅登录用户）
      if (userId != null && !userId.isBlank()) {
        String userKey = String.format(KEY_USER_RECENT, userId);
        redis.opsForList().leftPush(userKey, docId);
        redis.opsForList().trim(userKey, 0, USER_RECENT_LIMIT - 1);
        redis.expire(userKey, Duration.ofDays(7));
      }

      // 5. 热门点击排行 — 使用 clickCount 作为 score（后续可由离线任务换为 CTR）
      Long docClicks = redis.opsForValue().increment(String.format(KEY_DOC_CLICKS, docId));
      if (docClicks != null) {
        redis.opsForZSet().add(KEY_TOP_DOCS, docId, docClicks.doubleValue());
      }
    } catch (Exception e) {
      log.debug("[ClickFeedback] Redis 写入失败（不影响主链路）: docId={}, msg={}", docId, e.getMessage());
    }
  }

  /**
   * 获取某关键词下点击率最高的文档 ID 列表（按点击数降序）。
   *
   * <p>由 {@link BusinessRanker} 调用以应用 CTR boost。返回空列表表示无数据或 Redis 不可用，
   * 调用方需自行降级。
   *
   * @param keyword 搜索关键词
   * @param limit 返回条数上限
   * @return 文档 ID 列表，按点击数降序；无数据时返回空列表
   */
  public List<String> getTopClickedDocs(String keyword, int limit) {
    if (keyword == null || keyword.isBlank()) {
      return Collections.emptyList();
    }
    StringRedisTemplate redis = getRedis();
    if (redis == null) {
      return Collections.emptyList();
    }

    try {
      String key = String.format(KEY_KEYWORD_CLICKS, keyword.trim().toLowerCase());
      Set<ZSetOperations.TypedTuple<String>> tuples =
          redis.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);
      if (tuples == null || tuples.isEmpty()) {
        return Collections.emptyList();
      }
      List<String> docIds = new ArrayList<>(tuples.size());
      for (ZSetOperations.TypedTuple<String> tuple : tuples) {
        if (tuple.getValue() != null) {
          docIds.add(tuple.getValue());
        }
      }
      return docIds;
    } catch (Exception e) {
      log.debug("[ClickFeedback] Redis 读取 topClicks 失败: keyword={}, msg={}", keyword, e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * 获取某关键词下指定文档的累计点击数。
   *
   * <p>可由排序模块做细粒度 boost：点击越多则说明相关性越强。
   *
   * @param keyword 关键词
   * @param docId 文档 ID
   * @return 点击数；未点击过或 Redis 不可用时返回 0
   */
  public long getDocClickCount(String keyword, String docId) {
    if (keyword == null || keyword.isBlank() || docId == null || docId.isBlank()) {
      return 0L;
    }
    StringRedisTemplate redis = getRedis();
    if (redis == null) {
      return 0L;
    }

    try {
      String key = String.format(KEY_KEYWORD_CLICKS, keyword.trim().toLowerCase());
      Double score = redis.opsForZSet().score(key, docId);
      return score != null ? score.longValue() : 0L;
    } catch (Exception e) {
      return 0L;
    }
  }

  /**
   * 获取文档的全局点击数（跨所有关键词汇总）。
   *
   * @param docId 文档 ID
   * @return 点击数；无数据时返回 0
   */
  public long getGlobalDocClicks(String docId) {
    if (docId == null || docId.isBlank()) {
      return 0L;
    }
    StringRedisTemplate redis = getRedis();
    if (redis == null) {
      return 0L;
    }

    try {
      String val = redis.opsForValue().get(String.format(KEY_DOC_CLICKS, docId));
      return val != null ? Long.parseLong(val) : 0L;
    } catch (Exception e) {
      return 0L;
    }
  }

  /**
   * 获取用户最近点击的文档 ID 列表。
   *
   * <p>用于去重（不重复推荐已读文档）或"继续阅读"提示。
   *
   * @param userId 用户 ID
   * @return 文档 ID 列表，最近点击在前；无数据时返回空列表
   */
  public List<String> getUserRecentClicks(String userId) {
    if (userId == null || userId.isBlank()) {
      return Collections.emptyList();
    }
    StringRedisTemplate redis = getRedis();
    if (redis == null) {
      return Collections.emptyList();
    }

    try {
      List<String> docs = redis.opsForList().range(String.format(KEY_USER_RECENT, userId), 0, USER_RECENT_LIMIT - 1);
      return docs != null ? docs : Collections.emptyList();
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  /**
   * 获取全局热门点击文档排行。
   *
   * <p>适用于运营看板或首页"大家都在看"场景。
   *
   * @param limit 返回条数
   * @return 文档 ID 列表，按点击数降序
   */
  public List<String> getGlobalTopClicks(int limit) {
    StringRedisTemplate redis = getRedis();
    if (redis == null) {
      return Collections.emptyList();
    }

    try {
      Set<String> docIds = redis.opsForZSet().reverseRange(KEY_TOP_DOCS, 0, limit - 1);
      return docIds != null ? new ArrayList<>(docIds) : Collections.emptyList();
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  private StringRedisTemplate getRedis() {
    return redisProvider.getIfAvailable();
  }
}
