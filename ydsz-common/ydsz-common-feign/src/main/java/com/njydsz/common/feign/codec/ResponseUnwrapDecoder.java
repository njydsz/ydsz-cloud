package com.njydsz.common.feign.codec;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.json.JsonMapper;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.tree.ObjectNode;

/**
 * Feign 响应自动解包解码器
 *
 * <p>自动解包 {@link YdszResponse} 类型的响应，直接返回内部 data 字段，简化 Feign 客户端接口定义。
 *
 * <p><b>性能优化（自 26.09.19）：</b>
 *
 * <ul>
 *   <li>目标类型为普通业务类型时，采用 JsonNode 中间路径： 先解析 JSON 为树模型，提取 code/msg 判断业务状态，
 *       再仅对 data 子树调用 {@link JsonMapper#treeToValue} 直转目标类型，避免完整构造 YdszResponse 对象；
 *   <li>目标类型为 {@link YdszResponse} 本身 → 直接全量解析，保证原始字段完整；
 *   <li>目标类型为泛型参数化类型 → 回退至全量解析路径（保留兼容性）。
 * </ul>
 *
 * <p><b>使用场景：</b>
 *
 * <p>当 Feign 客户端接口方法声明返回类型为业务对象（如 {@code User}），而实际服务端返回的是统一包装格式（如
 * {@code Result<User>}）时，此解码器会自动提取 data 字段并反序列化为目标类型。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // Feign 客户端接口
 * public interface UserClient {
 *     // 服务端返回 Result<User>，但接口可直接声明返回 User
 *     @GetMapping("/users/{id}")
 *     User getUser(@PathVariable("id") Long id);
 *
 *     // 如果需要完整响应，仍可使用 YdszResponse<User>
 *     @GetMapping("/users/{id}")
 *     YdszResponse<User> getUserWithWrapper(@PathVariable("id") Long id);
 * }
 * }</pre>
 *
 * <p><b>解包规则：</b>
 *
 * <ul>
 *   <li>目标类型为 {@link YdszResponse} 或其子类 → 不解包，返回完整响应
 *   <li>目标类型为普通业务类型 → 先解析为 JsonNode，提取 data 子树再直转
 *   <li>响应 code 不等于成功码 → 抛出 {@link FeignBusinessException}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see JsonDecoder
 */
public class ResponseUnwrapDecoder implements Decoder {

  private static final Logger LOG = LoggerFactory.getLogger(ResponseUnwrapDecoder.class);

  /** 委托的实际解码器（用于 YdszResponse 完整解析及泛型回退路径） */
  private final Decoder delegate;

  /** 用于 treeToValue 直转的 JsonMapper 实例 */
  private final JsonMapper jsonMapper;

  /**
   * 使用指定的委托解码器构造。
   *
   * @param delegate 委托解码器，用于实际的反序列化
   */
  public ResponseUnwrapDecoder(Decoder delegate) {
    this(delegate, YdszJson.getDefaultMapper());
  }

  /**
   * 使用指定的委托解码器和 JsonMapper 构造。
   *
   * @param delegate 委托解码器
   * @param jsonMapper 用于 treeToValue 的 Mapper
   */
  public ResponseUnwrapDecoder(Decoder delegate, JsonMapper jsonMapper) {
    this.delegate = delegate;
    this.jsonMapper = jsonMapper;
  }

  /** 使用默认的 {@link JsonDecoder} 构造。 */
  public ResponseUnwrapDecoder() {
    this(new JsonDecoder());
  }

  @Override
  public Object decode(Response response, Type type) throws IOException, DecodeException {
    // 1. 判断目标类型是否为 YdszResponse 或其子类
    if (isResponseType(type)) {
      // 目标类型是 YdszResponse，不解包，直接解码
      LOG.debug("目标类型为 YdszResponse，不解包: {}", type);
      return delegate.decode(response, type);
    }

    // 2. 目标类型为参数化泛型（非 Class），回退到全量解析路径（兼容性）
    if (type instanceof ParameterizedType) {
      return decodeWithFullWrapper(response, type);
    }

    // 3. 目标类型为普通业务类型 → 走 JsonNode 中间路径（性能优化）
    return decodeWithJsonTree(response, type);
  }

  /**
   * 通过 JsonNode 中间路径解码（性能优化路径）。
   *
   * <p>先解析 JSON 为树模型，检查业务状态码，提取 data 子树后直转目标类型， 避免完整构造 YdszResponse 对象。
   *
   * @param response Feign 响应
   * @param type 目标业务类型
   * @return 反序列化后的业务对象
   * @throws IOException IO 异常
   * @throws DecodeException 解码异常
   */
  private Object decodeWithJsonTree(Response response, Type type) throws IOException, DecodeException {
    String body = readResponseBody(response);
    if (body == null || body.isBlank()) {
      return null;
    }

    // 解析为 JSON 树
    JsonNode rootNode = YdszJson.readTree(body);
    if (!(rootNode instanceof ObjectNode objectNode)) {
      // 非对象节点，无法解包，直接返回原始解码结果
      LOG.warn("响应根节点非 ObjectNode，回退全量解析: {}", rootNode.getClass().getSimpleName());
      return decodeWithFullWrapper(response, type);
    }

    // 检查业务状态码
    JsonNode codeNode = objectNode.get("code");
    if (codeNode != null && !codeNode.isNull() && !isSuccessCode(codeNode)) {
      String code = codeNode.asText();
      JsonNode msgNode = objectNode.get("msg");
      String msg = (msgNode != null && !msgNode.isNull()) ? msgNode.asText() : "";
      LOG.warn("Feign 响应业务失败, code: {}, msg: {}", code, msg);
      throw new FeignBusinessException(code, msg, response.request().url(), response.status());
    }

    // 提取 data 子树
    JsonNode dataNode = objectNode.get("data");
    if (dataNode == null || dataNode.isNull()) {
      return null;
    }

    // 目标类型为 Class → 走 treeToValue 直转（避免字符串中转）
    if (type instanceof Class<?> targetClass) {
      LOG.debug("JsonTree 路径解包: targetClass={}, dataType={}", targetClass.getSimpleName(), dataNode.getClass().getSimpleName());
      return jsonMapper.treeToValue(dataNode, targetClass);
    }

    // 非 Class 回退（理论上不会到这里，因为前面已处理 ParameterizedType）
    String dataJson = dataNode.toString();
    return YdszJson.fromJson(dataJson, type);
  }

