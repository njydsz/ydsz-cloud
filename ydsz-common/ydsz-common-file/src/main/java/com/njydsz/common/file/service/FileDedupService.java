package com.njydsz.common.file.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.common.util.string.StringUtils;

/**
 * 文件去重服务。
 *
 * <p>基于文件内容 Hash（SHA-256）实现秒传/重删。 Redis 映射设置 30 天 TTL，依赖存储端生命周期策略自动清理过期文件， 避免"幽灵秒传"（Redis
 * 中有记录但对象已被物理删除）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class FileDedupService {

  private static final String DEDUP_KEY_PREFIX = "file:dedup:hash:";

  /** 存储值分隔符：用于将 URL 和对象键拼合存储在一个 Redis String 中。 对象键本身由服务端生成（不含此分隔符），URL 中的特殊字符也不会与此冲突。 */
  private static final String VALUE_SEPARATOR = "|||";

  private final RedisStringOps redisStringOps;

  public FileDedupService(RedisStringOps redisStringOps) {
    this.redisStringOps = redisStringOps;
  }

  /**
   * 计算输入流的 SHA-256 摘要
   *
   * @param inputStream 输入流（方法会消费此流，调用者需自行重新获取）
   * @return 十六进制编码的 SHA-256 摘要字符串
   */
  public String calculateHash(InputStream inputStream) throws IOException {
    // 云顶规范 §22.5：复用 common-util 的 DigestUtils，禁止自建哈希（支持流式计算）
    return DigestUtils.sha256Hex(inputStream);
  }

  /**
   * 构建去重 Key（文件大小:SHA-256 双重校验）
   *
   * @param fileSize 文件大小（字节）
   * @param hash 文件 SHA-256 摘要
   * @return 去重 Key
   */
  private String buildDedupKey(long fileSize, String hash) {
    return DEDUP_KEY_PREFIX + fileSize + ":" + hash;
  }

  /**
   * 检查文件是否已存在（秒传）。
   *
   * <p>基于 Redis 缓存映射判断文件是否已上传，命中即返回已存储的 URL。 依赖存储端生命周期策略（Bucket Lifecycle）自动清理过期文件， 无需额外调用存储 API
   * 验证文件实体是否存在，减少一次远程 RPC。
   *
   * @param fileSize 文件大小（字节）
   * @param hash 文件 SHA-256 摘要
   * @return 已存在的文件访问地址，不存在时返回 {@code null}
   */
  public String checkExisting(long fileSize, String hash) {
    String key = buildDedupKey(fileSize, hash);
    String storedValue = redisStringOps.get(key, String.class);
    if (storedValue == null) {
      return null;
    }

    // 解析存储值，分离 URL 和对象键
    String url = storedValue;
    int sepIndex = storedValue.indexOf(VALUE_SEPARATOR);
    if (sepIndex >= 0) {
      url = storedValue.substring(0, sepIndex);
    }
    return url;
  }

  /**
   * 注册文件哈希映射。
   *
   * <p>将 URL 与对象键拼接存储，格式为 {@code url|||objectKey}， 以便后续 {@link #checkExisting} 能够解析。
   *
   * @param fileSize 文件大小（字节）
   * @param hash 文件 SHA-256 摘要
   * @param filePath 文件访问 URL
   * @param objectKey 存储对象键
   */
  public void registerHash(long fileSize, String hash, String filePath, String objectKey) {
    String key = buildDedupKey(fileSize, hash);
    String storedValue =
        StringUtils.isNotBlank(objectKey) ? filePath + VALUE_SEPARATOR + objectKey : filePath;
    redisStringOps.set(key, storedValue, Duration.ofDays(30));
  }
}
