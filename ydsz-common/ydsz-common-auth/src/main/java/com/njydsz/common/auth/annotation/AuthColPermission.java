package com.njydsz.common.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.njydsz.common.auth.model.ColumnScopeAware;
import com.njydsz.common.auth.model.ColumnScopeInfo;

/**
 * 列级数据权限校验与过滤注解。
 *
 * <p>用于控制接口返回数据中特定表的字段可见性，或控制写入数据时可操作的字段。 支持 READ（读）、WRITE（写）、READ_WRITE（读写）三种过滤模式。
 *
 * <p><b>工作原理：</b>
 *
 * <ol>
 *   <li>切面拦截标注了本注解的方法
 *   <li>从请求上下文解析当前用户的列权限规则（来自 Redis role-col-key）
 *   <li>将列权限信息注入到方法参数（支持 {@link ColumnScopeAware} 或 Map）
 *   <li>方法执行完成后，对返回值中的对象字段进行过滤（无权限字段置为 null）
 *   <li>同时将列权限规则以 header 形式透传给下游服务（如 SQL 拦截器）
 * </ol>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * // 过滤 sys_user 表的返回字段，仅返回有权限的字段
 * &#64;AuthColPermission(mode = ColumnMode.READ, table = "sys_user")
 * public UserVO getUser(Long id) { ... }
 *
 * // 过滤写入操作，仅允许写入有权限的字段
 * &#64;AuthColPermission(mode = ColumnMode.WRITE, table = "sys_user")
 * public void updateUser(UserDTO dto) { ... }
 *
 * // 同时过滤读写字段
 * &#64;AuthColPermission(mode = ColumnMode.READ_WRITE, table = "sys_user")
 * public UserVO updateUserDetail(UserDTO dto) { ... }
 *
 * // 自定义注入参数名
 * &#64;AuthColPermission(mode = ColumnMode.READ, table = "sys_user", targetParamName = "query")
 * public UserVO getUser(UserQuery query) { ... }
 * </pre>
 *
 * <p><b>数据格式约定：</b>
 *
 * <p>Redis role-col-key 存储的 JSON 格式：
 *
 * <pre>
 * {
 *   "visibleColumns": {
 *     "sys_user": ["id", "name", "phone"],
 *     "sys_role": ["id", "role_name"]
 *   },
 *   "editableColumns": {
 *     "sys_user": ["name", "phone"]
 *   }
 * }
 * </pre>
 *
 * @since 26.09.01
 * @see ColumnScopeInfo
 * @see ColumnScopeAware
 * @author ydsz-team
 */
@Inherited
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthColPermission {

  /**
   * 列权限过滤模式。
   *
   * <ul>
   *   <li>{@link ColumnMode#READ}：仅过滤读操作，返回数据中无权限字段置为 null
   *   <li>{@link ColumnMode#WRITE}：仅过滤写操作，可写入字段受限于权限配置
   *   <li>{@link ColumnMode#READ_WRITE}：同时过滤读写操作
   * </ul>
   *
   * @return 列权限模式
   */
  ColumnMode mode() default ColumnMode.READ_WRITE;

  /**
   * 目标表名。
   *
   * <p>用于精确匹配需要过滤的表。当方法涉及多表操作时， 通过此属性指定需要过滤的表。若为空，则使用默认值。
   *
   * @return 表名（建议小写）
   */
  String table() default "";

  /**
   * 注入到 Map 类型参数时的 key 名称。
   *
   * <p>当方法参数为 Map 类型时，列权限信息会以此 key 存入 Map。 默认值为 {@code columnPermission}。
   *
   * @return Map key 名称
   */
  String mapKey() default "columnPermission";

  /**
   * 目标方法参数名称。
   *
   * <p>用于精确指定需要注入列权限信息的参数名称。 若方法中存在多个可注入参数（实现 {@link ColumnScopeAware} 或 Map）， 通过此属性定位目标参数。
   *
   * @return 方法参数名称
   */
  String targetParamName() default "";

  /**
   * 列权限过滤模式枚举。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  enum ColumnMode {
    /** 读模式：过滤返回数据，无权限字段置为 null。 */
    READ,

    /** 写模式：控制可写入的字段。 */
    WRITE,

    /** 读写模式：同时控制读写权限。 */
    READ_WRITE
  }
}
