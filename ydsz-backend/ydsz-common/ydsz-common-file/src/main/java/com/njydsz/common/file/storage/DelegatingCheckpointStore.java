package com.njydsz.common.file.storage;

import lombok.extern.slf4j.Slf4j;

/**
 * 检查点存储委托实现
 * <p>优先使用 Redis 存储，当 Redis 不可用时降级到本地文件。
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
@Slf4j
public class DelegatingCheckpointStore implements CheckpointStore {

    /** 主存储（优先使用，通常为 Redis 实现） */
    private final CheckpointStore primary;
    /** 降级存储（主存储不可用时使用） */
    private final CheckpointStore fallback;

    /**
     * 构造检查点委托存储
     *
     * @param primary  主存储实现
     * @param fallback 降级存储实现
     */
    public DelegatingCheckpointStore(CheckpointStore primary, CheckpointStore fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public void save(String bucketName, String objectName, String checkpoint, long ttlSeconds) {
        if (primary != null) {
            try {
                primary.save(bucketName, objectName, checkpoint, ttlSeconds);
                return;
            } catch (Exception e) {
                log.warn("[Storage] primary checkpoint store failed, falling back, message={}", e.getMessage());
            }
        }
        if (fallback != null) {
            fallback.save(bucketName, objectName, checkpoint, ttlSeconds);
        }
    }

    @Override
    public String get(String bucketName, String objectName) {
        if (primary != null) {
            try {
                String data = primary.get(bucketName, objectName);
                if (data != null) {
                    return data;
                }
            } catch (Exception e) {
                log.warn("[Storage] primary checkpoint get failed, falling back, message={}", e.getMessage());
            }
        }
        if (fallback != null) {
            return fallback.get(bucketName, objectName);
        }
        return null;
    }

    @Override
    public void remove(String bucketName, String objectName) {
        if (primary != null) {
            try {
                primary.remove(bucketName, objectName);
            } catch (Exception e) {
                log.warn("[Storage] primary checkpoint remove failed, message={}", e.getMessage());
            }
        }
        if (fallback != null) {
            fallback.remove(bucketName, objectName);
        }
    }

    @Override
    public String buildKey(String bucketName, String objectName) {
        if (primary != null) {
            return primary.buildKey(bucketName, objectName);
        }
        if (fallback != null) {
            return fallback.buildKey(bucketName, objectName);
        }
        return null;
    }
}
