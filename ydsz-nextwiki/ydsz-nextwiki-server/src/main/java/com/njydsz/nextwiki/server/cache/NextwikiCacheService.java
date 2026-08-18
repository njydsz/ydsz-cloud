package com.njydsz.nextwiki.server.cache;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.StorageQuotaVO;

/**
 * NextWiki 缓存服务
 *
 * <p>封装文件详情、目录列表、配额用量的 Redis 缓存读写与失效逻辑。
 *
 * <p><b>缓存策略：</b>
 *
 * <ul>
 *   <li>文件详情：key={@code nw:file:{nodeId}}，TTL 10 分钟
 *   <li>目录列表：key={@code nw:children:{parentId}}，TTL 5 分钟
 *   <li>配额用量：key={@code nw:quota:{scopeType}:{scopeId}}，TTL 3 分钟
 * </ul>
 *
 * <p><b>缓存失效：</b>
 *
 * <ul>
 *   <li>文件创建/更新/删除 → 失效文件详情 + 父目录列表
 *   <li>配额变更（上传/删除/恢复） → 失效对应用户配额
 * </ul>
 *
 * <p><b>防穿透/雪崩：</b>
 *
 * <ul>
 *   <li>空值不缓存（文件/配额不存在时直接返回，避免缓存污染）
 *   <li>TTL 固定 + 随机偏移（RedisStringOps 内置）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NextwikiCacheService {

  /** 文件详情缓存前缀 */
  private static final String KEY_FILE = "nw:file:";

  /** 目录子节点列表缓存前缀 */
  private static final String KEY_CHILDREN = "nw:children:";

  /** 配额用量缓存前缀 */
  private static final String KEY_QUOTA = "nw:quota:";

  /** 文件详情缓存 TTL（秒） */
  private static final long TTL_FILE = 600;

  /** 目录列表缓存 TTL（秒） */
  private static final long TTL_CHILDREN = 300;

  /** 配额用量缓存 TTL（秒） */
  private static final long TTL_QUOTA = 180;

  private final RedisStringOps redisStringOps;

  // ==================== 文件详情缓存 ====================

  /**
   * 获取文件详情（优先缓存）。
   *
   * <p>缓存未命中时通过 {@code loader} 从数据库加载并回填缓存。
   *
   * @param nodeId 文件节点 ID
   * @param loader 数据库加载函数
   * @return 文件节点 VO；不存在返回 {@code Optional.empty()}
   */
  public Optional<FileNodeVO> getFile(String nodeId, java.util.function.Supplier<Optional<FileNodeVO>> loader) {
    String key = KEY_FILE + nodeId;
    try {
      FileNodeVO cached = redisStringOps.get(key, FileNodeVO.class);
      if (cached != null) {
        log.debug("[NextwikiCacheService] 文件详情缓存命中: nodeId={}", nodeId);
        return Optional.of(cached);
      }
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] 文件详情缓存读取异常: nodeId={}, err={}", nodeId, e.getMessage());
    }

    Optional<FileNodeVO> result = loader.get();
    result.ifPresent(vo -> {
      try {
        redisStringOps.set(key, vo, TTL_FILE);
        log.debug("[NextwikiCacheService] 文件详情缓存回填: nodeId={}", nodeId);
      } catch (Exception e) {
        log.warn("[NextwikiCacheService] 文件详情缓存写入异常: nodeId={}, err={}", nodeId, e.getMessage());
      }
    });
    return result;
  }

  /**
   * 失效文件详情缓存。
   *
   * @param nodeId 文件节点 ID
   */
  public void evictFile(String nodeId) {
    try {
      redisStringOps.delete(KEY_FILE + nodeId);
      log.debug("[NextwikiCacheService] 文件详情缓存失效: nodeId={}", nodeId);
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] 文件详情缓存失效异常: nodeId={}, err={}", nodeId, e.getMessage());
    }
  }

  // ==================== 目录列表缓存 ====================

  /**
   * 获取目录子节点列表（优先缓存）。
   *
   * <p>缓存未命中时通过 {@code loader} 从数据库加载并回填缓存。
   *
   * @param parentId 父节点 ID
   * @param loader 数据库加载函数
   * @return 子节点 VO 列表
   */
  public List<FileNodeVO> getChildren(String parentId, java.util.function.Supplier<List<FileNodeVO>> loader) {
    String key = KEY_CHILDREN + parentId;
    try {
      String json = redisStringOps.get(key, String.class);
      if (json != null && !json.isEmpty()) {
        List<FileNodeVO> cached = YdszJson.fromJson(json, List.class, FileNodeVO.class);
        if (cached != null && !cached.isEmpty()) {
          log.debug("[NextwikiCacheService] 目录列表缓存命中: parentId={}, size={}", parentId, cached.size());
          return cached;
        }
      }
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] 目录列表缓存读取异常: parentId={}, err={}", parentId, e.getMessage());
    }

    List<FileNodeVO> result = loader.get();
    if (result != null && !result.isEmpty()) {
      try {
        redisStringOps.set(key, YdszJson.toJson(result), TTL_CHILDREN);
        log.debug("[NextwikiCacheService] 目录列表缓存回填: parentId={}, size={}", parentId, result.size());
      } catch (Exception e) {
        log.warn("[NextwikiCacheService] 目录列表缓存写入异常: parentId={}, err={}", parentId, e.getMessage());
      }
    }
    return result != null ? result : Collections.emptyList();
  }

  /**
   * 失效目录子节点列表缓存。
   *
   * @param parentId 父节点 ID
   */
  public void evictChildren(String parentId) {
    try {
      redisStringOps.delete(KEY_CHILDREN + parentId);
      log.debug("[NextwikiCacheService] 目录列表缓存失效: parentId={}", parentId);
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] 目录列表缓存失效异常: parentId={}, err={}", parentId, e.getMessage());
    }
  }

  // ==================== 配额用量缓存 ====================

  /**
   * 获取配额用量（优先缓存）。
   *
   * <p>缓存未命中时通过 {@code loader} 从数据库加载并回填缓存。
   *
   * @param scopeType 配额维度
   * @param scopeId 维度 ID
   * @param loader 数据库加载函数
   * @return 配额 VO；不存在返回 {@code Optional.empty()}
   */
  public Optional<StorageQuotaVO> getQuota(String scopeType, String scopeId,
      java.util.function.Supplier<Optional<StorageQuotaVO>> loader) {
    String key = KEY_QUOTA + scopeType + ":" + scopeId;
    try {
      StorageQuotaVO cached = redisStringOps.get(key, StorageQuotaVO.class);
      if (cached != null) {
        log.debug("[NextwikiCacheService] 配额用量缓存命中: {}:{}", scopeType, scopeId);
        return Optional.of(cached);
      }
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] 配额用量缓存读取异常: {}:{}, err={}", scopeType, scopeId, e.getMessage());
    }

    Optional<StorageQuotaVO> result = loader.get();
    result.ifPresent(vo -> {
      try {
        redisStringOps.set(key, vo, TTL_QUOTA);
        log.debug("[NextwikiCacheService] 配额用量缓存回填: {}:{}", scopeType, scopeId);
      } catch (Exception e) {
        log.warn("[NextwikiCacheService] 配额用量缓存写入异常: {}:{}, err={}", scopeType, scopeId, e.getMessage());
      }
    });
    return result;
  }

  /**
   * 失效配额用量缓存。
   *
   * @param scopeType 配额维度
   * @param scopeId 维度 ID
   */
  public void evictQuota(String scopeType, String scopeId) {
    try {
      redisStringOps.delete(KEY_QUOTA + scopeType + ":" + scopeId);
      log.debug("[NextwikiCacheService] 配额用量缓存失效: {}:{}", scopeType, scopeId);
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] 配额用量缓存失效异常: {}:{}, err={}", scopeType, scopeId, e.getMessage());
    }
  }

  // ==================== 批量失效 ====================

  /**
   * 文件操作后的缓存失效（文件详情 + 父目录列表）。
   *
   * <p>在文件创建/更新/删除/移动后调用，确保缓存与数据库一致。
   *
   * @param nodeId 文件节点 ID
   * @param parentId 父节点 ID（可为 null，表示仅失效文件详情）
   */
  public void evictFileAndParent(String nodeId, String parentId) {
    evictFile(nodeId);
    if (parentId != null && !parentId.isEmpty()) {
      evictChildren(parentId);
    }
  }

  /**
   * 配额变更后的缓存失效。
   *
   * <p>在上传/删除/恢复文件导致配额变化后调用。
   *
   * @param scopeType 配额维度
   * @param scopeId 维度 ID
   */
  public void evictQuotaOnChange(String scopeType, String scopeId) {
    evictQuota(scopeType, scopeId);
  }

  // ==================== AI 摘要缓存 ====================

  /** AI 摘要缓存前缀 */
  private static final String KEY_AI_SUMMARY = "nw:ai:summary:";

  /** AI 关键词缓存前缀 */
  private static final String KEY_AI_KEYWORDS = "nw:ai:keywords:";

  /**
   * 获取 AI 摘要缓存。
   *
   * @param key 缓存键（通常为内容哈希）
   * @return 缓存的摘要文本；不存在返回 {@code null}
   */
  public String getAiSummary(String key) {
    try {
      return redisStringOps.get(KEY_AI_SUMMARY + key, String.class);
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] AI 摘要缓存读取异常: err={}", e.getMessage());
      return null;
    }
  }

  /**
   * 写入 AI 摘要缓存。
   *
   * @param key 缓存键
   * @param summary 摘要文本
   * @param ttlSeconds 过期时间（秒）
   */
  public void putAiSummary(String key, String summary, int ttlSeconds) {
    try {
      redisStringOps.set(KEY_AI_SUMMARY + key, summary, ttlSeconds);
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] AI 摘要缓存写入异常: err={}", e.getMessage());
    }
  }

  /**
   * 获取 AI 关键词缓存。
   *
   * @param key 缓存键（通常为内容哈希）
   * @return 缓存的关键词列表；不存在返回 {@code null}
   */
  public List<String> getAiKeywords(String key) {
    try {
      String json = redisStringOps.get(KEY_AI_KEYWORDS + key, String.class);
      if (json != null && !json.isEmpty()) {
        return YdszJson.fromJson(json, List.class, String.class);
      }
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] AI 关键词缓存读取异常: err={}", e.getMessage());
    }
    return null;
  }

  /**
   * 写入 AI 关键词缓存。
   *
   * @param key 缓存键
   * @param keywords 关键词列表
   * @param ttlSeconds 过期时间（秒）
   */
  public void putAiKeywords(String key, List<String> keywords, int ttlSeconds) {
    try {
      redisStringOps.set(KEY_AI_KEYWORDS + key, YdszJson.toJson(keywords), ttlSeconds);
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] AI 关键词缓存写入异常: err={}", e.getMessage());
    }
  }
}
