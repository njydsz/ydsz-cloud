package com.njydsz.common.json.spring;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.StreamUtils;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.provider.DeserializationProvider;
import com.njydsz.common.json.provider.SerializationProvider;

/**
 * YdszJson HTTP 消息转换器。
 *
 * <p>通用 JSON 消息转换器，支持所有 Java 对象类型的 JSON 序列化/反序列化。 自动注册到 Spring MVC 的 {@code HttpMessageConverter}
 * 链中。
 *
 * <p>支持 {@code application/json} 和 {@code application/*+json} 媒体类型。
 *
 * <p><b>安全与性能：</b>
 *
 * <ul>
 *   <li>读取时双重防护：Content-Length 预检 + 流式字节计数，覆盖 Content-Length 伪造与 chunked encoding 场景
 *   <li>写入使用缓冲模式：序列化为 byte[] 后设置 Content-Length 一次性写出，避免 chunked 编码开销
 *   <li>不手动 flush，由 Spring 框架统一管理输出流生命周期
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class JsonHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {

  /** 默认最大请求体大小（10MB），超过此值的请求将被拒绝 */
  private static final long MAX_REQUEST_BODY_SIZE = 10L * 1024 * 1024;

  /** 读取缓冲区大小（8KB，平衡内存占用与系统调用次数） */
  private static final int READ_BUFFER_SIZE = 8192;

  /** 可配置的最大请求体大小（默认与 MAX_REQUEST_BODY_SIZE 相同） */
  private long maxRequestBodySize = MAX_REQUEST_BODY_SIZE;

  /** 构造函数，注册支持的媒体类型。 */
  public JsonHttpMessageConverter() {
    super(
        StandardCharsets.UTF_8, MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
  }

  @Override
  protected boolean supports(Class<?> clazz) {
    // 通用转换器，支持所有非 CharSequence 类型
    return !CharSequence.class.isAssignableFrom(clazz);
  }

  /**
   * 设置最大请求体大小。
   *
   * @param maxRequestBodySize 最大请求体大小（字节）
   * @since 1.0.0
   */
  public void setMaxRequestBodySize(long maxRequestBodySize) {
    this.maxRequestBodySize = maxRequestBodySize;
  }

  /**
   * 读取泛型类型请求体（P1-5: 支持 @RequestBody List&lt;User&gt;等泛型类型）。
   *
   * <p>重写父类的 {@code read(Type, Class, HttpInputMessage)} 方法，当 {@code type} 为 {@link
   * ParameterizedType} 时委托 {@link DeserializationProvider#deserialize(byte[], Type)} 处理泛型类型。
   *
   * @param type 目标类型（可能是 Class 或 ParameterizedType）
   * @param contextClass 上下文类
   * @param inputMessage HTTP 输入消息
   * @return 反序列化后的对象
   * @throws IOException 读取失败
   * @throws HttpMessageNotReadableException JSON 解析失败
   * @since 1.0.0
   */
  @Override
  protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
      throws IOException, HttpMessageNotReadableException {
    return read(clazz, null, inputMessage);
  }

  /**
   * 重写父类的 {@code read(Type, Class, HttpInputMessage)} 方法，当 {@code type} 为 {@link ParameterizedType}
   * 时委托 {@code DeserializationProvider.deserialize(byte[], Type)} 处理泛型类型。
   */
  @Override
  public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage)
      throws IOException, HttpMessageNotReadableException {
    try {
      long contentLength = inputMessage.getHeaders().getContentLength();
      if (contentLength > maxRequestBodySize) {
        throw new IOException(
            "Request body too large (Content-Length): "
                + contentLength
                + " > "
                + maxRequestBodySize);
      }
      // 预估容量：优先用 Content-Length 提示，避免 ByteArrayOutputStream 频繁扩容
      int estimatedSize =
          contentLength > 0 ? (int) Math.min(contentLength, maxRequestBodySize) : READ_BUFFER_SIZE;
      byte[] body = readBoundedBytes(inputMessage.getBody(), maxRequestBodySize, estimatedSize);
      if (body.length == 0) {
        return null;
      }
      // 使用 ResolvableType 解析泛型类型
      if (type instanceof ParameterizedType) {
        return DeserializationProvider.deserialize(body, type);
      }
      // 非 ParameterizedType 退回常规路径
      Class<?> rawClass = type instanceof Class<?> c ? c : Object.class;
      return YdszJson.fromJsonBytes(body, rawClass);
    } catch (Exception e) {
      if (e instanceof IOException) {
        throw (IOException) e;
      }
      throw new HttpMessageNotReadableException("JSON 解析失败：" + e.getMessage(), e, inputMessage);
    } finally {
      // 请求结束后清理 ThreadLocal 资源，防止 Tomcat 线程池泄漏
      SerializationProvider.clearThreadLocals();
    }
  }

  /**
   * 从输入流中读取最多 maxBytes 字节，超限时立即抛 IOException。
   *
   * <p>相比 {@link InputStream#readAllBytes()} 的无界读取，本方法在读取过程中实时 累计已读字节数，超过阈值即抛异常，防止攻击者通过伪造
   * Content-Length 或使用 chunked encoding 绕过预检。
   *
   * @param input 原始输入流
   * @param maxBytes 最大允许读取字节数
   * @param estimatedSize 预估容量（用于 ByteArrayOutputStream 初始分配，避免频繁扩容）
   * @return 读取的字节数组（长度不超过 maxBytes）
   * @throws IOException 读取失败或超过大小限制
   * @since 1.0.0
   */
  private static byte[] readBoundedBytes(InputStream input, long maxBytes, int estimatedSize)
      throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(estimatedSize);
    byte[] chunk = new byte[READ_BUFFER_SIZE];
    long totalRead = 0;
    int n;
    while ((n = input.read(chunk)) != -1) {
      totalRead += n;
      if (totalRead > maxBytes) {
        throw new IOException(
            "Request body exceeds maximum size: "
                + maxBytes
                + " (read "
                + totalRead
                + " bytes so far)");
      }
      buffer.write(chunk, 0, n);
    }
    return buffer.toByteArray();
  }

  @Override
  protected void writeInternal(Object o, HttpOutputMessage outputMessage)
      throws IOException, HttpMessageNotWritableException {
    try {
      OutputStream out = outputMessage.getBody();

      // 缓冲模式：序列化为 byte[] 后设置 Content-Length 一次性写出
      byte[] bytes = YdszJson.toJsonBytes(o);
      // 设置 Content-Length，避免 HTTP chunked 编码开销
      outputMessage.getHeaders().setContentLength(bytes.length);
      StreamUtils.copy(bytes, out);
      // 不手动 flush，由 Spring 框架统一管理输出流生命周期
    } catch (Exception e) {
      throw new HttpMessageNotWritableException("JSON 序列化失败：" + e.getMessage(), e);
    } finally {
      // 请求结束后清理 ThreadLocal 资源（StringBuilder/JSONWriter/循环引用检测集），防止 Tomcat 线程池泄漏
      SerializationProvider.clearThreadLocals();
    }
  }

  @Override
  protected void writeInternal(Object o, Type type, HttpOutputMessage outputMessage)
      throws IOException, HttpMessageNotWritableException {
    writeInternal(o, outputMessage);
  }
}
