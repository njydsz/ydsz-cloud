package com.njydsz.common.auth.precheck;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限预检注解。
 *
 * <p>标注在方法上，在业务逻辑执行前预先校验用户是否拥有所需权限。
 * 与 {@link com.njydsz.common.auth.annotation.AuthApiPermission} 不同，预检不会直接抛出异常，
 * 而是返回详细的预检结果或注入到方法参数中，由业务方自行决定如何处理。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>前端根据预检结果动态显示/隐藏操作按钮</li>
 *   <li>批量操作前检查用户是否有权限执行</li>
 *   <li>需要根据权限校验结果执行不同业务逻辑的场景</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 方式一：自动注入预检结果到参数
 * &#64;PermissionPreCheck(resultParamName = "checkResult")
 * public void batchDeleteUsers(List<Long> ids, PermissionCheckResult checkResult) {
 *     if (!checkResult.isCheckPassed()) {
 *         throw new BusinessException("您没有批量删除的权限");
 *     }
 *     userService.deleteUsers(ids);
 * }
 *
 * // 方式二：抛出异常模式（与普通注解类似）
 * &#64;PermissionPreCheck(mode = PreCheckMode.THROW, checkType = CHECK_API, apiCodes = {"sys:user:delete"})
 * public void deleteUser(Long id) {
 *     // 业务逻辑
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see PermissionPreChecker
 * @see PermissionCheckResult
 */
@Inherited
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PermissionPreCheck {

    /**
     * 预检模式
     */
    enum PreCheckMode {
        /**
         * 返回模式：将预检结果注入到参数，业务方自行处理
         */
        RETURN,

        /**
         * 抛出异常模式：校验失败直接抛出异常
         */
        THROW
    }

    /**
     * 权限类型
     */
    enum CheckType {
        MENU,
        BUTTON,
        API
    }

    /**
     * 预检模式（AND/OR）
     */
    enum CheckMode {
        ALL,
        ANY
    }

    /**
     * 预检模式。
     *
     * <ul>
     *   <li>{@link PreCheckMode#RETURN}：返回模式（默认）</li>
     *   <li>{@link PreCheckMode#THROW}：抛出异常模式</li>
     * </ul>
     *
     * @return 预检模式
     */
    PreCheckMode mode() default PreCheckMode.RETURN;

    /**
     * 权限类型。
     *
     * @return 权限类型
     */
    CheckType checkType() default CheckType.API;

    /**
     * 需要校验的 API 权限码。
     *
     * @return API 权限码数组
     */
    String[] apiCodes() default {};

    /**
     * 需要校验的菜单权限码。
     *
     * @return 菜单权限码数组
     */
    String[] menuCodes() default {};

    /**
     * 需要校验的按钮权限码。
     *
     * @return 按钮权限码数组
     */
    String[] buttonCodes() default {};

    /**
     * 预检模式：ALL（必须全部满足）或 ANY（满足其一即可）。
     *
     * @return 预检模式
     */
    CheckMode checkMode() default CheckMode.ALL;

    /**
     * 当 mode=RETURN 时，预检结果注入到的参数名称。
     *
     * <p>如果指定的方法参数类型不是 PermissionCheckResult，则不会注入。
     *
     * @return 参数名称
     */
    String resultParamName() default "permissionCheckResult";
}
