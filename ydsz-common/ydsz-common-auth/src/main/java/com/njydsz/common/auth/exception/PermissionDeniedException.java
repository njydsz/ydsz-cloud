package com.njydsz.common.auth.exception;

import java.util.Collections;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.njydsz.common.auth.i18n.PermissionMessageResolver;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

import lombok.Getter;

/**
 * 权限校验拒绝异常。
 *
 * <p>当用户权限不足以执行特定操作时抛出此异常。
 * 相比普通 BusinessException，本异常包含更丰富的上下文信息，便于开发和运维排查问题。
 *
 * <p><b>异常信息包含：</b>
 * <ul>
 *   <li>userId：当前用户 ID</li>
 *   <li>userRoles：当前用户角色列表</li>
 *   <li>requiredPermissions：缺少的权限列表</li>
 *   <li>permissionType：权限类型（MENU/BUTTON/API/DATA/COLUMN）</li>
 *   <li>resource：被访问的资源路径</li>
 *   <li>checkMode：校验模式（AND/OR）</li>
 *   <li>grantedPermissions：当前用户已有的权限（调试用）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * throw PermissionDeniedException.denied()
 *     .userId(userId)
 *     .userRoles(userRoles)
 *     .requiredPermissions(requiredPerms)
 *     .permissionType(PermissionType.API)
 *     .resource("/api/user/list")
 *     .checkMode("AND")
 *     .build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see BusinessException
 */
