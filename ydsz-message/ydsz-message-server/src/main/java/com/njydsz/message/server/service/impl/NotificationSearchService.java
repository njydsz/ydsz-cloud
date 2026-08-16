package com.njydsz.message.server.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.njydsz.common.redis.service.ops.RedisCollectionOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * P2-18: 站内通知全文搜索。
 *
 * <p>基于 Redis 倒排索引实现轻量级全文搜索，避免引入 Elasticsearch 的运维成本。
 *
 * <p>实现原理：
 * <ul>
 *   <li>对通知标题和内容进行分词（简单按空格/标点分割）</li>
 *   <li>将每个词映射到通知 ID 集合（Redis Set）</li>
 *   <li>搜索时取多个词的交集（SINTER）或并集（SUNION）</li>
 *   <li>支持按用户隔离索引</li>
 * </ul>
 *
 * <p>Redis Key 格式：{@code search:idx:{userId}:{keyword}} → Set<notificationId>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSearchService {

    private final RedisCollectionOps redisCollectionOps;
    private final RedisStringOps redisStringOps;

    private static final String INDEX_KEY_PREFIX = "search:idx:";
    private static final long INDEX_TTL_DAYS = 30L;

    /**
     * 索引单条通知。
     *
     * @param userId         用户 ID
     * @param notificationId 通知 ID
     * @param title          标题
     * @param content        内容
     */
    public void index(String userId, String notificationId, String title, String content) {
        String text = (title != null ? title : "") + " " + (content != null ? content : "");
        Set<String> keywords = tokenize(text);
        for (String keyword : keywords) {
            if (keyword.length() < 2) {
                continue;
            }
            String key = INDEX_KEY_PREFIX + userId + ":" + keyword.toLowerCase();
            redisCollectionOps.sAdd(key, notificationId);
            redisStringOps.expire(key, Duration.ofDays(INDEX_TTL_DAYS));
        }
    }

    /**
     * 搜索通知。
     *
     * @param userId   用户 ID
     * @param keywords 搜索关键词（空格分隔）
     * @return 匹配的通知 ID 列表
     */
    public List<String> search(String userId, String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        Set<String> tokens = tokenize(keywords);
        if (tokens.isEmpty()) {
            return List.of();
        }
        List<String> keys = tokens.stream()
                .filter(t -> t.length() >= 2)
                .map(t -> INDEX_KEY_PREFIX + userId + ":" + t.toLowerCase())
                .collect(Collectors.toList());
        if (keys.isEmpty()) {
            return List.of();
        }
        // 取所有关键词的并集
        Set<String> ids = redisCollectionOps.sUnion(String.class, keys.toArray(new String[0]));
        if (ids == null) {
            return List.of();
        }
        return new ArrayList<>(ids);
    }

    /**
     * 从索引中移除通知。
     *
     * @param userId         用户 ID
     * @param notificationId 通知 ID
     * @param title          标题
     * @param content        内容
     */
    public void removeIndex(String userId, String notificationId, String title, String content) {
        String text = (title != null ? title : "") + " " + (content != null ? content : "");
        Set<String> keywords = tokenize(text);
        for (String keyword : keywords) {
            if (keyword.length() < 2) {
                continue;
            }
            String key = INDEX_KEY_PREFIX + userId + ":" + keyword.toLowerCase();
            redisCollectionOps.sRem(key, notificationId);
        }
    }

    /**
     * 简单分词（按空格/标点分割）。
     *
     * @param text 原始文本
     * @return 分词结果
     */
    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.split("[\\s,，。\\.！!？?；;：:、\\-_/]+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }
}
