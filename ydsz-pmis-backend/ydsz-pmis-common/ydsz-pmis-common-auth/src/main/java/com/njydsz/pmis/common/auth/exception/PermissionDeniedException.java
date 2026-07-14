package com.njydsz.pmis.common.auth.exception;

import java.util.Collections;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

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
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @see BusinessException
 */
@Getter
public class PermissionDeniedException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private final String userId;
    private final transient Set<String> userRoles;
    private final transient Set<String> requiredPermissions;
    private final PermissionType permissionType;
    private final String resource;
    private final String checkMode;
    private final transient Set<String> grantedPermissions;

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
        StringBuilder sb = new StringBuilder();
        sb.append("权限不足：");

        if (builder.permissionType != null) {
            sb.append(builder.permissionType.getDescription()).append("校验失败");
        } else {
            sb.append("权限校验失败");
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

    public static Builder denied() {
        return new Builder();
    }

    public static class Builder {
        private String userId;
        private Set<String> userRoles;
        private Set<String> requiredPermissions;
        private PermissionType permissionType;
        private String resource;
        private String checkMode;
        private Set<String> grantedPermissions;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder userRoles(Set<String> userRoles) {
            this.userRoles = userRoles;
            return this;
        }

        public Builder requiredPermissions(Set<String> requiredPermissions) {
            this.requiredPermissions = requiredPermissions;
            return this;
        }

        public Builder permissionType(PermissionType permissionType) {
            this.permissionType = permissionType;
            return this;
        }

        public Builder resource(String resource) {
            this.resource = resource;
            return this;
        }

        public Builder checkMode(String checkMode) {
            this.checkMode = checkMode;
            return this;
        }

        public Builder grantedPermissions(Set<String> grantedPermissions) {
            this.grantedPermissions = grantedPermissions;
            return this;
        }

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