@Getter
public class PermissionDeniedException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** 触发拒绝的当前用户 ID；{@code null} 表示未登录或匿名上下文。 */
    private final String userId;
    /** 当前用户拥有的角色集合；构造时包装为不可变集合，永不为 {@code null}。 */
    private final transient Set<String> userRoles;
    /** 本次校验缺失/要求的权限集合；构造时传入 {@code null} 会被规范为不可变空集合。 */
    private final transient Set<String> requiredPermissions;
    /** 被拒绝的权限类型（MENU/BUTTON/API/DATA/COLUMN），决定 HTTP 错误码与 i18n 文案键。 */
    private final PermissionType permissionType;
    /** 被拒绝访问的资源标识，如接口路径或菜单编码。 */
    private final String resource;
    /** 校验模式（AND/OR），表示所需权限之间的逻辑关系。 */
    private final String checkMode;
    /** 用户已拥有的权限集合（调试用）；构造时包装为不可变集合，永不为 {@code null}。 */
    private final transient Set<String> grantedPermissions;

    /**
     * 权限类型，决定权限校验维度与异常错误码映射。
     *
     * <p>每种类型携带中文描述与 i18n 消息键，错误码映射见 {@link PermissionDeniedException#buildCode}：
     * 菜单/按钮/接口/数据/列权限分别对应 {@code A03011}~{@code A03015}。
     */
    public enum PermissionType {
        MENU("菜单权限", "menu.permission"),
        BUTTON("按钮权限", "button.permission"),
        API("接口权限", "api.permission"),
        DATA("数据权限", "data.permission"),
        COLUMN("列权限", "column.permission");

        @Getter
        private final String description;
        @Getter
        private final String messageKey;

        PermissionType(String description, String messageKey) {
            this.description = description;
            this.messageKey = messageKey;
        }
    }

    private PermissionDeniedException(Builder builder) {
        super();
        initFields(buildCode(builder.permissionType), null, new Object[]{});
        setMessage(buildMessage(builder));
        this.userId = builder.userId;
        this.userRoles = builder.userRoles != null
                ? Collections.unmodifiableSet(builder.userRoles)
                : Collections.emptySet();
        this.requiredPermissions = builder.requiredPermissions != null
                ? Collections.unmodifiableSet(builder.requiredPermissions)
                : Collections.emptySet();
        this.permissionType = builder.permissionType;
        this.resource = builder.resource;
        this.checkMode = builder.checkMode;
        this.grantedPermissions = builder.grantedPermissions != null
                ? Collections.unmodifiableSet(builder.grantedPermissions)
                : Collections.emptySet();

        setHttpStatus(HttpStatus.FORBIDDEN.value());
        setLevel(ExceptionLevel.WARN);
        setCategory(ExceptionCategory.SECURITY);
    }

    private static String buildCode(PermissionType type) {
        if (type == null) {
            return String.valueOf(HttpStatus.FORBIDDEN.value());
        }
        switch (type) {
            case MENU:
                return "A03011";
            case BUTTON:
                return "A03012";
            case API:
                return "A03013";
            case DATA:
                return "A03014";
            case COLUMN:
                return "A03015";
            default:
                return String.valueOf(HttpStatus.FORBIDDEN.value());
        }
    }

    private static String buildMessage(Builder builder) {
        // 使用 i18n 解析基础消息
        String lang = builder.language != null ? builder.language : "zh-CN";
        String baseKey = builder.permissionType != null
                ? "permission.denied." + builder.permissionType.name().toLowerCase()
                : "permission.denied";
        String baseMsg = PermissionMessageResolver.resolve(baseKey, lang);

        StringBuilder sb = new StringBuilder();
        sb.append(baseMsg);

        if (builder.permissionType != null) {
            sb.append(" [").append(builder.permissionType.getDescription()).append("]");
        }

        if (builder.userId != null) {
            sb.append(" | 用户ID：").append(builder.userId);
        }

        if (builder.userRoles != null && !builder.userRoles.isEmpty()) {
            sb.append(" | 当前角色：").append(String.join(", ", builder.userRoles));
        }

        if (builder.requiredPermissions != null && !builder.requiredPermissions.isEmpty()) {
            sb.append(" | 需要权限：").append(String.join(", ", builder.requiredPermissions));
        }

        if (builder.grantedPermissions != null && !builder.grantedPermissions.isEmpty()) {
            sb.append(" | 已有权限：").append(String.join(", ", builder.grantedPermissions));
        }

        if (builder.resource != null) {
            sb.append(" | 资源：").append(builder.resource);
        }

        if (builder.checkMode != null) {
            sb.append(" | 校验模式：").append(builder.checkMode);
        }

        return sb.toString();
    }

    /**
     * 创建权限拒绝异常的构建器。
     *
     * <p>通过链式调用 {@code userId}/{@code requiredPermissions} 等方法填充上下文，最后调用 {@link Builder#build()} 抛出异常。
     * 构造出的异常固定为 HTTP 403，错误码按 {@link PermissionType} 映射（如 API 为 {@code A03013}）。</p>
     *
     * @return 异常构建器实例
     */
    public static Builder denied() {
        return new Builder();
    }

    /**
     * 权限拒绝异常构建器。
     *
     * <p>通过 {@link #denied()} 创建，链式填充拒绝上下文字段后调用 {@link #build()} 生成异常。
     * 构建器非线程安全，单次使用完毕即弃，不应跨线程复用。
     */
    public static class Builder {
        private String userId;
        private Set<String> userRoles;
        private Set<String> requiredPermissions;
        private PermissionType permissionType;
        private String resource;
        private String checkMode;
        private Set<String> grantedPermissions;
        /** i18n 语言标识，默认 {@code "zh-CN"}。 */
        private String language = "zh-CN";

        /**
         * 设置 i18n 语言标识。
         *
         * @param language 语言标识，如 {@code "zh-CN"} / {@code "en-US"}；默认 {@code "zh-CN"}
         * @return 当前构建器，支持链式调用
         */
        public Builder language(String language) {
            this.language = language;
            return this;
        }

        /**
         * 设置触发拒绝的用户标识。
         *
         * @param userId 用户唯一标识（如工号），用于错误上下文与审计；可为 null 表示匿名
         * @return 当前构建器，支持链式调用
         */
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * 设置当前用户所拥有的角色集合。
         *
         * @param userRoles 角色编码集合（如 {@code ["ROLE_ADMIN"]}），用于展示“实际拥有”的权限上下文
         * @return 当前构建器，支持链式调用
         */
        public Builder userRoles(Set<String> userRoles) {
            this.userRoles = userRoles;
            return this;
        }

        /**
         * 设置本次校验要求的权限集合。
         *
         * @param requiredPermissions 必须具备的权限编码集合；任一缺失即触发拒绝
         * @return 当前构建器，支持链式调用
         */
        public Builder requiredPermissions(Set<String> requiredPermissions) {
            this.requiredPermissions = requiredPermissions;
            return this;
        }

        /**
         * 设置权限类型（决定 HTTP 错误码映射）。
         *
         * @param permissionType 权限类型，如 API / 菜单 / 数据；决定异常错误码（如 API 映射 {@code A03013}）
         * @return 当前构建器，支持链式调用
         */
        public Builder permissionType(PermissionType permissionType) {
            this.permissionType = permissionType;
            return this;
        }

        /**
         * 设置被访问的资源标识。
         *
         * @param resource 资源标识（如接口路径、菜单编码、数据主键），用于错误上下文定位
         * @return 当前构建器，支持链式调用
         */
        public Builder resource(String resource) {
            this.resource = resource;
            return this;
        }

        /**
         * 设置权限校验模式。
         *
         * @param checkMode 校验模式编码（如 {@code "rbac"} / {@code "dataScope"}），用于区分校验链路
         * @return 当前构建器，支持链式调用
         */
        public Builder checkMode(String checkMode) {
            this.checkMode = checkMode;
            return this;
        }

        /**
         * 设置用户实际被授予的权限集合（与要求权限做差集展示）。
         *
         * @param grantedPermissions 用户已授予的权限编码集合；可为 null 表示未计算
         * @return 当前构建器，支持链式调用
         */
        public Builder grantedPermissions(Set<String> grantedPermissions) {
            this.grantedPermissions = grantedPermissions;
            return this;
        }

        /**
         * 构建权限拒绝异常实例。
         *
         * @return 组装完成的 {@link PermissionDeniedException}
         */
        public PermissionDeniedException build() {
            return new PermissionDeniedException(this);
        }
    }

    @Override
    public String toString() {
        return "PermissionDeniedException{" +
                "userId='" + userId + '\'' +
                ", userRoles=" + userRoles +
                ", requiredPermissions=" + requiredPermissions +
                ", permissionType=" + permissionType +
                ", resource='" + resource + '\'' +
                ", checkMode='" + checkMode + '\'' +
                ", grantedPermissions=" + grantedPermissions +
                ", code='" + getCode() + '\'' +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}
