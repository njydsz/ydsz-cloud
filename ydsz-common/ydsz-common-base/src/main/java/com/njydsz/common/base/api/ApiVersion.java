package com.njydsz.common.base.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 版本声明注解。
 *
 * <p>标注在 Controller 类或方法上，声明该 API 的版本生命周期元数据。
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>类级别：统一声明整个 Controller 的 API 版本（如 "26.09.01"）
 *   <li>方法级别：覆盖类级别声明，用于版本演进场景（如某接口已升级到 26.12.01）
 *   <li>标记废弃接口：{@code deprecated=true} + {@code replacement} 指明替代接口
 * </ul>
 *
 * <p><b>约定：</b>
 *
 * <ul>
 *   <li>版本号采用项目版本格式 {@code YY.MM.DD}，与版本发布保持一致
 *   <li>默认值为当前项目首版 {@code "26.09.01"}
 *   <li>废弃接口必须声明 {@code replacement} 指明替代方案
 *   <li>{@code since} 记录该接口首次发布的版本，便于生成 API 变更日志
 * </ul>
 *
 * <p><b>示例：</b>
 *
 * <pre>{@code
 * @ApiVersion("26.09.01")
 * @RestController
 * public class FileController {
 *
 *   @ApiVersion(value = "26.12.01", since = "26.12.01", replacement = "/api/files/upload-v2")
 *   @PostMapping("/upload")
 *   public YdszResponse<...> uploadV2() { ... }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiVersion {

  /**
   * API 版本号（项目版本格式 YY.MM.DD）。
   *
   * <p>默认值为当前项目首版 {@code "26.09.01"}。
   *
   * @return 版本号
   */
  String value() default "26.09.01";

  /**
   * 该接口首次引入的版本号（项目版本格式 YY.MM.DD）。
   *
   * <p>用于生成 API 变更日志与兼容性说明。默认为空表示与 value 一致。
   *
   * @return 版本号
   */
  String since() default "";

  /**
   * 是否已废弃。
   *
   * <p>废弃接口仍可使用，但响应中会携带 {@code Deprecation} 头部提示客户端迁移。
   *
   * @return {@code true} 表示已废弃
   */
  boolean deprecated() default false;

  /**
   * 替代接口路径或说明。
   *
   * <p>当 {@code deprecated=true} 时，指明替代接口的 URL 路径或迁移说明。
   *
   * @return 替代方案描述
   */
  String replacement() default "";

  /**
   * 计划移除版本（项目版本格式 YY.MM.DD）。
   *
   * <p>声明该废弃接口将在哪个版本彻底移除，便于客户端制定迁移计划。
   *
   * @return 计划移除的版本号
   */
  String removal() default "";
}
