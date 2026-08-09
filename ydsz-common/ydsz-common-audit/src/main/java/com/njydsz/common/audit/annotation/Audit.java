package com.njydsz.common.audit.annotation;

import java.lang.annotation.*;

import com.njydsz.common.audit.aspect.AuditAspect;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

/**
 * 审计日志方法标记注解
 * <p>
 * 标记在 Controller / Service 方法上，配合 {@link AuditAspect}
 * 完成对方法调用的全链路审计记录。注解本身只声明元数据，真正拦截由 AOP 完成。
 * </p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * @Audit(module = "用户管理",
 *        type = AuditType.OPERATION,
 *        action = AuditAction.CREATE,
 *        content = "'创建用户:' + #user.username",
 *        recordRequest = true,
 *        recordResponse = false,
 *        excludeParams = {"password", "oldPassword"})
 * @PostMapping("/users")
 * public R<User> createUser(@RequestBody UserDTO user) { ... }
 * }</pre>
 *
 * <p><b>性能与安全：</b></p>
 * <ul>
 *   <li>默认开启 {@link #async()} 异步模式，审计落盘不会阻塞业务主链路</li>
 *   <li>敏感参数（密码、令牌、密钥等）必须通过 {@link #excludeParams()} 显式排除，
 *       框架默认敏感词列表见 {@link com.njydsz.common.audit.config.AuditProperties#getSensitiveParams()}</li>
 *   <li>响应结果默认不记录，开启时需评估日志存储与合规风险</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AuditAspect
 * @see AuditType
 * @see AuditAction
 */
@Inherited
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {

    /**
     * 审计模块名称，用于业务域分类与查询过滤。
     * <p>建议与权限模块/菜单模块保持一致，例如：用户管理、订单管理、权限管理等。
     *
     * @return 模块名称
     */
    String module() default "";

    /**
     * 审计类型，区分不同审计域。
     * <p>例如：OPERATION（操作审计）、LOGIN（登录审计）、DATA（数据审计）。
     *
     * @return 审计类型，默认为 {@link AuditType#OPERATION}
     */
    AuditType type() default AuditType.OPERATION;

    /**
     * 操作行为，描述本次审计的行为分类。
     * <p>例如：CREATE、UPDATE、DELETE、QUERY 等。
     *
     * @return 操作行为，默认为 {@link AuditAction#OTHER}
     */
    AuditAction action() default AuditAction.OTHER;

    /**
     * 操作内容描述，支持 SpEL 表达式动态拼接。
     * <p>SpEL 中可访问：方法参数（按参数名）、方法返回值（{@code #result}）、目标对象。
     *
     * <p><b>示例：</b>{@code "'创建了用户[' + #username + ']'"} 或
     * {@code "'删除订单: ' + #orderId + ', 结果: ' + #result.data"}
     *
     * @return 操作内容模板，未配置时审计日志的 content 字段为空
     */
    String content() default "";

    /**
     * 是否记录请求参数。
     * <p>建议对包含敏感字段的接口（密码、证件号等）关闭请求参数记录，
     * 也可以通过 {@link #excludeParams()} 排除部分字段。
     *
     * @return 记录请求参数返回 true
     */
    boolean recordRequest() default true;

    /**
     * 是否记录响应结果。
     * <p>默认关闭，原因是响应体可能较大且包含敏感数据。开启时需评估存储成本与合规风险。
     *
     * @return 记录响应结果返回 true
     */
    boolean recordResponse() default false;

    /**
     * 是否异步记录，异步记录不阻塞主业务流程。
     * <p>推荐开启。关闭后审计落盘会同步执行，业务接口性能将受存储介质影响。
     *
     * @return 异步记录返回 true
     */
    boolean async() default true;

    /**
     * 需要排除的请求参数名称，命中名称的参数不会序列化到审计日志。
     * <p>用于防止敏感参数被记录。常见排除项：password、token、secretKey 等。
     * 注意：框架默认敏感词列表会与该参数合并生效。
     *
     * @return 需要排除的参数名数组
     */
    String[] excludeParams() default {};
}
