package com.njydsz.common.file.storage;

import java.util.Collections;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * 分片上传上下文存储委托实现
 * <p>优先使用 Redis 存储，当 Redis 不可用时降级到内存 Map。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
@Slf4j
public class DelegatingMultipartContextStore implements MultipartContextStore {

    /** 主存储（优先使用，通常为 Redis 实现） */
    private final MultipartContextStore primary;
    /** 降级存储（主存储不可用时使用） */
    private final MultipartContextStore fallback;

    /**
     * 构造分片上传上下文委托存储
     *
     * @param primary  主存储实现
     * @param fallback 降级存储实现
     */
    public DelegatingMultipartContextStore(MultipartContextStore primary, MultipartContextStore fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public void save(String uploadId, MultipartContextData context, long ttlSeconds) {
        if (primary != null) {
            try {
                primary.save(uploadId, context, ttlSeconds);
                return;
            } catch (Exception e) {
                log.warn("[Storage] primary multipart context store failed, falling back, message={}", e.getMessage());
            }
        }
        if (fallback != null) {
            fallback.save(uploadId, context, ttlSeconds);
        }
    }

    @Override
    public MultipartContextData get(String uploadId) {
        if (primary != null) {
            try {
                MultipartContextData data = primary.get(uploadId);
                if (data != null) {
                    return data;
                }
            } catch (Exception e) {
                log.warn("[Storage] primary multipart context get failed, falling back, message={}", e.getMessage());
            }
        }
        if (fallback != null) {
            return fallback.get(uploadId);
        }
        return null;
    }

    @Override
    public void remove(String uploadId) {
        if (primary != null) {
            try {
                primary.remove(uploadId);
            } catch (Exception e) {
                log.warn("[Storage] primary multipart context remove failed, message={}", e.getMessage());
            }
        }
        if (fallback != null) {
            fallback.remove(uploadId);
        }
    }

    @Override
    public Map<String, MultipartContextData> getAll() {
        if (primary != null) {
            try {
                return primary.getAll();
            } catch (Exception e) {
                log.warn("[Storage] primary multipart context getAll failed, falling back, message={}", e.getMessage());
            }
        }
        if (fallback != null) {
            return fallback.getAll();
        }
        return Collections.emptyMap();
    }

    @Override
    public void cleanExpired(int timeoutMinutes) {
        if (primary != null) {
            try {
                primary.cleanExpired(timeoutMinutes);
                return;
            } catch (Exception e) {
                log.warn("[Storage] primary multipart context cleanExpired failed, message={}", e.getMessage());
            }
        }
        if (fallback != null) {
            fallback.cleanExpired(timeoutMinutes);
        }
    }
}
