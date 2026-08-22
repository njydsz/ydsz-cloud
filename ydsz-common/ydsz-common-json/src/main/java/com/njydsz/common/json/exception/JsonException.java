package com.njydsz.common.json.exception;

/**
 * YdszJson 异常基类（参考 Jackson 的 JsonProcessingException）。
 *
 * <p>所有 YdszJson 相关异常的基类，携带错误码、源位置信息和可选的 JSON 字段路径， 便于快速定位序列化/反序列化失败的具体字段和原因。
 *
 * <p><b>字段路径格式（对标 Jackson {@code JsonProcessingException.getPath()}）：</b>
 *
 * <ul>
 *   <li>{@code user.address.street} — 嵌套对象字段
 *   <li>{@code items[2].name} — 数组元素字段
 *   <li>{@code null} — 未设置路径
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class JsonException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** 错误码 */
  private final int errorCode;

  /** JSON 字符串位置 */
  private final int position;

  /** 异常分类，用于程序化区分错误类型（可 null） */
  private final CauseType causeType;

  /**
   * 错误发生时的 JSON 字段路径，对标 Jackson 的 reference chain。
   *
   * <p>例如 {@code "user.addresses[0].zipCode"}，用于快速定位错误字段。 为 {@code null} 表示未设置。
   *
   * @since 1.0.0
   */
  private volatile String fieldPath;

  /** 错误类型枚举，对标 Jackson 的 JsonProcessingException 分类 */
  public enum CauseType {
    /** JSON 语法错误（非法格式） */
    PARSE_ERROR,
    /** 类型不匹配（如期望 int 但得到 string） */
    TYPE_MISMATCH,
    /** 必填字段缺失 */
    MISSING_FIELD,
    /** 循环引用 */
    CIRCULAR_REF,
    /** 字段访问失败（getter/setter 异常） */
    FIELD_ACCESS,
    /** 未知错误 */
    UNKNOWN
  }

  /**
   * 构造函数（仅消息）
   *
   * @param message 错误消息
   */
  public JsonException(String message) {
    super(message);
    this.errorCode = 0;
    this.position = -1;
    this.causeType = CauseType.UNKNOWN;
  }

  /**
   * 构造函数（消息和原因）
   *
   * @param message 错误消息
   * @param cause 原始异常
   */
  public JsonException(String message, Throwable cause) {
    super(message, cause);
    this.errorCode = 0;
    this.position = -1;
    this.causeType = CauseType.UNKNOWN;
  }

  /**
   * 构造函数（错误码和消息）
   *
   * @param errorCode 错误码
   * @param message 错误消息
   */
  public JsonException(int errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
    this.position = -1;
    this.causeType = CauseType.UNKNOWN;
  }

  /**
   * 构造函数（错误码、消息和位置）
   *
   * @param errorCode 错误码
   * @param message 错误消息
   * @param position JSON 字符串中的位置
   */
  public JsonException(int errorCode, String message, int position) {
    super(message + " at position " + position);
    this.errorCode = errorCode;
    this.position = position;
    this.causeType = CauseType.UNKNOWN;
  }

  /**
   * 构造函数（错误码、消息和原因）
   *
   * @param errorCode 错误码
   * @param message 错误消息
   * @param cause 原始异常
   */
  public JsonException(int errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
    this.position = -1;
    this.causeType = CauseType.UNKNOWN;
  }

  /**
   * 全参数构造函数。
   *
   * @param errorCode 错误码
   * @param message 错误消息
   * @param cause 原始异常
   * @param position JSON 字符串中的位置
   * @param causeType 异常分类
   * @since 1.0.0
   */
  public JsonException(
      int errorCode, String message, Throwable cause, int position, CauseType causeType) {
    super(message, cause);
    this.errorCode = errorCode;
    this.position = position;
    this.causeType = causeType != null ? causeType : CauseType.UNKNOWN;
  }

  /**
   * 获取错误码
   *
   * @return 错误码，未设置时返回 0
   */
  public int getErrorCode() {
    return errorCode;
  }

  /**
   * 获取 JSON 字符串位置。
   *
   * @return 字符位置，未设置时返回 -1
   */
  public int getPosition() {
    return position;
  }

  /**
   * 获取异常分类。
   *
   * @return 异常分类枚举，不会为 null
   * @since 1.0.0
   */
  public CauseType getCauseType() {
    return causeType;
  }

  /**
   * 获取错误发生时的字段路径。
   *
   * @return 字段路径（如 {@code "user.address.street"}），未设置时返回 null
   * @since 1.0.0
   */
  public String getFieldPath() {
    return fieldPath;
  }

  /**
   * 设置错误发生时的字段路径，返回 this 支持链式调用。
   *
   * <p>示例：
   *
   * <pre>
   * throw new JsonSerializationException("...").setFieldPath(getCurrentFieldPath());
   * </pre>
   *
   * @param fieldPath 字段路径（如 {@code "user.addresses[0]"}）
   * @return this
   * @since 1.0.0
   */
  public JsonException setFieldPath(String fieldPath) {
    this.fieldPath = fieldPath;
    return this;
  }

  /**
   * 获取位置描述字符串，格式对标 Jackson。
   *
   * <p>返回格式：
   *
   * <ul>
   *   <li>{@code "(position: 42)"} — 仅知道字符位置
   *   <li>{@code "(line 3, column 15)"} — 行号和列号可用时（子类实现）
   *   <li>{@code ""} — 位置未知
   * </ul>
   *
   * @return 位置描述字符串，永不为 null
   * @since 1.0.0
   */
  public String getLocationDescription() {
    if (position < 0) {
      return "";
    }
    return "(position: " + position + ")";
  }

  /**
   * 重写 getMessage()，自动附加字段路径信息。
   *
   * <p>对标 Jackson 的 {@code JsonProcessingException.getMessage()} 行为： 在原始消息后附加 <code> [field: xxx]
   * </code> 结构化后缀， 便于日志聚合和根因定位。
   *
   * @return 完整的错误消息（含字段路径后缀）
   */
  @Override
  public String getMessage() {
    String base = super.getMessage();
    if (fieldPath != null && !fieldPath.isEmpty()) {
      if (base == null) {
        return " [field: " + fieldPath + "]";
      }
      return base + " [field: " + fieldPath + "]";
    }
    return base;
  }
}
