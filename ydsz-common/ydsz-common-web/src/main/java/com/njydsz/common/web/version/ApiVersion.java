package com.njydsz.common.web.version;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 版本注解
 *
 * <p>标注在 Controller 类或方法上，指定该接口支持的 API 版本。支持基于 URL 路径（/v1/、/v2/）或 Accept 头的版本路由。
 *
 * <p>版本号采用项目版本格式 {@code YY.MM.DD}，默认值 {@code "26.09.01"} 为当前项目首版。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/users")
 * public class UserController {
 *
 *     @GetMapping
 *     @ApiVersion("26.09.01")
 *     public Result<UserVO> getUser() { ... }
 *
 *     @GetMapping
 *     @ApiVersion("26.12.01")
 *     public Result<UserV2VO> getUserV2() { ... }
 * }
 * }</pre>
 *
 * <p><b>版本路由策略：</b>
 *
 * <ul>
 *   <li>URL 路径模式：{@code /260901/api/users} → 匹配 @ApiVersion("26.09.01")
 *   <li>Accept 头模式：{@code Accept: application/vnd.ydsz.v260901+json} → 匹配 @ApiVersion("26.09.01")
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

  /**
   * API 版本号（项目版本格式 YY.MM.DD）。
   *
   * <p>默认值为当前项目首版 {@code "26.09.01"}。
   *
   * @return 版本号字符串
   */
  String value() default "26.09.01";
}
