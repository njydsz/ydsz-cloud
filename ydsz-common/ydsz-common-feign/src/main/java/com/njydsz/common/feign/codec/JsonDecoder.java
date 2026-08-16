package com.njydsz.common.feign.codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;

import com.njydsz.common.json.YdszJson;

/**
 * 基于 Jackson 的 Feign JSON 解码器。
 *
 * <p>使用 {@link YdszJson} 作为 JSON 反序列化实现，提供统一的 JSON 解码能力。
 *
 * <p>支持的返回类型：
 *
 * <ul>
 *   <li>普通 Bean 对象
 *   <li>Map&lt;String, Object&gt;
 *   <li>Collection 类型（List/Set）
 *   <li>简单类型（String、Number、Boolean）
 *   <li>带泛型的复杂类型
 *   <li>{@code void} / {@code Void}
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see JsonEncoder
 */
public class JsonDecoder implements Decoder {

  private static final Logger LOG = LoggerFactory.getLogger(JsonDecoder.class);

  /** 默认最大响应体字节数（10MB） */
  private static final int DEFAULT_MAX_BODY_BYTES = 10 * 1024 * 1024;

  /** 最大允许的响应体字节数 */
  private final int maxBodyBytes;

  /** 使用默认最大响应体大小（10MB）构造解码器。 */
  public JsonDecoder() {
    this(DEFAULT_MAX_BODY_BYTES);
  }

  /**
   * 使用自定义最大响应体大小构造解码器。
   *
   * @param maxBodyBytes 最大响应体字节数，小于等于 0 时使用默认值
   */
  public JsonDecoder(int maxBodyBytes) {
    this.maxBodyBytes = maxBodyBytes > 0 ? maxBodyBytes : DEFAULT_MAX_BODY_BYTES;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response == null) {
      return null;
    }

    int status = response.status();
    if (status == 204 || status == 205) {
      return null;
    }

    if (isVoidType(type)) {
      return null;
    }

    if (!hasBody(response)) {
      return null;
    }

    String bodyContent = readBody(response);

    if (bodyContent == null || bodyContent.isEmpty()) {
      return null;
    }

    return decodeBody(bodyContent, type, response);
  }

  private boolean isVoidType(Type type) {
    if (type == null) {
      return true;
    }
    if (type instanceof Class) {
      Class<?> clazz = (Class<?>) type;
      return clazz == void.class || clazz == Void.class;
    }
    return false;
  }

  private boolean hasBody(Response response) {
    return response.body() != null;
  }

  /**
   * 读取响应体内容，超过最大字节数限制时抛出异常。
   *
   * <p>使用 {@link ByteArrayOutputStream} 自动扩容，初始容量设为 64KB， 避免小初始缓冲区导致的频繁数组拷贝（旧实现从 8KB 开始翻倍扩容， 5MB
   * 响应体将触发 ~120 次数组复制）。
   *
   * @param response HTTP 响应对象
   * @return 响应体的 UTF-8 字符串内容
   * @throws IOException 读取响应体失败或响应体超过最大限制时抛出
   */
  private String readBody(Response response) throws IOException {
    try (InputStream inputStream = response.body().asInputStream()) {
      // 初始容量 64KB，平衡了小响应体的内存占用和大响应体的扩容次数
      ByteArrayOutputStream buffer = new ByteArrayOutputStream(65536);
      byte[] chunk = new byte[8192];
      int totalRead = 0;
      int bytesRead;

      while ((bytesRead = inputStream.read(chunk)) != -1) {
        if (totalRead + bytesRead > maxBodyBytes) {
          throw new DecodeException(
              500, "响应体超过最大限制: " + maxBodyBytes + " bytes", response.request(), null);
        }
        buffer.write(chunk, 0, bytesRead);
        totalRead += bytesRead;
      }

      return buffer.toString(StandardCharsets.UTF_8.name());
    }
  }

  /**
   * 使用 Jackson 将响应体字符串解码为目标类型。
   *
   * @param body 响应体字符串
   * @param type 目标解码类型
   * @param response HTTP 响应对象（用于构建异常信息）
   * @return 解码后的对象
   * @throws DecodeException JSON 解码失败时抛出
   */
  private Object decodeBody(String body, Type type, Response response) {
    try {
      return YdszJson.fromJson(body, type);
    } catch (Exception e) {
      LOG.warn("JSON 解码失败, 类型: {}, 错误: {}", type, e.getMessage());
      throw new DecodeException(500, "JSON 解码失败: " + e.getMessage(), response.request(), e);
    }
  }
}
