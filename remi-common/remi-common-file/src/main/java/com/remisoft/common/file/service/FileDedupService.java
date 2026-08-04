package com.remisoft.common.file.service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import com.remisoft.common.exception.custom.SysException;
import com.remisoft.common.file.storage.IFileStorage;
import com.remisoft.common.redis.service.ops.RedisStringOps;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件去重服务接口。
 * <p>基于文件内容 Hash（SHA-256）实现秒传/重删。
 *
 * @author remi-team
 * @since 1.0.0
 */


@Slf4j
public class FileDedupService {
    private static final String DEDUP_KEY_PREFIX = "file:dedup:hash:";

    private final RedisStringOps redisStringOps;

    private final IFileStorage fileStorage;

    public FileDedupService(RedisStringOps redisStringOps, IFileStorage fileStorage) {
        this.redisStringOps = redisStringOps;
        this.fileStorage = fileStorage;
    }

    /**
     * 计算输入流的 SHA-256 摘要
     */
    public String calculateHash(InputStream inputStream) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int len;
        try {
            while ((len = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
        } catch (Exception e) {
            throw new SysException("Failed to calculate SHA-256", e);
        }
        byte[] digestBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digestBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 构建去重 Key（文件大小:SHA-256 双重校验）
     *
     * @param fileSize 文件大小（字节）
     * @param hash     文件 SHA-256 摘要
     * @return 去重 Key
     */
    private String buildDedupKey(long fileSize, String hash) {
        return DEDUP_KEY_PREFIX + fileSize + ":" + hash;
    }

    /**
     * 检查文件是否已存在（秒传）
     *
     * @param fileSize 文件大小（字节）
     * @param hash     文件 SHA-256 摘要
     * @return 已存在的文件路径，如果不存在返回 null
     */
    public String checkExisting(long fileSize, String hash) {
        String key = buildDedupKey(fileSize, hash);
        String existingPath = redisStringOps.get(key, String.class);
        return existingPath;
    }

    /**
     * 注册文件哈希映射
     *
     * @param fileSize 文件大小（字节）
     * @param hash     文件 SHA-256 摘要
     * @param filePath 文件存储路径
     */
    public void registerHash(long fileSize, String hash, String filePath) {
        String key = buildDedupKey(fileSize, hash);
        // 设置 30 天过期
        redisStringOps.set(key, filePath, Duration.ofDays(30));
    }

}