  /**
   * 通过完整包装类型解码（兼容路径）。
   *
   * <p>先反序列化为 YdszResponse&lt;T&gt;，然后提取 data。适用于目标类型为参数化泛型（如 List&lt;User&gt;）的场景。
   *
   * @param response Feign 响应
   * @param type 目标业务类型（参数化泛型）
   * @return 反序列化后的业务对象
   * @throws IOException IO 异常
   * @throws DecodeException 解码异常
   */
  private Object decodeWithFullWrapper(Response response, Type type) throws IOException, DecodeException {
    Type wrapperType = buildWrapperType(type);
    Object decoded = delegate.decode(response, wrapperType);

    if (decoded == null) {
      return null;
    }

    if (decoded instanceof YdszResponse<?> wrapper) {
      if (!wrapper.isSuccess()) {
        String code = wrapper.getCode();
        String msg = wrapper.getMsg();
        LOG.warn("Feign 响应业务失败（全量路径）, code: {}, msg: {}", code, msg);
        throw new FeignBusinessException(code, msg, response.request().url(), response.status());
      }
      Object data = wrapper.getData();
      LOG.debug("全量路径解包成功, 目标类型: {}, data 类型: {}", type, data != null ? data.getClass().getSimpleName() : "null");
      return data;
    }

    // 解码结果不是 YdszResponse，直接返回
    LOG.debug("解码结果非 YdszResponse，直接返回: {}", decoded.getClass().getSimpleName());
    return decoded;
  }

  /**
   * 读取响应体为字符串。
   *
   * @param response Feign 响应
   * @return 响应体字符串
   * @throws IOException IO 异常
   */
  private String readResponseBody(Response response) throws IOException {
    if (response.body() == null) {
      return null;
    }
    // Feign Response.body() 是 Response.Body，使用 asReader 读取
    try (java.io.Reader reader = response.body().asReader(java.nio.charset.StandardCharsets.UTF_8)) {
      StringBuilder sb = new StringBuilder(1024);
      char[] buffer = new char[1024];
      int n;
      while ((n = reader.read(buffer)) != -1) {
        sb.append(buffer, 0, n);
      }
      return sb.toString();
    }
  }

  /**
   * 判断目标类型是否为 YdszResponse 或其子类。
   *
   * @param type 目标类型
   * @return true 表示是 YdszResponse 类型
   */
  private boolean isResponseType(Type type) {
    if (type instanceof Class<?> clazz) {
      return YdszResponse.class.isAssignableFrom(clazz);
    }
    if (type instanceof ParameterizedType pType) {
      Type rawType = pType.getRawType();
      if (rawType instanceof Class<?> clazz) {
        return YdszResponse.class.isAssignableFrom(clazz);
      }
    }
    return false;
  }

  /**
   * 判断响应码是否表示成功。
   *
   * @param codeNode code 字段节点
   * @return true 表示成功
   */
  private boolean isSuccessCode(JsonNode codeNode) {
    // 兼容 code 为字符串 "0" / "200" 或数字 0 / 200
    String codeStr = codeNode.asText();
    if (codeStr == null || codeStr.isEmpty()) {
      return true;
    }
    // "0" 或 "200" 表示成功
    return "0".equals(codeStr) || "200".equals(codeStr) || "success".equalsIgnoreCase(codeStr);
  }

  /**
   * 构建包装类型 YdszResponse&lt;T&gt;。
   *
   * @param innerType 内部数据类型
   * @return YdszResponse&lt;innerType&gt; 类型
   */
  private Type buildWrapperType(Type innerType) {
    return new ParameterizedType() {
      @Override
      public Type[] getActualTypeArguments() {
        return new Type[] {innerType};
      }

      @Override
      public Type getRawType() {
        return YdszResponse.class;
      }

      @Override
      public Type getOwnerType() {
        return null;
      }
    };
  }

  /**
   * Feign 业务异常。
   *
   * <p>当 Feign 调用返回的业务状态码非成功时抛出。携带原始 HTTP 状态码，便于调用方区分网络错误与业务错误。
   */
  public static class FeignBusinessException extends DecodeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final String msg;
    private final String url;

    /**
     * 构造 Feign 业务异常。
     *
     * @param code 业务错误码
     * @param msg 错误消息
     * @param url 请求 URL
     * @param httpCode 原始 HTTP 状态码（如 200、403 等）
     */
    public FeignBusinessException(String code, String msg, String url, int httpCode) {
      super(
          httpCode,
          String.format(
              "Feign 业务失败, url: %s, httpCode: %d, code: %s, msg: %s", url, httpCode, code, msg),
          null);
      this.code = code;
      this.msg = msg;
      this.url = url;
    }

    public String getCode() {
      return code;
    }

    public String getMsg() {
      return msg;
    }

    public String getUrl() {
      return url;
    }
  }
}
