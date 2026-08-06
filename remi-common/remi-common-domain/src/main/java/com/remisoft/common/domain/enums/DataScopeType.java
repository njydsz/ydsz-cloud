package com.remisoft.common.domain.enums;

import java.util.Map;
import java.util.function.Supplier;

import com.remisoft.common.core.constant.HeaderConstants;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据权限范围类型枚举。
 *
 * <p>定义系统中数据权限的维度类型，用于行级数据权限控制。
 * 支持租户、集团、公司、部门、用户、项目、区域等多种维度。
 *
 * <p><b>维度优先级（从高到低）：</b>
 * <ul>
 *   <li>CUSTOM(50) - 自定义 SQL</li>
 *   <li>GROUP(40) - 集团级别</li>
 *   <li>COMPANY(30) - 公司级别</li>
 *   <li>PROJECT(25) - 项目级别</li>
 *   <li>DEPT(20) - 部门级别</li>
 *   <li>REGION(15) - 区域级别</li>
 *   <li>TENANT(10) - 租户级别</li>
 *   <li>USER(5) - 用户级别</li>
 * </ul>
 *
 * <p><b>列名约定：</b>每个维度绑定一个数据库列名（如 {@code tenant_id}、{@code dept_id}），
 * 业务方可通过 {@link #getColumnName()} 获取列名，拼接 WHERE 条件。
 *
 * <p><b>接入方式：</b>
 * <ul>
 *   <li>SQL 拦截器：通过 {@link DataScopeEvaluator} 注入当前用户的数据权限范围</li>
 *   <li>AOP 切面：配合 {@code @RbacDataScope} 注解实现声明式数据权限</li>
 *   <li>Header 传递：客户端通过 HeaderConstants 中的 {@code X-Data-Scope} 传递</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * @since 1.5.0 增加 columnName、DataScopeEvaluator 接入点
 * @see HeaderConstants
 * @see DataScopeEvaluator
 */
@Getter
@AllArgsConstructor
public enum DataScopeType implements TypeEnum<String> {

    /**
     * 租户维度。
     *
     * <p>按租户隔离数据，用于多租户系统。
     * 列名：{@code tenant_id}
     */
    TENANT("tenant", "租户", 10, "tenant_id"),

    /**
     * 集团维度。
     *
     * <p>按集团维度过滤，用户可访问集团下所有公司数据。
     * 列名：{@code group_id}
     */
    GROUP("group", "集团", 40, "group_id"),

    /**
     * 公司维度。
     *
     * <p>按公司维度过滤，用户可访问公司及下属部门数据。
     * 列名：{@code company_id}
     */
    COMPANY("company", "公司", 30, "company_id"),

    /**
     * 项目维度。
     *
     * <p>按项目维度过滤，用户可访问有权限的项目数据。
     * 列名：{@code project_id}
     */
    PROJECT("project", "项目", 25, "project_id"),

    /**
     * 部门维度。
     *
     * <p>按部门维度过滤，用户只能访问本部门及下级部门数据。
     * 列名：{@code dept_id}
     */
    DEPT("dept", "部门", 20, "dept_id"),

    /**
     * 区域维度。
     *
     * <p>按区域维度过滤，用户可访问有权限的区域数据。
     * 列名：{@code region_id}
     */
    REGION("region", "区域", 15, "region_id"),

    /**
     * 用户维度。
     *
     * <p>按用户维度过滤，用户只能访问自己的数据。
     * 列名：{@code user_id}
     */
    USER("user", "用户", 5, "user_id"),

    /**
     * 自定义维度。
     *
     * <p>使用自定义 SQL 条件进行数据过滤，由业务方实现 {@link DataScopeEvaluator} 提供。
     * 列名：无固定列，由 evaluator 动态决定
     */
    CUSTOM("custom", "自定义", 50, null);

    /**
     * 维度类型编码。
     */
    private final String code;

    /**
     * 维度类型描述。
     */
    private final String desc;

    /**
     * 维度优先级。
     *
     * <p>数值越大优先级越高，用于多维度叠加时确定主导维度。
     */
    private final int priority;

    /**
     * 关联的数据库列名。
     *
     * <p>用于拼接行级过滤条件（如 {@code WHERE tenant_id = ?}）。
     * {@link #CUSTOM} 为 null，由业务方 evaluator 动态决定。
     */
    private final String columnName;

    /** 按维度编码索引的不可变映射，用于通过编码快速查找枚举值 */
    private static final Map<String, DataScopeType> CODE_MAP = TypeEnum.buildCodeMap(DataScopeType.class);

    /**
     * 根据编码获取数据权限范围类型。
     *
     * @param code 编码值
     * @return 对应的枚举值
     * @throws IllegalArgumentException 当编码不存在或为 null 时抛出
     */
    public static DataScopeType codeOf(String code) {
        return TypeEnum.codeOf(CODE_MAP, code);
    }

    /**
     * 比较两个维度，获取优先级较高的维度。
     *
     * @param a 维度A，可为 null
     * @param b 维度B，可为 null
     * @return 优先级较高的维度，若有 null 则返回非 null 的那个，两者都 null 时返回 null
     */
    public static DataScopeType max(DataScopeType a, DataScopeType b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.getPriority() >= b.getPriority() ? a : b;
    }

    /**
     * 生成标准行级过滤条件片段。
     *
     * <p>适用于非 {@link #CUSTOM} 的标准维度。由外部（如 SQL 拦截器）传入当前用户的 ID 值。
     *
     * @param contextSupplier 提供当前维度下用户 ID 的 Supplier（如当前用户所属部门 ID）
     * @return WHERE 条件片段，如 "dept_id = 42"；若 columnName 为 null 则返回空字符串
     */
    public String toSqlFragment(Supplier<Object> contextSupplier) {
        if (columnName == null || contextSupplier == null) {
            return "";
        }
        Object value = contextSupplier.get();
        if (value == null) {
            return "";
        }
        return columnName + " = " + value;
    }

    /**
     * 判断是否为标准维度（非 CUSTOM）。
     *
     * @return columnName 非空时返回 true
     */
    public boolean isStandardScope() {
        return columnName != null;
    }

    /**
     * 数据权限评估器接口（策略模式）。
     *
     * <p>业务模块实现此接口，提供当前用户的数据权限上下文信息。
     * 典型实现：从 Spring Security Authentication / RequestContext 中提取集团ID、部门ID等。
     *
     * <p>实现示例（放在业务模块的 infrastructure 层）：
     * <pre>{@code
     * &#64;Component
     * public class RbacDataScopeEvaluator implements DataScopeEvaluator {
     *     &#64;Override
     *     public Map<DataScopeType, Object> evaluateCurrentScope() {
     *         Authentication auth = SecurityContextHolder.getContext().getAuthentication();
     *         // 从 auth 中提取用户可访问的 tenant/group/company/dept IDs
     *         return Map.of(
     *             DataScopeType.TENANT, currentTenantId(),
     *             DataScopeType.DEPT,  currentUserDeptId()
     *         );
     *     }
     * }
     * }</pre>
     *
     * @since 1.5.0
     */
    @FunctionalInterface
    public interface DataScopeEvaluator {

        /**
         * 评估当前用户的数据权限范围。
         *
         * @return 维度 → 当前用户 ID 映射（如 {TENANT: 1, DEPT: 42}）。
         *         未出现的维度表示该维度不参与当前查询过滤。
         */
        Map<DataScopeType, Object> evaluateCurrentScope();
    }
}
