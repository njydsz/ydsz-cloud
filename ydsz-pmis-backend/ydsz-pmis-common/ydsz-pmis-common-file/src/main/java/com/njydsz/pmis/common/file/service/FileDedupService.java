package com.njydsz.pmis.common.file.service;

import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.file.storage.IFileStorage;
import com.njydsz.pmis.common.redis.service.ops.RedisStringOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * 文件去重服务（秒传）
 *
 * 基于文件 SHA-256 和文件大小实现秒传功能。上传前先计算 SHA-256，
 * 如果相同大小且相同 SHA-256 的文件已存在，直接返回已有文件信息。
 * 采用文件大小 + SHA-256 双重校验，避免单一哈希碰撞导致的内容替换风险。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Service
@ConditionalOnBean({RedisStringOps.class, IFileStorage.class})
public class FileDedupService {

    private static final Logger log = LoggerFactory.getLogger(FileDedupService.class);
    private static final String DEDUP_KEY_PREFIX = "file:dedup:hash:";

    private final RedisStringOps redisStringOps;
    @SuppressWarnings("unused")
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

    /**
     * 清理过期的去重映射记录
     *
     * <p>由于去重映射在 Redis 中已设置 TTL，此方法主要用于
     * 记录清理日志和执行额外的清理逻辑。
     */
    public void cleanupExpiredEntries() {
        log.debug("去重映射记录基于 Redis TTL 自动过期，无需手动清理");
    }
}
