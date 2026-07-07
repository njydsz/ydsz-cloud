package com.njydsz.pmis.common.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 分页查询参数
 *
 * <p>约定：page 从 1 开始；size 默认 10，最大 200。
 * P2-3 新增 Bean Validation 注解，配合 Controller 层 @Valid 防止恶意大分页。
 * P0-C3 新增 orderBy 白名单校验：{@link #safeOrderBy(Set, String)} + {@link #safeOrderDir()}，
 * 杜绝 SQL 注入与服务端排序字段被恶意篡改。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class PageQuery implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 每页最大限制（P2-3 安全防护） */
    public static final long MAX_SIZE = 200;

    /**
     * 排序字段允许的字符模式（仅字母/数字/下划线，必须以字母开头）。
     *
     * <p>用于 {@link #orderBy} 字段的 Bean Validation，从源头阻断 SQL 注入：
     * 即使 service 层未调用 {@link #safeOrderBy(Set, String)}，
     * Controller 的 {@code @Valid} 也会拒绝含特殊字符的排序字段。
     */
    public static final String ORDER_BY_PATTERN = "^[a-zA-Z][a-zA-Z0-9_]*$";

    /** 当前页（从 1 开始） */
    @Min(value = 1, message = "{validation.common.msg_6d2ed876}")
    private long page = 1;

    /** 每页大小 */
    @Min(value = 1, message = "{validation.common.msg_1888441f}")
    @Max(value = MAX_SIZE, message = "{validation.common.msg_7f3e4739}")
    private long size = 10;

    /** 关键字（模糊搜索） */
    private String keyword;

    /**
     * 排序字段（snake_case，必须匹配 {@link #ORDER_BY_PATTERN}）。
     *
     * <p>注意：通过 Bean Validation 仅校验格式，service 层必须调用
     * {@link #safeOrderBy(Set, String)} 进行白名单二次校验。
     */
    @Pattern(regexp = ORDER_BY_PATTERN, message = "{validation.common.msg_c2a8f9e1}")
    private String orderBy;

    /** 排序方向 asc/desc */
    private String orderDir = "desc";

    /**
     * 计算 SQL 偏移量（page 从 1 开始，对 page/size 做最小值保护，size 做最大值保护）
     *
     * @return 偏移量
     */
    public long offset() {
        return (Math.max(page, 1) - 1) * Math.min(Math.max(size, 1), MAX_SIZE);
    }

    /**
     * 返回安全的排序字段名（白名单校验）。
     *
     * <p>大厂安全规范：service 层在拼接排序前必须调用此方法，确保排序字段在预定义白名单内。
     * 即使前端绕过 {@code @Valid}（如内部 RPC 调用未校验），白名单仍可兜底拦截。
     *
     * @param allowedFields 允许的排序字段集合（snake_case），可为 null（仅做格式校验）
     * @param defaultField  默认排序字段（当 orderBy 为空或不在白名单时使用），可为 null
     * @return 安全的排序字段名；orderBy 为空时返回 defaultField；不在白名单时返回 defaultField
     */
    public String safeOrderBy(Set<String> allowedFields, String defaultField) {
        if (orderBy == null || orderBy.isBlank()) {
            return defaultField;
        }
        // 格式校验兜底（防止非 @Valid 入口）
        if (!orderBy.matches(ORDER_BY_PATTERN)) {
            return defaultField;
        }
        // 白名单校验
        if (allowedFields == null || allowedFields.isEmpty()) {
            // 未配置白名单时，仅允许默认字段（保守策略）
            return defaultField;
        }
        return allowedFields.contains(orderBy) ? orderBy : defaultField;
    }

    /**
     * 返回安全的排序方向。
     *
     * <p>仅允许 {@code asc}/{@code desc}，非法值默认 {@code desc}，
     * 防止 orderDir 被注入 SQL 关键字。
     *
     * @return "asc" 或 "desc"
     */
    public String safeOrderDir() {
        return "asc".equalsIgnoreCase(orderDir) ? "asc" : "desc";
    }
}
