package com.njydsz.common.base.advice;

import java.nio.ByteBuffer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.json.YdszJson;

/**
 * 全局响应包装抽象基类。
 *
 * <p>封装三套 Advice（Base/App/Web）的公共响应包装逻辑：
 *
 * <ul>
 *   <li>统一返回体结构 {@link YdszResponse#success(Object)}
 *   <li>跳过已包装类型（{@link YdszResponse}、void、ResponseEntity/HttpEntity、Resource）
 *   <li>跳过流式响应（SSE / StreamingResponseBody / byte[] / ByteBuffer）
 *   <li>String 类型返回值的差异化包装由子类实现 {@link #wrapStringBody(String)}
 * </ul>
 *
 * <p>子类需完成两件事：
 *
 * <ol>
 *   <li>通过 {@code @RestControllerAdvice} 注解与条件过滤确认作用范围
 *   <li>实现 {@link #wrapStringBody(String)} 提供端侧差异化的 String 包装语义
 * </ol>
 *
 * <p><b>跳过包装的类型：</b>
 *
 * <ul>
 *   <li>{@link YdszResponse} — 已是标准响应
 *   <li>{@code void} — 无返回值（如文件下载、204 No Content）
 *   <li>{@link ResponseEntity} — Spring MVC 特殊处理，包装会丢失原始状态码和 Header
 *   <li>{@link HttpEntity} — 同 ResponseEntity
 *   <li>{@link Resource} — 文件下载场景
 * </ul>
 *
 * <p>符合 YDIZ-ARCH-007（ResponseAdvice 抽象收敛，v1.0.12）。
 *
 * @author ydsz-team
 * @since 26.09.29
 */
public abstract class AbstractGlobalResponseAdvice implements ResponseBodyAdvice<Object> {

  @Override
  public boolean supports(
      @NonNull MethodParameter returnType,
      @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
    Class<?> paramType = returnType.getParameterType();
    // 跳过已包装类型、void、ResponseEntity/HttpEntity、Resource
    if (paramType == YdszResponse.class
        || paramType == void.class
        || paramType == Void.class
        || ResponseEntity.class.isAssignableFrom(paramType)
        || HttpEntity.class.isAssignableFrom(paramType)
        || Resource.class.isAssignableFrom(paramType)) {
      return false;
    }
    // 跳过流式响应类型：SSE / StreamingResponseBody / byte[] / ByteBuffer
    // 这些返回值由 Spring MVC 直接写回，包装会破坏流式协议（如 AI 对话流式输出）
    if (isStreamingType(paramType)) {
      return false;
    }
    return true;
  }

  /**
   * 判断返回类型是否为流式响应类型。
   *
   * <p>流式响应必须保持原始字节流/事件流输出，全局响应包装会导致：
   *
   * <ul>
   *   <li>SSE（text/event-stream）被包装为 JSON，破坏事件流协议
   *   <li>{@link StreamingResponseBody} 被序列化为错误内容
   *   <li>文件下载（byte[]）被包装成 JSON 字符串
   * </ul>
   *
   * @param paramType 返回类型
   * @return true-为流式类型，需跳过包装
   */
  private static boolean isStreamingType(Class<?> paramType) {
    if (paramType == byte[].class || paramType == ByteBuffer.class) {
      return true;
    }
    if (StreamingResponseBody.class.isAssignableFrom(paramType)) {
      return true;
    }
    return SseEmitter.class.isAssignableFrom(paramType);
  }

  @Override
  public @Nullable Object beforeBodyWrite(
      @Nullable Object body,
      @NonNull MethodParameter returnType,
      @NonNull MediaType selectedContentType,
      @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
      @NonNull ServerHttpRequest request,
      @NonNull ServerHttpResponse response) {
    if (body instanceof YdszResponse) {
      return body;
    }
    if (body instanceof String) {
      // 仅当 String 返回值仍为默认 text/plain 时修正为 application/json；
      // 若 Controller 已显式指定（如 text/csv、text/html），保留原 Content-Type
      MediaType contentType = selectedContentType;
      if (contentType == null || MediaType.TEXT_PLAIN.isCompatibleWith(contentType)) {
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
      }
      YdszResponse<String> result = wrapStringBody((String) body);
      try {
        return YdszJson.toJson(result);
      } catch (Exception e) {
        return result;
      }
    }
    if (body == null) {
      return YdszResponse.success();
    }
    return YdszResponse.success(body);
  }

  /**
   * 子类覆盖此方法处理 String 类型返回值的包装差异。
   *
   * <p>不同客户端对 String 响应的语义理解不同：
   *
   * <ul>
   *   <li>PC Web / 通用 Web — 将原始字符串视作成功消息体
   *   <li>App — 将原始字符串作为业务消息字段（msg）
   * </ul>
   *
   * @param body 原始 String 返回值
   * @return 包装后的 YdszResponse
   */
  protected abstract YdszResponse<String> wrapStringBody(String body);
}
