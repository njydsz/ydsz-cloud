package com.njydsz.common.feign.aspect;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.njydsz.common.feign.config.FeignProperties;
import com.njydsz.common.feign.exception.BadRequestException;
import com.njydsz.common.feign.exception.NotFoundException;
import com.njydsz.common.feign.exception.OpenFeignException;

/**
 * YdszFeign 错误解码器。
 *
 * <p>将 Feign 调用返回的 HTTP 响应状态码转换为对应的业务异常， 提供更友好的错误信息和可读性。
 *
 * <p>支持的 HTTP 状态码映射：
 *
 * <ul>
 *   <li>{@code 400 Bad Request} - 转换为 {@link BadRequestException}
 *   <li>{@code 401 Unauthorized} - 转换为 {@link OpenFeignException}（错误码 401）
 *   <li>{@code 403 Forbidden} - 转换为 {@link OpenFeignException}（错误码 403）
 *   <li>{@code 404 Not Found} - 转换为 {@link NotFoundException}
 *   <li>{@code 429 Too Many Requests} - 转换为 {@link OpenFeignException}（错误码 429）
 *   <li>{@code 500 Internal Server Error} - 转换为 {@link OpenFeignException}（错误码 500）
 *   <li>{@code 502 Bad Gateway} - 转换为 {@link OpenFeignException}（错误码 502）
 *   <li>{@code 503 Service Unavailable} - 转换为 {@link OpenFeignException}（错误码 503）
 *   <li>{@code 504 Gateway Timeout} - 转换为 {@link OpenFeignException}（错误码 504）
 * </ul>
 *
 * <p>错误消息格式：
 *
 * <pre>
 * Feign call failed, method: {methodKey}, request: {httpMethod} {url}, status: {status}, reason: {reason}
 * </pre>
 *
 * <p>可通过配置控制是否在异常信息中包含响应体：
 *
 * <ul>
 *   {@code ydsz.feign.error.include-body=true} - 包含响应体（默认） {@code
 *   ydsz.feign.error.include-body=false} - 不包含响应体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see BadRequestException
 * @see NotFoundException
 * @see OpenFeignException
 */
public class YdszFeignErrorDecoder implements ErrorDecoder {

  private static final Logger LOG = LoggerFactory.getLogger(YdszFeignErrorDecoder.class);

  private final ErrorDecoder defaultErrorDecoder = new Default();
  private final FeignProperties feignProperties;

  /**
   * 使用自定义配置构造错误解码器。
   *
   * @param feignProperties Feign 配置属性
   */
  public YdszFeignErrorDecoder(FeignProperties feignProperties) {
    this.feignProperties = feignProperties;
  }

  /**
   * 根据 HTTP 响应状态码解码为具体的异常对象。
   *
   * @param methodKey Feign 方法标识，格式为 {@code ServiceName#methodName(params)}
   * @param response HTTP 响应对象
   * @return 解码后的异常对象
   */
  @Override
  public Exception decode(String methodKey, Response response) {
    LOG.debug("Feign 调用失败, method: {}, status: {}", methodKey, response.status());

    switch (response.status()) {
      case 400:
        return new BadRequestException(buildErrorMessage(methodKey, response));

      case 404:
        return new NotFoundException(buildErrorMessage(methodKey, response));

      case 401:
        return new OpenFeignException("401", buildErrorMessage(methodKey, response));

      case 403:
        return new OpenFeignException("403", buildErrorMessage(methodKey, response));

      case 429:
        return new OpenFeignException("429", buildErrorMessage(methodKey, response));

      case 500:
        return new OpenFeignException("500", buildErrorMessage(methodKey, response));

      case 502:
        return new OpenFeignException("502", buildErrorMessage(methodKey, response));

      case 503:
        return new OpenFeignException("503", buildErrorMessage(methodKey, response));

      case 504:
        return new OpenFeignException("504", buildErrorMessage(methodKey, response));

      default:
        return defaultErrorDecoder.decode(methodKey, response);
    }
  }

  /**
   * 构建错误消息字符串。
   *
   * @param methodKey Feign 方法标识
   * @param response HTTP 响应对象
   * @return 格式化的错误消息
   */
  private String buildErrorMessage(String methodKey, Response response) {
    String reason = StringUtils.hasText(response.reason()) ? response.reason() : "N/A";
    String body = resolveBodyIfEnabled(response);
    String requestLine =
        response.request() == null
            ? "N/A"
            : String.format("%s %s", response.request().httpMethod(), response.request().url());
    String base =
        String.format(
            "Feign 调用失败, method: %s, request: %s, status: %s, reason: %s",
            methodKey, requestLine, response.status(), reason);
    return StringUtils.hasText(body) ? base + ", body: " + body : base;
  }

  /**
   * 根据配置决定是否读取响应体。
   *
   * @param response HTTP 响应对象
   * @return 响应体内容，若不读取或读取失败返回 null
   */
  private String resolveBodyIfEnabled(Response response) {
    if (response.body() == null || feignProperties == null || feignProperties.getError() == null) {
      return null;
    }
    if (!feignProperties.getError().isIncludeBody()) {
      return null;
    }
    int maxBytes = feignProperties.getError().getMaxBodyBytes();
    if (maxBytes <= 0) {
      return null;
    }

    Charset charset = response.charset() == null ? StandardCharsets.UTF_8 : response.charset();
    try (InputStream in = response.body().asInputStream()) {
      BodyReadResult readResult = readUpTo(in, maxBytes);
      if (readResult.bytes.length == 0) {
        return null;
      }
      String body = new String(readResult.bytes, charset);
      if (!StringUtils.hasText(body)) {
        return null;
      }
      return readResult.truncated ? body + "...(已截断)" : body;
    } catch (IOException ex) {
      LOG.debug("读取 Feign 响应体失败", ex);
      return null;
    }
  }

  /**
   * 读取输入流直到达到指定的字节数上限。
   *
   * @param in 输入流
   * @param maxBytes 最大字节数
   * @return 读取结果，包含读取的字节数组和是否被截断的标志
   * @throws IOException 读取过程中发生 I/O 错误
   */
  private BodyReadResult readUpTo(InputStream in, int maxBytes) throws IOException {
    int limit = maxBytes + 1;
    ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(1024, limit));
    byte[] buffer = new byte[1024];
    int remaining = limit;
    while (remaining > 0) {
      int read = in.read(buffer, 0, Math.min(buffer.length, remaining));
      if (read < 0) {
        break;
      }
      out.write(buffer, 0, read);
      remaining -= read;
    }
    byte[] all = out.toByteArray();
    if (all.length <= maxBytes) {
      return new BodyReadResult(all, false);
    }
    byte[] clipped = new byte[maxBytes];
    System.arraycopy(all, 0, clipped, 0, maxBytes);
    return new BodyReadResult(clipped, true);
  }

  /** 响应体读取结果内部类。 */
  private static class BodyReadResult {
    private final byte[] bytes;
    private final boolean truncated;

    BodyReadResult(byte[] bytes, boolean truncated) {
      this.bytes = bytes;
      this.truncated = truncated;
    }
  }
}
