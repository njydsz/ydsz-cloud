package com.njydsz.pmis.common.file.storage;

import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Redis 的分片上传上下文存储实现
 * <p>支持多实例部署时分片上下文共享。
 *
 * @author ydsz-pmis-team
 * 
 * 
 */
@Slf4j
public class RedisMultipartContextStore implements MultipartContextStore {

    /** Redis 键前缀 */
    private static final String REDIS_KEY_PREFIX = "remi:file:multipart:";
    /** SCAN 命令匹配模式 */
    private static final String SCAN_PATTERN = REDIS_KEY_PREFIX + "*";

    /** Redis 操作模板 */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造 Redis 分片上传上下文存储
     *
     * @param stringRedisTemplate Redis 操作模板
     */
    public RedisMultipartContextStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 保存分片上传上下文到 Redis
     *
     * @param uploadId   分片上传会话 ID
     * @param context    分片上传上下文数据
     * @param ttlSeconds 生存时间（秒）
     */
    @Override
    public void save(String uploadId, MultipartContextData context, long ttlSeconds) {
        if (StringUtils.isBlank(uploadId) || context == null) {
            return;
        }
        try {
            String key = buildKey(uploadId);
            String json = JsonUtils.toJson(context);
            stringRedisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("[Storage] RedisMultipartContextStore save failed, uploadId={}, message={}",
                    uploadId, e.getMessage());
        }
    }

    /**
     * 获取分片上传上下文
     *
     * @param uploadId 分片上传会话 ID
     * @return 上下文数据，不存在时返回 null
     */
    @Override
    public MultipartContextData get(String uploadId) {
        if (StringUtils.isBlank(uploadId)) {
            return null;
        }
        try {
            String key = buildKey(uploadId);
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return JsonUtils.fromJson(json, MultipartContextData.class);
        } catch (Exception e) {
            log.warn("[Storage] RedisMultipartContextStore get failed, uploadId={}, message={}",
                    uploadId, e.getMessage());
            return null;
        }
    }

    /**
     * 删除分片上传上下文
     *
     * @param uploadId 分片上传会话 ID
     */
    @Override
    public void remove(String uploadId) {
        if (StringUtils.isBlank(uploadId)) {
            return;
        }
        try {
            String key = buildKey(uploadId);
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[Storage] RedisMultipartContextStore remove failed, uploadId={}, message={}",
                    uploadId, e.getMessage());
        }
    }

    /**
     * 获取所有分片上传上下文
     *
     * @return uploadId 到上下文数据的映射
     */
    @Override
    public Map<String, MultipartContextData> getAll() {
        Map<String, MultipartContextData> result = new ConcurrentHashMap<>();
        try {
            List<String> keys = scanKeys();
            if (keys.isEmpty()) {
                return result;
            }
            List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
            if (values != null) {
                for (int i = 0; i < keys.size(); i++) {
                    String json = values.get(i);
                    if (json != null) {
                        String uploadId = extractUploadId(keys.get(i));
                        MultipartContextData context = JsonUtils.fromJson(json, MultipartContextData.class);
                        result.put(uploadId, context);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Storage] RedisMultipartContextStore getAll failed, message={}", e.getMessage());
        }
        return result;
    }

    /**
     * 清理过期的分片上传上下文
     *
     * @param timeoutMinutes 超时时间（分钟），超过此时间的上下文将被删除
     */
    @Override
    public void cleanExpired(int timeoutMinutes) {
        long cutoffTime = System.currentTimeMillis() - (timeoutMinutes * 60L * 1000L);
        try {
            List<String> keys = scanKeys();
            if (keys.isEmpty()) {
                return;
            }
            List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return;
            }

            List<String> expiredKeys = new ArrayList<>();
            for (int i = 0; i < keys.size(); i++) {
                String json = values.get(i);
                if (json != null) {
                    try {
                        MultipartContextData context = JsonUtils.fromJson(json, MultipartContextData.class);
                        if (context != null && context.lastAccessTime() < cutoffTime) {
                            expiredKeys.add(keys.get(i));
                        }
                    } catch (Exception e) {
                        // ignore parse errors, treat as expired
                        expiredKeys.add(keys.get(i));
                    }
                } else {
                    expiredKeys.add(keys.get(i));
                }
            }

            if (!expiredKeys.isEmpty()) {
                stringRedisTemplate.delete(expiredKeys);
                log.info("[Storage] cleaned {} expired multipart contexts", expiredKeys.size());
            }
        } catch (Exception e) {
            log.warn("[Storage] RedisMultipartContextStore cleanExpired failed, message={}", e.getMessage());
        }
    }

    /**
     * 使用 SCAN 扫描匹配的键（避免 KEYS 阻塞）
     */
    private List<String> scanKeys() {
        List<String> keys = new ArrayList<>();
        try {
            org.springframework.data.redis.core.ScanOptions options =
                    org.springframework.data.redis.core.ScanOptions.scanOptions()
                            .match(SCAN_PATTERN)
                            .count(100)
                            .build();

            stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                try (org.springframework.data.redis.core.Cursor<byte[]> cursor =
                             connection.keyCommands().scan(options)) {
                    if (cursor != null) {
                        while (cursor.hasNext()) {
                            keys.add(new String(cursor.next(), java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("[Storage] scan multipart keys failed, message={}", e.getMessage());
        }
        return keys;
    }

    private String buildKey(String uploadId) {
        return REDIS_KEY_PREFIX + uploadId;
    }

    private String extractUploadId(String redisKey) {
        if (redisKey != null && redisKey.startsWith(REDIS_KEY_PREFIX)) {
            return redisKey.substring(REDIS_KEY_PREFIX.length());
        }
        return redisKey;
    }
}
