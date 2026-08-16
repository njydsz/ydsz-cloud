package com.njydsz.common.util.http;

import com.njydsz.common.json.YdszJson;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * HTTP 响应渲染工具类
 *
 * <p>封装向 Servlet Response 写入内容的标准化逻辑， 统一处理状态码、Content-Type 和异常降级。
 *
 * <h2>渲染能力</h2>
 *
 * <ul>
 *   <li>字符串渲染：直接写入文本内容
 *   <li>对象渲染：自动序列化为 JSON 字符串后写入
 * </ul>
 *
 * @author ydsz-team
 * @since 2.0.0
 */
@Slf4j
public final class HttpResponseUtils {

  /** 默认响应状态码 */
  private static final int DEFAULT_STATUS = HttpStatus.OK.value();

  /** 默认 Content-Type */
  private static final String DEFAULT_CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE;

  /** 默认字符编码 */
  private static final String DEFAULT_CHARSET = StandardCharsets.UTF_8.name();

  private HttpResponseUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 将字符串渲染到客户端。
   *
   * <p>统一设置状态码 200、Content-Type 为 application/json;charset=UTF-8， 然后写入 body 内容。
   *
   * @param response HTTP 响应
   * @param content 要渲染的字符串
   */
  public static void renderString(HttpServletResponse response, String content) {
    render(response, content, DEFAULT_STATUS, DEFAULT_CONTENT_TYPE, DEFAULT_CHARSET);
  }

  /**
   * 将对象序列化为 JSON 后渲染到客户端。
   *
   * <p>内部使用 {@link YdszJson#toJson(Object)} 序列化，再调用 {@link #renderString} 渲染。 若 object 为
   * null，不进行任何操作（避免写入 null 字符串）。
   *
   * @param response HTTP 响应
   * @param object 要序列化并渲染的对象
   */
  public static void renderObject(HttpServletResponse response, Object object) {
    if (object == null) {
      return;
    }
    renderString(response, YdszJson.toJson(object));
  }

  /**
   * 指定状态码渲染 JSON 对象到客户端。
   *
   * <p>用于需要自定义 HTTP 状态码的场景（如 201 Created、204 No Content）。
   *
   * @param response HTTP 响应
   * @param object 要渲染的对象
   * @param httpStatus HTTP 状态码枚举
   */
  public static void renderJson(
      HttpServletResponse response, Object object, HttpStatus httpStatus) {
    if (object == null) {
      // 空 body 渲染
      render(response, "", httpStatus.value(), DEFAULT_CONTENT_TYPE, DEFAULT_CHARSET);
      return;
    }
    render(
        response,
        YdszJson.toJson(object),
        httpStatus.value(),
        DEFAULT_CONTENT_TYPE,
        DEFAULT_CHARSET);
  }

  /**
   * 写入错误响应。
   *
   * <p>以 JSON 格式返回错误信息，状态码由调用方指定。
   *
   * <pre>{@code
   * HttpResponseUtils.renderError(response, HttpStatus.BAD_REQUEST, "参数校验失败");
   * }</pre>
   *
   * @param response HTTP 响应
   * @param httpStatus HTTP 状态码
   * @param message 错误消息
   */
  public static void renderError(
      HttpServletResponse response, HttpStatus httpStatus, String message) {
    // 使用统一 JSON 序列化器，避免手工拼接导致的特殊字符（引号/反斜杠/换行）破坏 JSON 结构或注入额外字段
    String body = YdszJson.toJson(java.util.Map.of("error", message == null ? "" : message));
    render(response, body, httpStatus.value(), DEFAULT_CONTENT_TYPE, DEFAULT_CHARSET);
  }

  /**
   * 底层渲染方法（统一处理响应头设置与写入）。
   *
   * @param response response
   * @param content 内容
   * @param status status
   * @param contentType contentType
   * @param charset charset
   */
  private static void render(
      HttpServletResponse response,
      String content,
      int status,
      String contentType,
      String charset) {
    if (response == null) {
      return;
    }
    try {
      response.setStatus(status);
      response.setContentType(contentType);
      response.setCharacterEncoding(charset);
      response.getWriter().print(content);
    } catch (IOException e) {
      log.error("HttpResponseUtils -> render error: {}", e.getMessage());
    }
  }
}
