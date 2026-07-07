package com.njydsz.pmis.common.constant;

/**
 * 系统级常量（P1-审计字段）。
 *
 * <p>用于统一标识「非用户操作 / 系统自动触发」场景下审计字段的取值，
 * 避免在 {@code AuditFieldFiller}、SQL DDL 等多处硬编码字面量。
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>{@code created_by} / {@code updated_by} 在无登录用户时填充 {@link #SYSTEM_USER_ID}</li>
 *   <li>SQL DDL 中 {@code DEFAULT 'SYSTEM'} 与该常量保持一致</li>
 *   <li>前端展示审计字段时，可根据该值渲染为「系统」而非「0」</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class SystemConstants {

    private SystemConstants() {
    }

    /**
     * 系统占位用户 ID。
     *
     * <p>当前线程未登录用户时，{@code created_by} / {@code updated_by} 默认填充该值；
     * 业务层可通过 {@code createdBy.equals(SystemConstants.SYSTEM_USER_ID)} 判断是否为系统操作。
     */
    public static final String SYSTEM_USER_ID = "SYSTEM";
}
