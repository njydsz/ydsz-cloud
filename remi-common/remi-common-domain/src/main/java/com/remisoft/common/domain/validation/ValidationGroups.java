package com.remisoft.common.domain.validation;

/**
 * 校验分组标记接口（Jakarta Validation Groups）。
 *
 * <p>用于对同一 DTO/Command 在不同操作场景下应用不同的校验规则。
 * 配合 Spring 的 {@code @Validated} 注解使用，避免"创建时可为空、更新时必填"等场景
 * 需要分裂多个 DTO 的问题。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class UserCommand {
 *     @Null(groups = Create.class, message = "创建时 ID 必须为空")
 *     @NotNull(groups = Update.class, message = "更新时 ID 不能为空")
 *     private Long id;
 *
 *     @NotBlank(groups = {Create.class, Update.class})
 *     private String name;
 *
 *     @NotBlank(groups = Create.class, message = "创建时密码必填")
 *     private String password;
 * }
 *
 * // Controller 层指定分组
 * @PostMapping
 * public BaseResponse&lt;Void&gt; create(@Validated(ValidationGroups.Create.class) @RequestBody UserCommand cmd) { ... }
 *
 * @PutMapping("/{id}")
 * public BaseResponse&lt;Void&gt; update(@Validated(ValidationGroups.Update.class) @RequestBody UserCommand cmd) { ... }
 * }</pre>
 *
 * <p>设计参考：
 * <ul>
 *   <li>Jakarta Validation 3.1 规范（Bean Validation 3.1 / JSR 380）的 Groups 机制</li>
 *   <li>Spring {@code @Validated} 注解的 {@code value} 参数</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.6.0
 */
public final class ValidationGroups {

    private ValidationGroups() {
        // 工具类，禁止实例化
    }

    /**
     * 新增操作校验分组。
     *
     * <p>约束：ID 为空、必填字段强制校验（如密码、初始状态）。
     */
    public interface Create {
    }

    /**
     * 更新操作校验分组。
     *
     * <p>约束：ID 非空、部分字段可选（如密码不修改时不传）。
     */
    public interface Update {
    }

    /**
     * 分页查询操作校验分组。
     *
     * <p>约束：页码/页大小范围校验、排序字段白名单校验。
     */
    public interface PageQuery {
    }

    /**
     * 导出操作校验分组。
     *
     * <p>约束：导出格式校验、最大导出条数限制。
     */
    public interface Export {
    }

    /**
     * 删除操作校验分组。
     *
     * <p>约束：ID 非空、级联删除校验、删除前业务规则检查。
     */
    public interface Delete {
    }
}
