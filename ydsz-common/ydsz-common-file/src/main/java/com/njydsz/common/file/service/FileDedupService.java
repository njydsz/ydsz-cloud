package com.njydsz.common.file.service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件去重服务。
 *
 * <p>基于文件内容 Hash（SHA-256）实现秒传/重删。
 * 存储 Redis 映射时同时保存对象键，用于在命中缓存后验证文件实体是否仍然存在，
 * 避免生命周期清理导致的"幽灵秒传"（Redis 中有记录但对象已被物理删除）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class FileDedupService {

    private static final String DEDUP_KEY_PREFIX = "file:dedup:hash:";

    /**
     * 存储值分隔符：用于将 URL 和对象键拼合存储在一个 Redis String 中。
     * 对象键本身由服务端生成（不含此分隔符），URL 中的特殊字符也不会与此冲突。
     */
    private static final String VALUE_SEPARATOR = "|||";

    private final RedisStringOps redisStringOps;

    private final IFileStorage fileStorage;

    public FileDedupService(RedisStringOps redisStringOps, IFileStorage fileStorage) {
        this.redisStringOps = redisStringOps;
        this.fileStorage = fileStorage;
    }

    /**
     * 计算输入流的 SHA-256 摘要
     *
     * @param inputStream 输入流（方法会消费此流，调用者需自行重新获取）
     * @return 十六进制编码的 SHA-256 摘要字符串
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
            throw SysException.builder().message("Failed to calculate SHA-256").cause(e).build();
        }
        byte[] digestBytes = digest.digest();
        StringBuilder sb = new StringBuilder(digestBytes.length * 2);
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
     * 检查文件是否已存在（秒传）。
     *
     * <p>命中缓存后会通过 {@link IFileStorage#objectExists} 验证文件实体是否仍然存在。
     * 若文件已被物理删除（生命周期清理等），自动清理过期的 Redis 映射并返回 {@code null}，
     * 避免返回无效 URL。
     *
     * @param fileSize 文件大小（字节）
     * @param hash     文件 SHA-256 摘要
     * @return 已存在的文件访问地址，验证失败或不存在时返回 {@code null}
     */
    public String checkExisting(long fileSize, String hash) {
        String key = buildDedupKey(fileSize, hash);
        String storedValue = redisStringOps.get(key, String.class);
        if (storedValue == null) {
            return null;
        }

        // 解析存储值，分离 URL 和对象键
        String url = storedValue;
        String objectKey = null;
        int sepIndex = storedValue.indexOf(VALUE_SEPARATOR);
        if (sepIndex >= 0) {
            url = storedValue.substring(0, sepIndex);
            objectKey = storedValue.substring(sepIndex + VALUE_SEPARATOR.length());
        }

        // 仅当存储了对象键且能验证文件存在时才返回 URL
        if (objectKey != null) {
            if (verifyObjectExists(objectKey)) {
                return url;
            }
            // 文件已不存在，清理过期缓存
            log.info("[Dedup] stored file no longer exists, cleaning up cache, hash={}", hash);
            redisStringOps.delete(key);
            return null;
        }

        // 兼容旧格式（仅存储 URL 无对象键），无法验证直接返回
        return url;
    }

    /**
     * 验证对象键在存储中是否仍然存在
     *
     * @param objectKey 对象键
     * @return true 表示文件存在，false 表示不存在或验证失败
     */
    private boolean verifyObjectExists(String objectKey) {
        try {
            if (fileStorage == null) {
                return true;
            }
            return fileStorage.objectExists(null, objectKey);
        } catch (Exception e) {
            log.warn("[Dedup] objectExists verification failed, objectKey={}, message={}",
                    objectKey, e.getMessage());
            // 验证出错时宁可多上传一次，也不要返回无效 URL
            return true;
        }
    }

    /**
     * 注册文件哈希映射。
     *
     * <p>将 URL 与对象键拼接存储，格式为 {@code url|||objectKey}，
     * 以便后续 {@link #checkExisting} 能够验证文件实体是否仍然存在。
     *
     * @param fileSize  文件大小（字节）
     * @param hash      文件 SHA-256 摘要
     * @param filePath  文件访问 URL
     * @param objectKey 存储对象键（用于后续存在性验证）
     */
    public void registerHash(long fileSize, String hash, String filePath, String objectKey) {
        String key = buildDedupKey(fileSize, hash);
        String storedValue = StringUtils.isNotBlank(objectKey)
                ? filePath + VALUE_SEPARATOR + objectKey
                : filePath;
        redisStringOps.set(key, storedValue, Duration.ofDays(30));
    }

}
