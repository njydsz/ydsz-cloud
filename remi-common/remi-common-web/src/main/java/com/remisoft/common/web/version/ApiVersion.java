package com.remisoft.common.web.version;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 版本注解
 *
 * <p>标注在 Controller 类或方法上，指定该接口支持的 API 版本。
 * 支持基于 URL 路径（/v1/、/v2/）或 Accept 头的版本路由。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/users")
 * public class UserController {
 *
 *     @GetMapping
 *     @ApiVersion("1.0")
 *     public Result<UserVO> getUserV1() {
 *         // v1.0 实现
 *     }
 *
 *     @GetMapping
 *     @ApiVersion("2.0")
 *     public Result<UserVO> getUserV2() {
 *         // v2.0 实现
 *     }
 * }
 * }</pre>
 *
 * <p><b>版本路由策略：</b>
 * <ul>
 *   <li>URL 路径模式：{@code /v1/api/users} → 匹配 @ApiVersion("1.0")</li>
 *   <li>Accept 头模式：{@code Accept: application/vnd.remi.v1+json} → 匹配 @ApiVersion("1.0")</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

    /**
     * API 版本号
     *
     * <p>支持格式：
     * <ul>
     *   <li>主版本：{@code "1"}、{@code "2"}</li>
     *   <li>次版本：{@code "1.0"}、{@code "2.1"}</li>
     * </ul>
     *
     * @return 版本号字符串
     */
    String value();
}
