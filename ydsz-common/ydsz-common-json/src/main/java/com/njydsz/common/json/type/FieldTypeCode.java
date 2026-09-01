package com.njydsz.common.json.type;

/**
 * 统一的字段类型码枚举（P1-A4 类型码统一方案）。
 *
 * <p>取代本模块中多套不兼容的 int 类型码（ValueWriter.TYPE_CODE_* / FieldMeta.computeSerializeTypeCode /
 * JsonTypeCode / ObjectReader.getTypeCode / BeanReader.getTypeCode），后续版本将所有组件逐步迁移至此枚举。
 *
 * <p>当前编码表以 {@link com.njydsz.common.json.provider.ValueWriter} 的 TYPE_CODE_* 为准， 各组件通过 {@link
 * #fromLegacy(int, String)} 按来源兼容转换。
 *
 * @since 1.0.0
 */
/**
 * FieldTypeCode。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum FieldTypeCode {
/** string */
  STRING(1),
/** int */
  INT(2),
/** long */
  LONG(3),
/** double */
  DOUBLE(4),
/** float */
  FLOAT(5),
/** boolean */
  BOOLEAN(6),
/** char */
  CHAR(7),
/** short */
  SHORT(8),
/** byte */
  BYTE(9),
/** local date time */
  LOCAL_DATE_TIME(10),
/** local date */
  LOCAL_DATE(11),
/** date */
  DATE(12),
/** collection */
  COLLECTION(13),
/** map */
  MAP(14),
/** nested object */
  NESTED_OBJECT(15),
/** big decimal */
  BIG_DECIMAL(16),
/** big integer */
  BIG_INTEGER(17),
/** uuid */
  UUID(18);

  private final int code;

  FieldTypeCode(int code) {
    this.code = code;
  }

  /**
   * 返回该字段类型对应的整型类型码。
   *
   * <p>类型码用于序列化/反序列化路径上以 int 快速分支（取代反射或 instanceof 链）， 编码表见类级文档。该值与旧系统类型码不完全兼容，跨系统转换请走 {@link
   * #fromLegacy(int, String)}。
   *
   * @return 整型类型码（编码含义见枚举声明顺序）
   */
  public int code() {
    return code;
  }

  /**
   * 按当前编码方案获取枚举（直接匹配）。
   *
   * @param code 待匹配的类型码
   * @return 对应的枚举常量，不会为 {@code null}；无任何枚举匹配时降级返回 {@link #NESTED_OBJECT}
   */
  public static FieldTypeCode of(int code) {
    for (FieldTypeCode tc : values()) {
      if (tc.code == code) {
        return tc;
      }
    }
    return NESTED_OBJECT;
  }

  /**
   * 从各旧系统的 int 类型码按来源转换。
   * @param code 旧系统的 int 类型码
   * @param source 来源标识（"FieldMeta"）
   *
   * @return 对应的枚举常量，不会为 {@code null}；当前各来源编码表已与内部编码统一，
   *     实际等价于 {@link #of(int)}，未识别时降级返回 {@link #NESTED_OBJECT}
   */
  public static FieldTypeCode fromLegacy(int code, String source) {
    return of(code);
  }
}
