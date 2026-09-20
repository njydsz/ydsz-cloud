package com.njydsz.common.file.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.zip.Adler32;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.string.StringUtils;

/**
 * 文件去重服务。
 *
 * <p>基于文件内容 Hash 实现秒传/重删。采用两级校验：
 *
 * <ol>
 *   <li><b>adler32 一级校验</b>：32 位流式校验和（O(1) 内存、纳秒/字节），Redis 索引极低碰撞成本， 用于快速排除"绝对不重复"的文件。
 *   <li><b>SHA-256 二级校验</b>：仅在 adler32 匹配时触发，零误判，确认真正的重复。
 * </ol>
 *
 * <p>Redis 主结构：{@code file:dedup:fast:{size}:{adler32}} → sha256-hex 占位、 {@code file:dedup:hash:{size}:{sha256}} → "url|||objectKey"。
 * 两层结构共享 TTL（30 天），避免"幽灵秒传"。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class FileDedupService {

  private static final String DEDUP_KEY_PREFIX = "file:dedup:hash:";

  /** adler32 一级校验快索引前缀（仅存 sha256-hex，用作存在性暗示） */
  private static final String DEDUP_FAST_KEY_PREFIX = "file:dedup:fast:";

  /** 存储值分隔符：用于将 URL 和对象键拼合存储在一个 Redis String 中。 对象键本身由服务端生成（不含此分隔符），URL 中的特殊字符也不会与此冲突。 */
  private static final String VALUE_SEPARATOR = "|||";

  /** 去重哈希映射的默认 TTL（天），默认 30 天，依赖存储端生命周期策略自动清理过期文件。 */
  @Value("${ydsz.common.file.dedup-hash-ttl-days:30}")
  private long dedupHashTtlDays;

  private final RedisStringOps redisStringOps;

  public FileDedupService(RedisStringOps redisStringOps) {
    this.redisStringOps = redisStringOps;
  }

  /**
   * 内容哈希指纹（adler32 + SHA-256）
   *
   * <p>单遍流读取同时更新两个摘要状态，比分两次读取流节省 50% IO。
   *
   * @param adler32 32 位 adler32 校验和（用作一级快索引）
   * @param sha256Hex SHA-256 十六进制摘要（用作二级确定性校验）
   */
  public record FileContentHash(long adler32, String sha256Hex) {

    /**
     * 构建 adler32 一级校验键
     *
     * @param fileSize 文件大小（字节）
     * @return adler32 快索引 Redis 键
     */
    public String fastKey(long fileSize) {
      return DEDUP_FAST_KEY_PREFIX + fileSize + ":" + Long.toHexString(adler32);
    }
  }

  /**
   * 单遍流读取同时计算 adler32 与 SHA-256 摘要，节省 50% IO。
   *
   * @param inputStream 输入流（方法会消费此流，调用者需自行重新获取）
   * @return 包含 adler32 与 sha256Hex 的内容指纹
   */
  public FileContentHash calculateHash(InputStream inputStream) throws IOException {
    try {
      Adler32 adler = new Adler32();
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[64 * 1024];
      int len;
      while ((len = inputStream.read(buffer)) != -1) {
        adler.update(buffer, 0, len);
        sha256.update(buffer, 0, len);
      }
      String sha256Hex = HexFormat.of().formatHex(sha256.digest());
      return new FileContentHash(adler.getValue(), sha256Hex);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 是 JVM 必选实现的算法；此处仅做防御性处理
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  /**
   * 构建 SHA-256 去重主键
   *
   * @param fileSize 文件大小（字节）
   * @param hash 文件内容指纹
   * @return 去重 Key
   */
  private String buildDedupKey(long fileSize, FileContentHash hash) {
    return DEDUP_KEY_PREFIX + fileSize + ":" + hash.sha256Hex();
  }

  /**
   * 检查文件是否已存在（秒传）。
   *
   * <p>采用两级校验：
   * <ol>
   *   <li><b>adler32 快索引</b>：Redis GET 成本极低，miss 即返回（新文件极大可能命中此分支）。
   *   <li><b>SHA-256 主键</b>：仅在 adler32 命中后做最终确认，零误判。
   * </ol>
   *
   * @param fileSize 文件大小（字节）
   * @param hash 文件内容指纹（adler32 + SHA-256）
   * @return 已存在的文件访问地址，不存在时返回 {@code null}
   */
  public String checkExisting(long fileSize, FileContentHash hash) {
    // Phase 1: adler32 一级校验
    String fastKey = hash.fastKey(fileSize);
    String fastHit = redisStringOps.get(fastKey, String.class);
    if (fastHit == null || !fastHit.equals(hash.sha256Hex())) {
      return null;
    }
    // Phase 2: SHA-256 主键确认（双保险）
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
   * 注册文件哈希映射（两级索引同步写入）。
   *
   * <p>将 URL 与对象键拼接存储在主键中，格式为 {@code url|||objectKey}。 同时写入 adler32 快索引，使后续上传能利用一级校验快速判断。
   *
   * @param fileSize 文件大小（字节）
   * @param hash 文件内容指纹（adler32 + SHA-256）
   * @param filePath 文件访问 URL
   * @param objectKey 存储对象键
   */
  public void registerHash(long fileSize, FileContentHash hash, String filePath, String objectKey) {
    Duration ttl = Duration.ofDays(dedupHashTtlDays);
    // 主索引: SHA-256 → "url|||objectKey"
    String primaryKey = buildDedupKey(fileSize, hash);
    String storedValue =
        StringUtils.isNotBlank(objectKey) ? filePath + VALUE_SEPARATOR + objectKey : filePath;
    redisStringOps.set(primaryKey, storedValue, ttl);
    // 快索引: adler32 → sha256-hex（用作快速 NEW 文件判定）
    redisStringOps.set(hash.fastKey(fileSize), hash.sha256Hex(), ttl);
  }
}
