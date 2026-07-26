package com.njydsz.literule.server.expr;

import java.util.Collection;
import java.util.List;

/**
 * 变量空间元数据注册表
 *
 * <p>提供规则表达式中可引用的变量定义，用于：
 * <ul>
 *   <li>触发 {@link ExpressionValidationService} 的 UNDEFINED_VARIABLE 校验</li>
 *   <li>前端表达式编辑器的自动补全提示</li>
 *   <li>dryRun 时填充默认 facts（基于 sampleValue）</li>
 * </ul>
 *
 * <p>实现方式：
 * <ul>
 *   <li>默认实现 {@link EmptyVariableRegistry}：空实现，向后兼容（不启用 UNDEFINED_VARIABLE 校验）</li>
 *   <li>数据库实现（project 模块）：从 {@code ydsz_rule_variable_def} 表加载</li>
 *   <li>编程式实现：通过 {@link #register(VariableDefinition)} 动态注册</li>
 * </ul>
 *
 * <p>线程安全：实现类应保证 {@link #lookup(String)} 和 {@link #register(VariableDefinition)} 的线程安全。
 *
 * @since 1.0.0
 */
public interface VariableRegistry {

    /**
     * 查询变量定义
     *
     * @param name 变量名
     * @return 变量定义；不存在返回 null
     */
    VariableDefinition lookup(String name);

    /**
     * 判断变量是否已注册
     *
     * @param name 变量名
     * @return true=已注册
     */
    default boolean contains(String name) {
        return lookup(name) != null;
    }

    /**
     * 列出所有已注册变量
     *
     * @return 变量定义列表
     */
    List<VariableDefinition> listAll();

    /**
     * 按类别查询变量
     *
     * @param category 变量来源类别（如 EVM / PROJECT / FINANCE）
     * @return 该类别下的变量列表
     */
    default List<VariableDefinition> listByCategory(String category) {
        return listAll().stream()
                .filter(v -> category.equals(v.getCategory()))
                .toList();
    }

    /**
     * 注册变量定义
     *
     * <p>用于编程式动态注册。数据库实现可忽略此方法（由 DB 持久化驱动）。
     *
     * @param definition 变量定义
     */
    default void register(VariableDefinition definition) {
        throw new UnsupportedOperationException("当前 VariableRegistry 实现不支持动态注册");
    }

    /**
     * 批量注册变量定义
     *
     * @param definitions 变量定义集合
     */
    default void registerAll(Collection<VariableDefinition> definitions) {
        if (definitions == null) return;
        definitions.forEach(this::register);
    }

    /**
     * 注销变量定义
     *
     * <p>用于编程式动态注销。数据库实现从 DB 删除并刷新缓存；
     * 内存实现从缓存移除。
     *
     * @param name 变量名
     */
    default void unregister(String name) {
        throw new UnsupportedOperationException("当前 VariableRegistry 实现不支持动态注销");
    }

    /**
     * 刷新缓存
     *
     * <p>强制重新加载变量定义。内存实现为空操作；数据库实现清空缓存并重新加载。
     */
    default void refresh() {
        // 默认空操作：内存注册表无需刷新
    }

    /**
     * 是否为空注册表（无任何变量定义）
     *
     * @return true=空注册表
     */
    default boolean isEmpty() {
        return listAll().isEmpty();
    }
}
