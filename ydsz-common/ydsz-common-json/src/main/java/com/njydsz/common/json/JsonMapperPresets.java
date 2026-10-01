package com.njydsz.common.json;

import com.njydsz.common.json.naming.PropertyNamingStrategy;

/**
 * 常用 JsonMapper 预设工厂（P2-F）。
 *
 * <p>当前业务代码均使用 {@link YdszJson#getDefaultMapper()} 默认配置（LOWER_CAMEL_CASE）， 当遇到以下场景时，
 * 可通过本工厂快速获取预配置的独立 Mapper 实例：
 *
 * <ul>
 *   <li><b>SNAKE_CASE</b>：对接外部 API（银行、第三方 SaaS）要求蛇形命名的 JSON 载荷
 *   <li><b>BIG_DECIMAL</b>：金融/账单场景要求浮点数使用 BigDecimal 精确序列化（禁用 double 默认行为）
 *   <li><b>PrettyFormat</b>：调试日志、OpenAPI 文档示例等人类可读场景
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 对接要求蛇形命名的外部银行 API
 * JsonMapper mapper = JsonMapperPresets.snakeCase();
 * String payload = mapper.toJson(bankTransferDTO);
 *
 * // 金融金额精确序列化
 * JsonMapper decimalMapper = JsonMapperPresets.bigDecimal();
 * String json = decimalMapper.toJson(invoiceVO);
 * }</pre>
 *
 * <p>注意：返回的 Mapper 实例非线程安全的 {@link YdszJson#getDefaultMapper()}，
 * 需避免在多线程间共享 single instance，建议以 {@code ThreadLocal} 持有或每次 {@code build()} 新实例。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public final class JsonMapperPresets {

  private JsonMapperPresets() {}

  /**
   * 蛇形命名预设（外部 API 对接场景）。
   *
   * <p>将 Java 字段的 lowerCamelCase 转换为 snake_case 输出（如 {@code userName} → {@code user_name}）。 同时开启
   * {@code writeNulls=true} 保证字段不丢失（外部 API 通常要求完整 schema）。
   *
   * @return 蛇形命名 Mapper 实例
   */
  public static JsonMapper snakeCase() {
    return JsonMapper.builder()
        .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
        .writeNulls(true)
        .build();
  }

  /**
   * BigDecimal 精确序列化预设（金融/账单场景）。
   *
   * <p>启用 {@code useBigDecimal=true} 后，浮点数字段通过 {@link java.math.BigDecimal#toPlainString()} 输出，
   * 避免 IEEE 754 精度丢失；同时开启 {@code writeNulls=false} 减少无用字段。
   *
   * @return BigDecimal 精确模式 Mapper 实例
   */
  public static JsonMapper bigDecimal() {
    return JsonMapper.builder()
        .useBigDecimal(true)
        .writeNulls(false)
        .build();
  }

  /**
   * Pretty-print 预设（调试/文档场景）。
   *
   * <p>输出带缩进和换行的可读 JSON，适用于：
   *
   * <ul>
   *   <li>日志中输出请求/响应体（仅 DEBUG 级别生产环境关闭）
   *   <li>OpenAPI / Swagger 文档中的 example 字段
   *   <li>配置文件导出
   * </ul>
   *
   * @return Pretty-print Mapper 实例
   */
  public static JsonMapper prettyPrint() {
    return JsonMapper.builder()
        .prettyPrint(true)
        .writeNulls(true)
        .build();
  }
}
